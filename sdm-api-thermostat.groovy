import groovy.json.JsonOutput

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
 *  version: 1.0.2
 */

metadata {
    definition(name: 'Google Nest Thermostat', namespace: 'dkilgore90', author: 'David Kilgore', importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-thermostat.groovy') {
        capability 'TemperatureMeasurement'
        capability 'Thermostat'
        capability 'ThermostatCoolingSetpoint'
        capability 'ThermostatHeatingSetpoint'
        capability 'ThermostatFanMode'
        capability 'ThermostatMode'
        capability 'ThermostatOperatingState'
        capability 'RelativeHumidityMeasurement'
        capability 'Refresh'

        attribute 'connectivity', 'string'
        attribute 'fanTimeout', 'string'
        attribute 'ecoMode', 'string'
        attribute 'ecoCoolPoint', 'number'
        attribute 'ecoHeatPoint', 'number'
        attribute 'tempScale', 'string'

        command 'fanOn', [[name: 'duration', type: 'NUMBER', description: 'length of time, in seconds']]
        command 'setThermostatFanMode', [[name: 'fanmode', type: 'ENUM', constraints: ['auto', 'on']], [name: 'duration', type: 'NUMBER', description: 'length of time, in seconds']]
        command 'setEcoMode', [[name: 'ecoMode*', type: 'ENUM', constraints: ['OFF', 'MANUAL_ECO']]]
        command 'setHeatCoolSetpoint', [[name: 'heatPoint*', type: 'NUMBER'], [name: 'coolPoint*', type: 'NUMBER']]
    }
    
    preferences {
        input 'defaultFanTime', 'number', title: 'Default Fan Time (s)', 'description': 'default length of time (in seconds) that the fan will run for `fanOn`, if an explicit time is not specified', required: true, defaultValue: 900, range: "1..43200"
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

}

def initialize() {
    // Workaround for Basic Rules when thermostat does not report any fan status/modes (UK boiler)
    // https://community.hubitat.com/t/release-google-sdm-api-nest-integration/52226/1013?u=dkilgore90
    device.sendEvent(
        name: 'supportedThermostatFanModes',
        value: device.currentValue('supportedThermostatFanModes') ?: JsonOutput.toJson(['auto'])
    )
}

def refresh() {
    initialize()
    parent.getDeviceData(device)
}

def auto() {
    parent.deviceSetThermostatMode(device, 'HEATCOOL')
}

def cool() {
    parent.deviceSetThermostatMode(device, 'COOL')
}

def heat() {
    parent.deviceSetThermostatMode(device, 'HEAT')
}

def fanAuto() {
    parent.deviceSetFanMode(device, 'OFF')
}

def fanOn(duration=null) {
    if (defaultFanTime == null) {
        device.updateSetting('defaultFanTime', 900)
    }
    duration = duration ?: defaultFanTime
    def sDuration = duration.toString() + 's'
    parent.deviceSetFanMode(device, 'ON', sDuration)
}

def off() {
    parent.deviceSetThermostatMode(device, 'OFF')
}

def setCoolingSetpoint(temp) {
    def mode = device.currentValue('thermostatMode')
    if (mode == 'cool') {
        parent.deviceSetTemperatureSetpoint(device, null, temp)
    } else if (mode == 'auto') {
        def heat = device.currentValue('heatingSetpoint')
        def tempMovement = checkDeadband(heat, temp)
        if (tempMovement > 0) {
            heat = heat.toDouble() - tempMovement.toDouble()
        }
        parent.deviceSetTemperatureSetpoint(device, heat, temp)
    } else {
        log.warn("Cannot setCoolingSetpoint in thermostatMode: ${mode}")
    }
}

def setHeatingSetpoint(temp) {
    def mode = device.currentValue('thermostatMode')
    if (mode == 'heat') {
        parent.deviceSetTemperatureSetpoint(device, temp, null)
    } else if (mode == 'auto') {
        def cool = device.currentValue('coolingSetpoint')
        def tempMovement = checkDeadband(temp, cool)
        if (tempMovement > 0) {
            cool = cool.toDouble() + tempMovement.toDouble()
        }
        parent.deviceSetTemperatureSetpoint(device, temp, cool)
    } else {
        log.warn("Cannot setHeatingSetpoint in thermostatMode: ${mode}")
    }
}

def setHeatCoolSetpoint(heat, cool) {
    def mode = device.currentValue('thermostatMode')
    if (mode == 'auto') {
        def tempMovement = checkDeadband(heat, cool)
        if  (tempMovement <= 0) {
            parent.deviceSetTemperatureSetpoint(device, heat, cool)
        } else {
            log.error("Heat/Cool setpoints require a minimum deadband of 1.5*C or 2.7*F -- inputs: ${heat} / ${cool}")
        }
    } else {
        log.warn("Cannot setHeatCoolSetpoint in thermostatMode: ${mode}")
    }
}

def checkDeadband(heat, cool) {
    try {
        def deadband = getTemperatureScale() == 'F' ? 2.7 : 1.5
        def tempMovement = heat.toDouble() - cool.toDouble() + deadband.toDouble()
        return (tempMovement.round(1))
    } catch (NullPointerException e) {
        return 0
    }
}

def setThermostatMode(mode) {
    parent.deviceSetThermostatMode(device, mode == 'auto' ? 'HEATCOOL' : mode.toUpperCase())
}

def setThermostatFanMode(mode, duration=null) {
    if (defaultFanTime == null) {
        device.updateSetting('defaultFanTime', 900)
    }
    duration = duration ?: defaultFanTime
    def sDuration = mode == 'auto' ? null : duration.toString() + 's'
    parent.deviceSetFanMode(device, mode == 'auto' ? 'OFF' : 'ON', sDuration)
}

def setEcoMode(mode) {
    parent.deviceSetEcoMode(device, mode)
}

def setSchedule(sched) {
    log.info("setSchedule is not currently supported")
}

def fanCirculate() {
    log.info("fanCirculate is not currently supported")
}

def emergencyHeat() {
    log.info("emergencyHeat is not currently supported")
}

def getLastEventTime() {
    return state.lastEventTime
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