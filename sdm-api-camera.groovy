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
 */

metadata {
    definition(name: 'Google Nest Camera', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-camera.groovy') {
        capability 'VideoCamera'
        capability 'ImageCapture'
        capability 'Refresh'
        capability 'MotionSensor'

        attribute 'room', 'string'
        attribute 'imgWidth', 'number'
        attribute 'imgHeight', 'number'
        attribute 'rawImg', 'string'
        attribute 'lastEventTime', 'string'
    }
    
    preferences {
        input 'minimumMotionTime', 'number', title: 'Minimum Motion time (s)', 'description': 'minimum time (in seconds) that the motion attribute will show `active` after receiving an event', required: false, defaultValue: 15
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

def processMotion() {
    device.sendEvent(name: 'motion', value: 'active')
    runIn(minimumMotionTime, motionInactive, [overwrite: true])
}

def motionInactive() {
    device.sendEvent(name: 'motion', value: 'inactive')
}

def take() {
    log.warn('on-demand image capture is not supported')
}
