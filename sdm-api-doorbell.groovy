import groovy.transform.Field

/**
 *
 *  Copyright 2020-2022 David Kilgore. All Rights Reserved
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *  version: 1.1.0.alpha1
 */

metadata {
    definition(name: 'Google Nest Doorbell', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-doorbell.groovy') {
        //capability 'VideoCamera'
        capability 'PushableButton'
        capability 'ImageCapture'
        capability 'Refresh'
        capability 'MotionSensor'
        capability 'PresenceSensor'
        capability 'SoundSensor'
        capability 'Initialize'        

        attribute 'rawImg', 'string'
        attribute 'streamUrl', 'string'
    }
    
    preferences {
        input 'minimumMotionTime', 'number', title: 'Motion timeout (s)', required: true, defaultValue: 15
        input 'minimumPresenceTime', 'number', title: 'Presence timeout (s)', required: true, defaultValue: 15
        input 'minimumSoundTime', 'number', title: 'Sound timeout (s)', required: true, defaultValue: 15
    
        input 'chimeImageCapture', 'bool', title: 'Chime - Capture image?', required: true, defaultValue: true
        input 'personImageCapture', 'bool', title: 'Person - Capture image?', required: true, defaultValue: true
        input 'motionImageCapture', 'bool', title: 'Motion - Capture image?', required: true, defaultValue: true
        input 'soundImageCapture', 'bool', title: 'Sound - Capture image?', required: true, defaultValue: true

        input 'enableVideoStream', 'bool', title: 'Enable Video Stream?', required: true, defaultValue: false

        input name: "debugOutput", type: "bool", title: "Enable Debug Logging?", defaultValue: false
    }
}

@Field static Map<String,Map<String,List<String>>> activeThreads = [:]

void appendActiveThread(String eventType, String threadId) {
    if (threadId) {
        logDebug("${eventType} event is active in thread: ${threadId}")
        if (activeThreads["${device.id}" as String] == null) {
            activeThreads["${device.id}" as String] = [:]
        }
        List<String> threadEvents = activeThreads["${device.id}" as String][threadId] ?: []
        if (!threadEvents.contains(eventType)) {
            threadEvents.add(eventType)
        }
        activeThreads["${device.id}" as String][threadId] = threadEvents
        logDebug('after append - current active threads:' + activeThreads["${device.id}" as String].keySet())
    }
}

void removeActiveThread(String eventType, String threadId) {
    if (threadId) {
        List<String> events = activeThreads["${device.id}" as String].remove(threadId)
        logDebug("${threadId} ended -- marking events inactive: ${events}")
        for (evt in events) {
            switch (evt) {
            // skip eventType as already processed
            case eventType:
                break
            case 'person':
                presenceInactive()
                break
            case 'motion':
                motionInactive()
                break
            case 'sound':
                soundInactive()
                break
            }
        }
        logDebug('after remove - current active threads:' + activeThreads["${device.id}" as String].keySet())
    }
}

private logDebug(msg) {
    if (settings?.debugOutput) {
        log.debug "${device.label}: $msg"
    }
}

def installed() {
    initialize()
}

def updated() {
    if (!chimeImageCapture && !personImageCapture && !motionImageCapture && !soundImageCapture) {
        device.sendEvent(name: 'image', value: '<img src="" />')
        device.sendEvent(name: 'rawImg', value: ' ')
    }
    initialize()
}

def uninstalled() {
    unschedule()
}

def initialize() {
    if (enableVideoStream && (state.videoFormat == 'RTSP')) {
        Random rnd = new Random()
        parent.deviceGenerateStream(device)
        schedule("${rnd.nextInt(60)} ${rnd.nextInt(4)}/4 * ? * * *", extendStream)
    } else {
        unschedule(extendStream)
        if (state.streamUrl) {
            parent.deviceStopStream(device, state.streamExtensionToken)
        }
        state.remove('streamUrl')
        state.remove('streamToken')
        state.remove('streamExtensionToken')
        device.sendEvent(name: 'streamUrl', value: ' ')
    }
    device.sendEvent(name: 'numberOfButtons', value: 1)
    // reset person, motion, sound states, clear activeThreads
    def devs = getChildDevices()
    def zones = []
    devs.each{
        zones.append(it.getId().tokenize('_')[-1])
    }
    presenceInactive(zones)
    motionInactive(zones)
    soundInactive(zones)
    activeThreads["${device.id}" as String] = [:]

    device.sendEvent(name: 'rawImg', value: device.currentValue('rawImg') ?: ' ')
    device.sendEvent(name: 'image', value: device.currentValue('image') ?: '<img src="" />')
    device.sendEvent(name: 'streamUrl', value: device.currentValue('streamUrl') ?: ' ')
}

def refresh() {
    initialize()
    parent.getDeviceData(device)
}

def push(button) {
    logDebug("Digital button push (${button})")
    device.sendEvent(name: 'pushed', value: button, isStateChange: true)
}

def processChime() {
    logDebug('Doorbell/chime pressed')
    device.sendEvent(name: 'pushed', value: 1, isStateChange: true)
}

def processPerson(String threadState='', String threadId='', zones=[]) {
    switch (threadState) {
    case 'ENDED':
        presenceInactive(zones)
        removeActiveThread('person', threadId)
        break
    case 'STARTED':
    case 'UPDATED':
        presenceActive(zones)
        appendActiveThread('person', threadId)
        break
    case '':
    default:
        presenceActive(zones)
        if (minimumPresenceTime == null) {
            device.updateSetting('minimumPresenceTime', 15)
        }
        runIn(minimumPresenceTime, presenceInactive, [overwrite: true, data: zones])
        break
    }
}

def presenceActive(zones=[]) {
    logDebug('Person -- present')
    device.sendEvent(name: 'presence', value: 'present')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.presenceActive()
        }
    }
}

def presenceInactive(zones=[]) {
    logDebug('Person -- not present')
    device.sendEvent(name: 'presence', value: 'not present')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.presenceInactive()
        }
    }
}

def processMotion(String threadState='', String threadId='', zones=[]) {
    switch (threadState) {
    case 'ENDED':
        motionInactive(zones)
        removeActiveThread('motion', threadId)
        break
    case 'STARTED':
    case 'UPDATED':
        motionActive(zones)
        appendActiveThread('motion', threadId)
        break
    case '':
    default:
        motionActive(zones)
        if (minimumMotionTime == null) {
            device.updateSetting('minimumMotionTime', 15)
        }
        runIn(minimumMotionTime, motionInactive, [overwrite: true, data: zones])
        break
    }
}

def motionActive(zones=[]) {
    logDebug('Motion -- active')
    device.sendEvent(name: 'motion', value: 'active')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.motionActive()
        }
    }
}

def motionInactive(zones=[]) {
    logDebug('Motion -- inactive')
    device.sendEvent(name: 'motion', value: 'inactive')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.motionInactive()
        }
    }
}

def processSound(String threadState='', String threadId='', zones=[]) {
    switch (threadState) {
    case 'ENDED':
        soundInactive(zones)
        removeActiveThread('sound', threadId)
        break
    case 'STARTED':
    case 'UPDATED':
        soundActive(zones)
        appendActiveThread('sound', threadId)
        break
    case '':
    default:
        soundActive(zones)
        if (minimumSoundTime == null) {
            device.updateSetting('minimumSoundTime', 15)
        }
        runIn(minimumSoundTime, soundInactive, [overwrite: true, data: zones])
        break
    }
}

def soundActive(zones=[]) {
    logDebug('Sound -- detected')
    device.sendEvent(name: 'sound', value: 'detected')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.soundActive()
        }
    }
}

def soundInactive(zones=[]) {
    logDebug('Sound -- not detected')
    device.sendEvent(name: 'sound', value: 'not detected')
    zones.each{
        def dev = makeRealDevice(it)
        if (dev != null) {
            dev.soundInactive()
        }
    }
}

def shouldGetImage(String event) {
    switch (event) {
    case 'Chime':
        return chimeImageCapture != null ? chimeImageCapture : true
        break
    case 'Person':
        return personImageCapture != null ? personImageCapture : true
        break
    case 'Motion':
        return motionImageCapture != null ? motionImageCapture : true
        break
    case 'Sound':
        return soundImageCapture != null ? soundImageCapture : true
        break
    case 'ClipPreview':
        return false
        break
    default:
        return true
        break
    }
}

def extendStream() {
    parent.deviceExtendStream(device, state.streamExtensionToken)
}

def updateStreamData(Map data) {
    String url = state.streamUrl
    if (data.results.streamUrls) {
        int queryIndex = data.results.streamUrls.rtspUrl.indexOf('?')
        url = data.results.streamUrls.rtspUrl.substring(0, queryIndex)
        state.streamUrl = url
    }
    device.sendEvent(name: 'streamUrl', value: "${url}?auth=${data.results.streamToken}")
    state.streamToken = data.results.streamToken
    state.streamExtensionToken = data.results.streamExtensionToken
}

def take() {
    log.warn('on-demand image capture is not supported')
}

def getFolderId() {
    if (state.folderId) {
        return state.folderId
    } else {
        device.sendEvent(name: 'rawImg', value: ' ')
        parent.createFolder(device)
        return false
    }
}

def setDeviceState(String attr, value) {
    logDebug("updating state -- ${attr}: ${value}")
    state[attr] = value
}

def getDeviceState(String attr) {
    if (state[attr]) {
        return state[attr]
    } else {
        refresh()
    }
}

def makeRealDevice(zone) {
    def deviceId = "${getId()}_${zone}"
    def deviceName = "${getLabel()}_${zone}"
    try {
        addChildDevice(
            'dkilgore90',
            "Google Nest Zone Child",
            deviceId.toString(),
            [
                name: deviceName.toString(),
                label: deviceName.toString()
            ]
        )
    } catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
        log.warn("${e.message} - you need to install the appropriate driver: ${device.type}")
    } catch (IllegalArgumentException ignored) {
        //Intentionally ignored.  Expected if device id already exists in HE.
        getChildDevice(deviceId.toString())
    }
}