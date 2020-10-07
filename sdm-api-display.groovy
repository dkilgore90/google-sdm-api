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
 *  version: 0.1.0
 */

metadata {
    definition(name: 'Google Nest Display', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-display.groovy') {
        capability 'VideoCamera'
        capability 'ImageCapture'
        capability 'Refresh'
        capability 'MotionSensor'

        attribute 'room', 'string'
        attribute 'imgWidth', 'number'
        attribute 'imgHeight', 'number'
        attribute 'rawImg', 'string'
        attribute 'lastEventTime', 'string'
        attribute 'activeChime', 'bool'
        attribute 'activePerson', 'bool'
        attribute 'activeMotion', 'bool'
        attribute 'activeSound', 'bool'
    }
    
    preferences {
        input name: "debugOutput", type: "bool", title: "Enable Debug Logging?", defaultValue: false
        input 'minimumMotionTime', 'number', title: 'Minimum Motion time (s)', 'description': 'minimum time (in seconds) that the motion attribute will show `active` after receiving an event', required: true, defaultValue: 15

        input name: "activeChimeInput", type: "bool", title: "Trigger motion on chime?", defaultValue: false
        input name: "activePersonInput", type: "bool", title: "Trigger motion on person?", defaultValue: false
        input name: "activeMotionInput", type: "bool", title: "Trigger motion on motion?", defaultValue: false
        input name: "activeSoundInput", type: "bool", title: "Trigger motion on sound?", defaultValue: false
	}    
}

private logDebug(msg) {
    if (settings?.debugOutput) {
        log.debug "${device.label}: $msg"
    }
}

def installed() {
    updated()
}

def updated() {
    // Four buttons:
    //   1 - sdm.devices.traits.DoorbellChime
    //   2 - sdm.devices.traits.CameraPerson
    //   3 - sdm.devices.traits.CameraMotion
    //   4 - sdm.devices.traits.CameraSound
    sendEvent(name:"numberOfButtons", value:4)
    sendEvent(name:"activeChime", value:activeChimeInput)
    sendEvent(name:"activePerson", value:activePersonInput)
    sendEvent(name:"activeMotion", value:activeMotionInput)
    sendEvent(name:"activeSound", value:activeSoundInput)
    motionInactive()
}

def uninstalled() {
}

def refresh() {
    updated()
    parent.getDeviceData(device)
}

def processMotion() {
    logDebug "${device.label}: Motion Active"
    device.sendEvent(name: 'motion', value: 'active')
    if (minimumMotionTime == null) {
        device.updateSetting('minimumMotionTime', 15)
    }
    runIn(minimumMotionTime, motionInactive, [overwrite: true])
}

def motionInactive() {
    logDebug "${device.label}: Motion Inactive"
    device.sendEvent(name: 'motion', value: 'inactive')
}

def take() {
    log.warn('on-demand image capture is not supported')
}