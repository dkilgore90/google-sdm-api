/**
 *
 *  Copyright 2020 David Kilgore. All Rights Reserved
 *
 *  This software is free for Private Use. You may use and modify the software without distributing it.
 *  If you make a fork, and add new code, then you should create a pull request to add value, there is no
 *  guarantee that your pull request will be merged.
 *
 *  You may not grant a sublicense to modify and distribute this software to third parties without permission
 *  from the copyright holder
 *  Software is provided without warranty and your use of it is at your own risk.
 *
<<<<<<< HEAD
 *  version: 0.2.2
=======
 *  version: 0.2.1
>>>>>>> develop
 */

metadata {
    definition(name: 'Google Nest Camera', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-camera.groovy') {
        //capability 'VideoCamera'
        capability 'ImageCapture'
        capability 'Refresh'
        capability 'MotionSensor'
        capability 'PresenceSensor'
        capability 'SoundSensor'
        capability 'Initialize'

        attribute 'room', 'string'
        attribute 'imgWidth', 'number'
        attribute 'imgHeight', 'number'
        attribute 'rawImg', 'string'
        attribute 'lastEventTime', 'string'
        attribute 'lastEventType', 'string'
    }
    
    preferences {
        input 'minimumMotionTime', 'number', title: 'Motion timeout (s)', required: true, defaultValue: 15
        input 'minimumPresenceTime', 'number', title: 'Presence timeout (s)', required: true, defaultValue: 15
        input 'minimumSoundTime', 'number', title: 'Sound timeout (s)', required: true, defaultValue: 15
    
        input 'personImageCapture', 'bool', title: 'Person - Capture image?', required: true, defaultValue: true
        input 'motionImageCapture', 'bool', title: 'Motion - Capture image?', required: true, defaultValue: true
        input 'soundImageCapture', 'bool', title: 'Sound - Capture image?', required: true, defaultValue: true
    
        input name: "debugOutput", type: "bool", title: "Enable Debug Logging?", defaultValue: false
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
    if (!personImageCapture && !motionImageCapture && !soundImageCapture) {
        device.sendEvent(name: 'image', value: '<img src="" />')
        device.sendEvent(name: 'rawImg', value: ' ')
    }
    initialize()
}

def uninstalled() {
}

def initialize() {
    device.sendEvent(name: 'presence', value: device.currentValue('presence') ?: 'not present')
    device.sendEvent(name: 'motion', value: device.currentValue('motion') ?: 'inactive')
    device.sendEvent(name: 'sound', value: device.currentValue('sound') ?: 'not detected')
    device.sendEvent(name: 'image', value: device.currentValue('image') ?: '<img src="" />')
}

def refresh() {
    parent.getDeviceData(device)
}

def processPerson() {
    logDebug('Person -- present')
    device.sendEvent(name: 'presence', value: 'present')
    if (minimumPresenceTime == null) {
        device.updateSetting('minimumPresenceTime', 15)
    }
    runIn(minimumPresenceTime, presenceInactive, [overwrite: true])
}

def presenceInactive() {
    logDebug('Person -- not present')
    device.sendEvent(name: 'presence', value: 'not present')
}

def processMotion() {
    logDebug('Motion -- active')
    device.sendEvent(name: 'motion', value: 'active')
    if (minimumMotionTime == null) {
        device.updateSetting('minimumMotionTime', 15)
    }
    runIn(minimumMotionTime, motionInactive, [overwrite: true])
}

def motionInactive() {
    logDebug('Motion -- inactive')
    device.sendEvent(name: 'motion', value: 'inactive')
}

def processSound() {
    logDebug('Sound -- detected')
    device.sendEvent(name: 'sound', value: 'detected')
    if (minimumSoundTime == null) {
        device.updateSetting('minimumSoundTime', 15)
    }
    runIn(minimumSoundTime, soundInactive, [overwrite: true])
}

def soundInactive() {
    logDebug('Sound -- not detected')
    device.sendEvent(name: 'sound', value: 'not detected')
}

def shouldGetImage(String event) {
    switch (event) {
    case 'Person':
        return personImageCapture != null ? personImageCapture : true
        break
    case 'Motion':
        return motionImageCapture != null ? motionImageCapture : true
        break
    case 'Sound':
        return soundImageCapture != null ? soundImageCapture : true
        break
    default:
        return true
        break
    }
}
def take() {
    log.warn('on-demand image capture is not supported')
}
