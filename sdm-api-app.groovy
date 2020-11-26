import groovy.json.JsonSlurper

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
 *  version: 0.3.3
 */

definition(
        name: 'Google SDM API',
        namespace: 'dkilgore90',
        author: 'David Kilgore',
        description: 'Provides for discovery and control of Google Nest devices',
        importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-app.groovy',
        category: 'Discovery',
        oauth: true,
        iconUrl: '',
        iconX2Url: '',
        iconX3Url: ''
)

preferences {
    page(name: 'mainPage')
    page(name: 'debugPage')
}

mappings {
    path("/events") {
        action: [
            POST: "postEvents"
        ]
    }
    path("/handleAuth") {
        action: [
            GET: "handleAuthRedirect"
        ]
    }
    path("/img/:deviceId") {
        action: [
            GET: "getDashboardImg"
        ]
    }
}

private logDebug(msg) {
    if (settings?.debugOutput) {
        log.debug "$msg"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Setup", install: true, uninstall: true) {
        section {
            input 'projectId', 'text', title: 'Google Device Access - Project ID', required: true, submitOnChange: false
            input 'credentials', 'text', title: 'Google credentials.json', required: true, submitOnChange: false
        }
        getAuthLink()
        getDiscoverButton()
        
        section {
            input 'imgSize', 'enum', title: 'Image download size', required: false, submitOnChange: true, options: ['small', 'medium', 'large', 'max']
            input name: "debugOutput", type: "bool", title: "Enable Debug Logging?", defaultValue: false, submitOnChange: true
        }
        
        listDiscoveredDevices()
        
        getDebugLink()
    }
}

def debugPage() {
    dynamicPage(name:"debugPage", title: "Debug", install: false, uninstall: false) {
        section {
            paragraph "Debug buttons"
        }
        section {
            input 'getToken', 'button', title: 'Log Access Token', submitOnChange: true
        }
        section {
            input 'refreshToken', 'button', title: 'Force Token Refresh', submitOnChange: true
        }
        section {
            input 'eventSubscribe', 'button', title: 'Subscribe to Events', submitOnChange: true
        }
        section {
            input 'eventUnsubscribe', 'button', title: 'Delete event subscription', submitOnChange: true
        }
        section {
            input 'deleteDevices', 'button', title: 'Delete all devices', submitOnChange: true
        }
    }
}

def getAuthLink() {
    if (projectId && credentials && state?.accessToken) {
        def creds = getCredentials()
        section {
            href(
                name       : 'authHref',
                title      : 'Auth Link',
                url        : 'https://nestservices.google.com/partnerconnections/' + projectId + 
                                '/auth?redirect_uri=https://cloud.hubitat.com/oauth/stateredirect' +
                                '&state=' + getHubUID() + '/apps/' + app.id + '/handleAuth?access_token=' + state.accessToken +
                                '&access_type=offline&prompt=consent&client_id=' + creds.client_id + 
                                '&response_type=code&scope=https://www.googleapis.com/auth/sdm.service https://www.googleapis.com/auth/pubsub',
                description: 'Click this link to authorize with your Google Device Access Project'
            )
        }
    } else {
        section {
            paragraph "Authorization link is hidden until the required projectId and credentials.json inputs are provided, and App installation is saved by clicking 'Done'"
        }
    }
}

def getDiscoverButton() {
    if (state?.googleAccessToken != null) {
        section {
            input 'discoverDevices', 'button', title: 'Discover', submitOnChange: true
        }
    } else {
        section {
            paragraph "Device discovery button is hidden until authorization is completed."
        }
    }
}

def getDebugLink() {
    section{
        href(
            name       : 'debugHref',
            title      : 'Debug buttons',
            page       : 'debugPage',
            description: 'Access debug buttons (log current googleAccessToken, force googleAccessToken refresh, retry failed event subscription, delete child devices)'
        )
    }
}

def listDiscoveredDevices() {
    def children = getChildDevices()
    def builder = new StringBuilder()
    builder << "<ul>"
    children.each {
        if (it != null) {
            builder << "<li><a href='/device/edit/${it.getId()}'>${it.getLabel()}</a></li>"
        }
    }
    builder << "</ul>"
    def links = builder.toString()
    section {
        paragraph "Discovered devices are listed below:"
        paragraph links
    }
}

def getCredentials() {
    //def uri = 'https://' + location.hub.localIP + '/local/credentials.json'
    //def creds = httpGet([uri: uri]) { response }
    def creds = new JsonSlurper().parseText(credentials)
    return creds.web
}

def handleAuthRedirect() {
    log.info('successful redirect from google')
    unschedule()
    def authCode = params.code
    login(authCode)
    runEvery1Hour refreshLogin
    createEventSubscription()
    def builder = new StringBuilder()
    builder << "<!DOCTYPE html><html><head><title>Hubitat Elevation - Google SDM API</title></head>"
    builder << "<body><p>Congratulations! Google SDM API has authenticated successfully</p>"
    builder << "<p><a href=https://${location.hub.localIP}/installedapp/configure/${app.id}/mainPage>Click here</a> to return to the App main page.</p></body></html>"
    
    def html = builder.toString()

    render contentType: "text/html", data: html, status: 200
}

def mainPageLink() {
    section {
        href(
            name       : 'Main page',
            page       : 'mainPage',
            description: 'Back to main page'
        )
    }
}

def updated() {
    log.info 'Google SDM API updating'
    initialize()
}

def installed() {
    log.info 'Google SDM API installed'
    //initialize()
    createAccessToken()
}

def uninstalled() {
    log.info 'Google SDM API uninstalling'
    removeChildren()
    deleteEventSubscription()
    unschedule()
}

def initialize() {
    unschedule()
    refreshLogin()
    runEvery1Hour refreshLogin
}

def login(String authCode) {
    log.info('Getting access_token from Google')
    def creds = getCredentials()
    def uri = 'https://www.googleapis.com/oauth2/v4/token'
    def query = [
                    client_id    : creds.client_id,
                    client_secret: creds.client_secret,
                    code         : authCode,
                    grant_type   : 'authorization_code',
                    redirect_uri : 'https://cloud.hubitat.com/oauth/stateredirect'
                ]
    def params = [uri: uri, query: query]
    try {
        httpPost(params) { response -> handleLoginResponse(response) }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error("Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
    }
}

def refreshLogin() {
    log.info('Refreshing access_token from Google')
    def creds = getCredentials()
    def uri = 'https://www.googleapis.com/oauth2/v4/token'
    def query = [
                    client_id    : creds.client_id,
                    client_secret: creds.client_secret,
                    refresh_token: state.googleRefreshToken,
                    grant_type   : 'refresh_token',
                ]
    def params = [uri: uri, query: query]
    try {
        httpPost(params) { response -> handleLoginResponse(response) }
    } catch (groovyx.net.http.HttpResponseException e) {
        log.error("Login refresh failed -- ${e.getLocalizedMessage()}: ${e.response.data}")
    }
}

def handleLoginResponse(resp) {
    def respCode = resp.getStatus()
    def respJson = resp.getData()
    logDebug("Authorized scopes: ${respJson.scope}")
    if (respJson.refresh_token) {
        state.googleRefreshToken = respJson.refresh_token
    }
    state.googleAccessToken = respJson.access_token
}

def appButtonHandler(btn) {
    switch (btn) {
    case 'discoverDevices':
        discover()
        break
    case 'eventSubscribe':
        createEventSubscription()
        break
    case 'eventUnsubscribe':
        deleteEventSubscription()
        break
    case 'deleteDevices':
        removeChildren()
        break
    case 'getToken':
        logToken()
        break
    case 'refreshToken':
        refreshLogin()
        break
    }
}

private void discover() {
    log.info("Discovery started")
    def uri = 'https://smartdevicemanagement.googleapis.com/v1/enterprises/' + projectId + '/devices'
    def headers = [ Authorization: 'Bearer ' + state.googleAccessToken ]
    def contentType = 'application/json'
    def params = [ uri: uri, headers: headers, contentType: contentType ]
    asynchttpGet(handleDeviceList, params, [params: params])
}

def handleDeviceList(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorJson()
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            initialize()
            data.isRetry = true
            asynchttpGet(handleDeviceList, data.params, data)
        } else if (respCode == 429 && data.backoffCount < 5) {
            log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            data.backoffCount = (data.backoffCount ?: 0) + 1
            runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.warn("Device-list response code: ${respCode}, body: ${respError}")
        }
    } else {
        def respJson = resp.getJson()
        respJson.devices.each {
            def device = [:]
            device.type = it.type.tokenize('.')[-1].toLowerCase().capitalize()
            device.id = it.name.tokenize('/')[-1]
            device.label = it.traits['sdm.devices.traits.Info'].customName ?: it.parentRelations[0].displayName
            def dev = makeRealDevice(device)
            if (dev != null) {
                processTraits(dev, it)
            }
        }
    }
}

def handleBackoffRetryGet(map) {
    asynchttpGet(map.callback, map.data.params, map.data)
}

def makeRealDevice(device) {
    def deviceType = "Google Nest ${device.type}"
    try {
        addChildDevice(
            'dkilgore90',
            deviceType.toString(),
            device.id,
            [
                name: device.label,
                label: device.label
            ]
        )
    } catch (com.hubitat.app.exception.UnknownDeviceTypeException e) {
        "${e.message} - you need to install the appropriate driver: ${device.type}"
    } catch (IllegalArgumentException ignored) {
        //Intentionally ignored.  Expected if device id already exists in HE.
    }
}

def processTraits(device, details) {
    logDebug("Processing data for ${device}: ${details}")
    def room = details.parentRelations?.getAt(0)?.displayName
    room ? sendEvent(device, [name: 'room', value: room]) : null
    if (device.hasCapability('Thermostat')) {
        processThermostatTraits(device, details)
    } else {
        processCameraTraits(device, details)
    }
}

def processThermostatTraits(device, details) {
    def humidity = details.traits['sdm.devices.traits.Humidity']?.ambientHumidityPercent
    humidity ? sendEvent(device, [name: 'humidity', value: humidity]) : null
    def connectivity = details.traits['sdm.devices.traits.Connectivity']?.status
    connectivity ? sendEvent(device, [name: 'connectivity', value: connectivity]) : null
    def fanStatus = details.traits['sdm.devices.traits.Fan']?.timerMode
    fanStatus ? sendEvent(device, [name: 'thermostatFanMode', value: fanStatus == 'OFF' ? 'auto' : 'on']) : null
    fanStatus ? sendEvent(device, [name: 'supportedThermostatFanModes', value: ['auto', 'on']]) : null
    def fanTimeout = details.traits['sdm.devices.traits.Fan']?.timerTimeout
    fanTimeout ? sendEvent(device, [name: 'fanTimeout', value: fanStatus == 'OFF' ? '' : fanTimeout]) : null
    def nestMode = details.traits['sdm.devices.traits.ThermostatMode']?.mode
    nestMode ? sendEvent(device, [name: 'thermostatMode', value: nestMode == 'HEATCOOL' ? 'auto' : nestMode.toLowerCase()]) : null
    def nestAvailableModes = details.traits['sdm.devices.traits.ThermostatMode']?.availableModes
    nestAvailableModes ? sendEvent(device, [name: 'supportedThermostatModes', value: translateNestAvailableModes(nestAvailableModes)]) : null
    def ecoMode = details.traits['sdm.devices.traits.ThermostatEco']?.mode
    ecoMode ? sendEvent(device, [name: 'ecoMode', value: ecoMode]) : null
    def ecoCoolPoint = details.traits['sdm.devices.traits.ThermostatEco']?.coolCelsius
    def ecoHeatPoint = details.traits['sdm.devices.traits.ThermostatEco']?.heatCelsius
    def nestHvac = details.traits['sdm.devices.traits.ThermostatHvac']?.status
    def operState = ''
    fanStatus = fanStatus ? fanStatus.toLowerCase() : device.currentValue('thermostatFanMode')
    if (nestHvac == 'OFF') {
        operState = fanStatus == 'on' ? 'fan only' : 'idle'
    } else {
        operState = nestHvac?.toLowerCase()
    }
    nestHvac ? sendEvent(device, [name: 'thermostatOperatingState', value: operState]) : null
    def tempScale = details.traits['sdm.devices.traits.Settings']?.temperatureScale
    tempScale ? sendEvent(device, [name: 'tempScale', value: tempScale]) : null
    if (tempScale && tempScale.substring(0, 1) != getTemperatureScale()) {
        log.warn("Overriding ${device} tempScale: ${tempScale} with HE config: ${getTemperatureScale()}")
        tempScale = getTemperatureScale() == 'F' ? 'FAHRENHEIT' : 'CELSIUS'
    }
    def coolPoint = details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.coolCelsius
    def heatPoint = details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.heatCelsius
    def temp = details.traits['sdm.devices.traits.Temperature']?.ambientTemperatureCelsius
    if (getTemperatureScale() == 'F') {
        ecoCoolPoint = ecoCoolPoint ? celsiusToFahrenheit(ecoCoolPoint) : null
        ecoHeatPoint = ecoHeatPoint ? celsiusToFahrenheit(ecoHeatPoint) : null
        coolPoint = coolPoint ? celsiusToFahrenheit(coolPoint) : null
        heatPoint = heatPoint ? celsiusToFahrenheit(heatPoint) : null
        temp = temp ? celsiusToFahrenheit(temp) : null
    }
    ecoCoolPoint ? sendEvent(device, [name: 'ecoCoolPoint', value: new Double(ecoCoolPoint).round(1)]) : null
    ecoHeatPoint ? sendEvent(device, [name: 'ecoHeatPoint', value: new Double(ecoHeatPoint).round(1)]) : null
    coolPoint ? sendEvent(device, [name: 'coolingSetpoint', value: new Double(coolPoint).round(1)]) : null
    heatPoint ? sendEvent(device, [name: 'heatingSetpoint', value: new Double(heatPoint).round(1)]) : null
    temp ? sendEvent(device, [name: 'temperature', value: new Double(temp).round(1)]) : null
}

def translateNestAvailableModes(modes) {
    def trModes = []
    modes.each {
        if (it == 'HEATCOOL') {
            trModes.add('auto')
        } else {
            trModes.add(it.toLowerCase())
        }
    }
    return trModes
}

def processCameraTraits(device, details) {
    if (details.events) {
        processCameraEvents(device, details.events)
    }
    def imgRes = details?.traits?.get('sdm.devices.traits.CameraImage')?.maxImageResolution
    imgRes?.width ? sendEvent(device, [name: 'imgWidth', value: imgRes.width]) : null
    imgRes?.height ? sendEvent(device, [name: 'imgHeight', value: imgRes.height]) : null   
}

def processCameraEvents(com.hubitat.app.DeviceWrapper device, Map events) {
    events.each { key, value -> 
        if (key == 'sdm.devices.events.DoorbellChime.Chime') {
            device.processChime()
            device.processPerson() //assume person must be present in order to push doorbell
        } else if (key == 'sdm.devices.events.CameraPerson.Person') {
            device.processPerson()
        } else if (key == 'sdm.devices.events.CameraMotion.Motion') {
            device.processMotion()
        } else if (key == 'sdm.devices.events.CameraSound.Sound') {
            device.processSound()
        }
        def abbrKey = key.tokenize('.')[-1]
        sendEvent(device, [name: 'lastEventType', value: abbrKey])
        if (device.shouldGetImage(abbrKey)) {
            deviceSendCommand(device, 'sdm.devices.commands.CameraEventImage.GenerateImage', [eventId: value.eventId])
        }
    }
}

def createEventSubscription() {
    log.info('Creating Google pub/sub event subscription')
    def creds = getCredentials()
    def uri = 'https://pubsub.googleapis.com/v1/projects/' + creds.project_id + '/subscriptions/hubitat-sdm-api'
    def headers = [ Authorization: 'Bearer ' + state.googleAccessToken ]
    def contentType = 'application/json'
    def body = [
        topic: 'projects/sdm-prod/topics/enterprise-' + projectId,
        pushConfig: [
            pushEndpoint: getFullApiServerUrl() + '/events?access_token=' + state.accessToken
        ]
    ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    asynchttpPut(putResponse, params, [params: params])
}

def putResponse(resp, data) {
    def respCode = resp.getStatus()
    if (respCode == 409) {
        log.warn('createEventSubscription returned status code 409 -- subscription already exists')
    } else if (respCode != 200) {
        def respError = ''
        try {
            respError = resp.getErrorJson()
        } catch (Exception ignored) {
            // no response body
        }
        log.error("createEventSubscription returned status code ${respCode} -- ${respError}")
    } else {
        logDebug(resp.getJson())
    }
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpPut(handlePostCommand, data.params, data)
    }
}

def postEvents() {
    logDebug('Event received from Google pub/sub')
    def dataString = new String(request.JSON?.message.data.decodeBase64())
    logDebug(dataString)
    def dataJson = new JsonSlurper().parseText(dataString)
    def deviceId = dataJson.resourceUpdate.name.tokenize('/')[-1]
    def device = getChildDevice(deviceId)
    if (device != null) {
        // format back to millisecond decimal places in case the timestamp has micro-second resolution
        int periodIndex = dataJson.timestamp.lastIndexOf('.')
        dataJson.timestamp = dataJson.timestamp.substring(0, (periodIndex + 4))
        dataJson.timestamp = dataJson.timestamp+"Z" 
        
        def lastEvent = device.currentValue('lastEventTime')
        if (lastEvent == null) {
            lastEvent = '1970-01-01T00:00:00.000Z'
        }
        def timeCompare = -1
        try {
            timeCompare = (toDateTime(dataJson.timestamp)).compareTo(toDateTime(lastEvent))
        } catch (java.text.ParseException e) {
            //don't expect this to ever fail - catch for safety only
        }
        if ( timeCompare >= 0) {
            def utcTimestamp = toDateTime(dataJson.timestamp)
            sendEvent(device, [name: 'lastEventTime', value: utcTimestamp.format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", location.timeZone)])
            processTraits(device, dataJson.resourceUpdate)
        } else {
            log.warn("Received event out of order -- timestamp: ${dataJson.timestamp}, lastEventTime: ${lastEvent} -- refreshing device ${device}")
            getDeviceData(device)
        }
    }
}

void removeChildren() {
    def children = getChildDevices()
    log.info("Deleting all child devices: ${children}")
    children.each {
        if (it != null) {
            deleteChildDevice it.getDeviceNetworkId()
        }
    }
}

void deleteEventSubscription() {
    log.info('Deleting Google pub/sub event subscription')
    def creds = getCredentials()
    def uri = 'https://pubsub.googleapis.com/v1/projects/' + creds.project_id + '/subscriptions/hubitat-sdm-api'
    def headers = [ Authorization: 'Bearer ' + state.googleAccessToken ]
    def contentType = 'application/json'
    def params = [uri: uri, headers: headers, contentType: contentType]
    httpDelete(params) { response ->
        log.info("Deleting event subscription: response code ${response.getStatus()}")
    }
}

def logToken() {
    log.debug("Access Token: ${state.googleAccessToken}")
}

def getDeviceData(com.hubitat.app.DeviceWrapper device) {
    log.info("Refresh device details for ${device}")
    def deviceId = device.getDeviceNetworkId()
    def uri = 'https://smartdevicemanagement.googleapis.com/v1/enterprises/' + projectId + '/devices/' + deviceId
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def params = [ uri: uri, headers: headers, contentType: contentType ]
    asynchttpGet(handleDeviceGet, params, [device: device, params: params])
}

def handleDeviceGet(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorJson()
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            initialize()
            data.isRetry = true
            asynchttpGet(handleDeviceGet, data.params, data)
        } else if (respCode == 429 && data.backoffCount < 5) {
            log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            data.backoffCount = (data.backoffCount ?: 0) + 1
            runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Device-get response code: ${respCode}, body: ${respError}")
        }
    } else {
        processTraits(data.device, resp.getJson())
    }
}

def deviceSetThermostatMode(com.hubitat.app.DeviceWrapper device, String mode) {
    deviceSendCommand(device, 'sdm.devices.commands.ThermostatMode.SetMode', [mode: mode])
}

def deviceSetFanMode(com.hubitat.app.DeviceWrapper device, String mode, duration=null) {
    Map params = [timerMode: mode]
    if (duration) {
        params.duration = duration
    }
    deviceSendCommand(device, 'sdm.devices.commands.Fan.SetTimer', params)
}

def deviceSetTemperatureSetpoint(com.hubitat.app.DeviceWrapper device, heatPoint=null, coolPoint=null) {
    if (device.currentValue('ecoMode') == 'MANUAL_ECO') {
        log.warn('Cannot adjust temperature setpoint(s) when device is in MANUAL_ECO mode')
        return
    }
    if (device.currentValue('tempScale') == 'FAHRENHEIT') {
        coolPoint = coolPoint ? fahrenheitToCelsius(coolPoint) : null
        heatPoint = heatPoint ? fahrenheitToCelsius(heatPoint) : null
    }
    if (coolPoint && heatPoint) {
        deviceSendCommand(device, 'sdm.devices.commands.ThermostatTemperatureSetpoint.SetRange', [coolCelsius: coolPoint, heatCelsius: heatPoint])
    } else if (coolPoint) {
        deviceSendCommand(device, 'sdm.devices.commands.ThermostatTemperatureSetpoint.SetCool', [coolCelsius: coolPoint])
    } else if (heatPoint) {
        deviceSendCommand(device, 'sdm.devices.commands.ThermostatTemperatureSetpoint.SetHeat', [heatCelsius: heatPoint])
    }
}

def deviceSetEcoMode(com.hubitat.app.DeviceWrapper device, String mode) {
    deviceSendCommand(device, 'sdm.devices.commands.ThermostatEco.SetMode', [mode: mode])
}

def deviceSendCommand(com.hubitat.app.DeviceWrapper device, String command, Map cmdParams) {
    if (command == 'sdm.devices.commands.CameraEventImage.GenerateImage') {
        //log GenerateImage at debug as it is triggered automatically
        logDebug("Sending ${command} to ${device} with params: ${cmdParams}")
    } else {
        log.info("Sending ${command} to ${device} with params: ${cmdParams}")
    }
    def deviceId = device.getDeviceNetworkId()
    def uri = 'https://smartdevicemanagement.googleapis.com/v1/enterprises/' + projectId + '/devices/' + deviceId + ':executeCommand'
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def body = [ command: command, params: cmdParams ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    asynchttpPost(handlePostCommand, params, [device: device, command: command, params: params])
}

def handlePostCommand(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorJson()
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            initialize()
            data.isRetry = true
            asynchttpPost(handlePostCommand, data.params, data)
        } else if (respCode == 429 && data.backoffCount < 5) {
            log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            data.backoffCount = (data.backoffCount ?: 0) + 1
            runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("executeCommand ${data.command} response code: ${respCode}, body: ${respError}")
        }
    } else {
        if (data.command == 'sdm.devices.commands.CameraEventImage.GenerateImage') {
            def respJson = resp.getJson()
            def uri = respJson.results.url
            logDebug("GenerateImage returned url ${uri}, downloading image")
            def query = [ width: getWidthFromSize(data.device) ]
            def headers = [ Authorization: "Basic ${respJson.results.token}" ]
            def params = [uri: uri, headers: headers, query: query]
            asynchttpGet(handleImageGet, params, [device: data.device])
        }
    }
}

def getWidthFromSize(device) {
    switch (imgSize) {
    case 'small':
        return 240
        break
    case 'medium':
        return 480
        break
    case 'large':
        return 960
        break
    case 'max':
    default:
        return device.currentValue('imgWidth')
        break
    }
}

def handleBackoffRetryPost(map) {
//disable backoff/retry for now
//    asynchttpPost(map.callback, map.data.params, map.data)
}

def handleImageGet(resp, data) {
    def respCode = resp.getStatus()
    if (respCode == 200) {
        def img = resp.getData()
        sendEvent(data.device, [name: 'rawImg', value: img])
        sendEvent(data.device, [name: 'image', value: "<img src=/apps/api/${app.id}/img/${data.device.getDeviceNetworkId()}?access_token=${state.accessToken}&ts=${now()} />", isStateChange: true])
//        sendEvent(data.device, [name: 'image', value: "<img src='data:image/jpeg;base64, ${img}' />"])
    } else {
        log.error("image download failed for device ${data.device}, response code: ${respCode}")
    }
}

def getDashboardImg() {
    def deviceId = params.deviceId
    def device = getChildDevice(deviceId)
    logDebug("Rendering image from raw data for device: ${device}")
    def img = device.currentValue('rawImg')
    render contentType: 'image/jpeg', data: img.decodeBase64(), status: 200
}