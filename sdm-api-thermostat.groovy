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
 *  version: 0.1.4
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

        attribute 'room', 'string'
        attribute 'connectivity', 'string'
        attribute 'fanTimeout', 'string'
        attribute 'ecoMode', 'string'
        attribute 'ecoCoolPoint', 'number'
        attribute 'ecoHeatPoint', 'number'
        attribute 'tempScale', 'string'
        attribute 'lastEventTime', 'string'

        command 'fanOn', [[name: 'duration', type: 'NUMBER', description: 'length of time, in seconds']]
        command 'setThermostatFanMode', [[name: 'fanmode', type: 'ENUM', constraints: ['auto', 'on']], [name: 'duration', type: 'NUMBER', description: 'length of time, in seconds']]
        command 'setEcoMode', [[name: 'ecoMode*', type: 'ENUM', constraints: ['OFF', 'MANUAL_ECO']]]
        command 'setHeatCoolSetpoint', [[name: 'heatPoint*', type: 'NUMBER'], [name: 'coolPoint*', type: 'NUMBER']]
    }
    
    preferences {
        input 'defaultFanTime', 'number', title: 'Default Fan Time (s)', 'description': 'default length of time (in seconds) that the fan will run for `fanOn`, if an explicit time is not specified', required: true, defaultValue: 900, range: "1..43200"
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

}

def refresh() {
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
    def tempMovement = checkDeadband(device.currentValue('heatingSetpoint'), temp)
    if (tempMovement <= 0) {
        tempMovement = device.currentValue('heatingSetpoint')
    } else {
        tempMovement = device.currentValue('heatingSetpoint').toFloat() - tempMovement.toFloat()
    }
    def mode = device.currentValue('thermostatMode')
    if (mode == 'cool') {
        parent.deviceSetTemperatureSetpoint(device, null, temp)
    } else if (mode == 'auto') {
        parent.deviceSetTemperatureSetpoint(device, tempMovement, temp)
    } else {
        log.warn("Cannot setCoolingSetpoint in thermostatMode: ${mode}")
    }
}

def setHeatingSetpoint(temp) {
    def tempMovement = checkDeadband(temp, device.currentValue('coolingSetpoint'))
    if (tempMovement <= 0) {
        tempMovement = device.currentValue('coolingSetpoint')
    } else {
        tempMovement = device.currentValue('coolingSetpoint').toFloat() + tempMovement.toFloat()
    }
    def mode = device.currentValue('thermostatMode')
    if (mode == 'heat') {
        parent.deviceSetTemperatureSetpoint(device, temp, null)
    } else if (mode == 'auto') {
        parent.deviceSetTemperatureSetpoint(device, temp, tempMovement)
    } else {
        log.warn("Cannot setHeatingSetpoint in thermostatMode: ${mode}")
    }
}

def setHeatCoolSetpoint(heat, cool) {
    def tempMovement = checkDeadband(heat, cool)
    def mode = device.currentValue('thermostatMode')
    if (mode == 'auto') {
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
    def deadband = getTemperatureScale() == 'F' ? 2.7 : 1.5
    def tempMovement = heat.toFloat() - cool.toFloat() + deadband
    return tempMovement
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



