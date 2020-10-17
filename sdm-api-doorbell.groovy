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
 *  version: 0.2.0
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
        

        attribute 'room', 'string'
        attribute 'imgWidth', 'number'
        attribute 'imgHeight', 'number'
        attribute 'rawImg', 'string'
        attribute 'lastEventTime', 'string'
        attribute 'lastEventType', 'string'
    }
    
    preferences {
        input 'minimumMotionTime', 'number', title: 'Motion timeout', description: 'minimum time (in seconds) that the motion attribute will show `active` after receiving an event', required: true, defaultValue: 15
        input 'minimumPresenceTime', 'number', title: 'Presence timeout', description: 'minimum time (in seconds) that the presence attribute will show `present` after receiving an event', required: true, defaultValue: 15
        input 'minimumSoundTime', 'number', title: 'Sound timeout', description: 'minimum time (in seconds) that the sound attribute will show `detected` after receiving an event', required: true, defaultValue: 15
    
        input 'chimeImageCapture', 'bool', title: 'Chime - Capture image?', description: 'whether to download the still image for "Chime" events', required: true, defaultValue: true
        input 'personImageCapture', 'bool', title: 'Person - Capture image?', description: 'whether to download the still image for "Person" events', required: true, defaultValue: true
        input 'motionImageCapture', 'bool', title: 'Motion - Capture image?', description: 'whether to download the still image for "Motion" events', required: true, defaultValue: true
        input 'soundImageCapture', 'bool', title: 'Sound - Capture image?', description: 'whether to download the still image for "Sound" events', required: true, defaultValue: true
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def uninstalled() {

}

def initialize() {
    device.sendEvent(name: 'numberOfButtons', value: 1)
}

def refresh() {
    initialize()
    parent.getDeviceData(device)
}

def processPerson() {
    device.sendEvent(name: 'presence', value: 'present')
    if (minimumPresenceTime == null) {
        device.updateSetting('minimumPresenceTime', 15)
    }
    runIn(minimumPresenceTime, presenceInactive, [overwrite: true])
}

def presenceInactive() {
    device.sendEvent(name: 'presence', value: 'not present')
}

def processMotion() {
    device.sendEvent(name: 'motion', value: 'active')
    if (minimumMotionTime == null) {
        device.updateSetting('minimumMotionTime', 15)
    }
    runIn(minimumMotionTime, motionInactive, [overwrite: true])
}

def motionInactive() {
    device.sendEvent(name: 'motion', value: 'inactive')
}

def processSound() {
    device.sendEvent(name: 'sound', value: 'detected')
    if (minimumSoundTime == null) {
        device.updateSetting('minimumSoundTime', 15)
    }
    runIn(minimumSoundTime, soundInactive, [overwrite: true])
}

def soundInactive() {
    device.sendEvent(name: 'sound', value: 'not detected')
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
    default:
        return true
        break
    }
}

def take() {
    log.warn('on-demand image capture is not supported')
}
