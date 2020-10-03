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
    definition(name: 'Google Nest Doorbell', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-doorbell.groovy') {
        capability 'VideoCamera'
        capability 'PushableButton'
        capability 'ImageCapture'
        capability 'Refresh'

        attribute 'room', 'string'
        attribute 'imgWidth', 'number'
        attribute 'imgHeight', 'number'
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

def take() {
    log.warn('on-demand image capture is not supported')
}