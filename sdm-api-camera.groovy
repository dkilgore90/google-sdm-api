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
 *  version: 0.1.1
 */

metadata {
    definition(name: 'Google Nest Camera-GG', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-camera.groovy') {
        //capability 'VideoCamera'
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
        input 'minimumMotionTime', 'number', title: ' Motion timeout', 'description': 'minimum time (in seconds) that the motion attribute will show `active` after receiving an event', required: true, defaultValue: 15
         input 'minimumPresenseTime', 'number', title: 'Presense timeout', 'description': 'minimum time (in seconds) that the presence attribute will show `present` after receiving an event', required: true, defaultValue: 15
         input 'minimumSoundTime', 'number', title: 'Sound timeout', 'description': 'minimum time (in seconds) that the sound attribute will show `detected` after receiving an event', required: true, defaultValue: 15
    }
}

def installed() {
}

def updated() {
}

def uninstalled() {
}

def refresh() {
    parent.getDeviceData(device)
}

def processPerson() {
    device.sendEvent(name: 'presence', value: 'present')
    if (minimumPresenseTime == null) {
        device.updateSetting('minimumPresenseTime', 15)
    }
    runIn(minimumPresenseTime, presenceInactive, [overwrite: true])
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

def take() {
    log.warn('on-demand image capture is not supported')
}
