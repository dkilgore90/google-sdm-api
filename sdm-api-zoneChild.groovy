/**
 *
 *  Copyright 2020-2021 David Kilgore. All Rights Reserved
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
 *  version: 0.0.1.zones_alpha1
 */

metadata {
    definition(name: 'Google Nest Zone Child', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-zoneChild.groovy') {
        capability 'MotionSensor'
        capability 'PresenceSensor'
        capability 'SoundSensor'
        capability 'Initialize'
    }
    
    preferences {
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
    initialize()
}

def uninstalled() {
    unschedule()
}

def initialize() {
    device.sendEvent(name: 'presence', value: device.currentValue('presence') ?: 'not present')
    device.sendEvent(name: 'motion', value: device.currentValue('motion') ?: 'inactive')
    device.sendEvent(name: 'sound', value: device.currentValue('sound') ?: 'not detected')
}

def presenceActive() {
    logDebug('Person -- present')
    device.sendEvent(name: 'presence', value: 'present')
}

def presenceInactive() {
    logDebug('Person -- not present')
    device.sendEvent(name: 'presence', value: 'not present')
}

def motionActive() {
    logDebug('Motion -- active')
    device.sendEvent(name: 'motion', value: 'active')
}

def motionInactive() {
    logDebug('Motion -- inactive')
    device.sendEvent(name: 'motion', value: 'inactive')
}

def soundActive() {
    logDebug('Sound -- detected')
    device.sendEvent(name: 'sound', value: 'detected')
}

def soundInactive() {
    logDebug('Sound -- not detected')
    device.sendEvent(name: 'sound', value: 'not detected')
}