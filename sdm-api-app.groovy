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
 *  version: 0.0.1
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

def mainPage() {
    dynamicPage(name: "mainPage", title: "Setup", install: true, uninstall: true) {
        section {
            input 'projectId', 'text', title: 'Google Device Access - Project ID', required: true, submitOnChange: false
            input 'credentials', 'text', title: 'Google credentials.json', required: true, submitOnChange: false
        }
        getAuthLink()
        getDiscoverButton()

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
    log.debug('successful redirect from google')
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

def loginAuth() {
    unschedule()
    login(auth_code)
    runEvery1Hour refreshLogin
    createEventSubscription()
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
    log.info 'Google SDM API uninstalling - removing children'
    removeChildren()
    log.info 'Google SDM API uninstalling - deleting event subscription'
    deleteEventSubscription()
    unschedule()
}

def initialize() {
    unschedule()
    refreshLogin()
    runEvery1Hour refreshLogin
}

def login(String authCode) {
    def creds = getCredentials()
    def uri = 'https://www.googleapis.com/oauth2/v4/token'
    def query = [
                    client_id    : creds.client_id,
                    client_secret: creds.client_secret,
                    code         : authCode,
                    grant_type   : 'authorization_code',
                    redirect_uri : 'https://cloud.hubitat.com/oauth/stateredirect' //'https://www.google.com'  //getFullApiServerUrl() + '/handleAuth'
                ]
    def params = [uri: uri, query: query]
    try {
        httpPost(params) { response -> handleLoginResponse(response) }
    } catch (Exception e) {
        log.error("Login failed: ${e.toString()}")
    }
}

def refreshLogin() {
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
    } catch (Exception e) {
        log.error("Login refresh failed: ${e.toString()}")
    }
}

def handleLoginResponse(resp) {
    def respCode = resp.getStatus()
    def respJson = resp.getData()
    if (respCode != 200) {
        log.warn('Login response code: ' + respCode + ', body: ' + respJson)
        return
    }
    log.debug("Authorized scopes: ${respJson.scope}")
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
    case 'googleAuth':
        loginAuth()
        break
    case 'eventSubscribe':
        createEventSubscription()
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
    def respJson = resp.getJson()
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpGet(handleDeviceList, data.params, data)
    } else if (respCode == 429 && data.backoffCount < 5) {
        log.warn('Hit rate limit, backoff and retry')
        data.backoffCount = (data.backoffCount ?: 0) + 1
        runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
    } else if (respCode != 200 ) {
        log.warn('Device-list response code: ' + respCode + ', body: ' + respJson)
    } else {
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
    try {
        addChildDevice(
            'dkilgore90',
            "Google Nest ${device.type}",
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
    def room = details.parentRelations?.getAt(0)?.displayName
    room ? sendEvent(device, [name: 'room', value: room]) : null
    if (device.hasCapability('Thermostat')) {
        processThermostatTraits(device, details)
    } else {
        processCameraTraits(device, details)
    }
}

def processThermostatTraits(device, details) {
    log.debug(device)
    log.debug(details)
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
        log.warn("Overriding ${device} tempScale: ${tempScale} with HE: ${getTemperatureScale()}")
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
            sendEvent(device, [name: 'pushed', value: 1, isStateChange: true])
        }
        device.processMotion()
        deviceSendCommand(device, 'sdm.devices.commands.CameraEventImage.GenerateImage', [eventId: value.eventId])
    }
}

def createEventSubscription() {
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
    } else if (resp.hasError()) {
        log.error("createEventSubscription returned status code ${respCode} -- ${resp.getErrorJson()}")
    } else {
        log.debug(resp.getJson())
    }
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpPut(handlePostCommand, data.params, data)
    }
}

def postEvents() {
    log.debug('event received')
    def dataString = new String(request.JSON?.message.data.decodeBase64())
    log.debug(dataString)
    def dataJson = new JsonSlurper().parseText(dataString)
    def deviceId = dataJson.resourceUpdate.name.tokenize('/')[-1]
    def device = getChildDevice(deviceId)
    if (device != null) {
        def lastEvent = device.currentValue('lastEventTime')
        def timeCompare
        try {
            timeCompare = toDateTime(dataJson.timestamp).compareTo(toDateTime(lastEvent))
        } catch (java.text.ParseException ignored) {
            //should only fail on first event -- e.g. lastEvent == null
        }
        if ( timeCompare > 0 || lastEvent == null ) {
            sendEvent(device, [name: 'lastEventTime', value: dataJson.timestamp])
            processTraits(device, dataJson.resourceUpdate)
        } else {
            log.warn("Received event out of order, refreshing device ${device}")
            getDeviceData(device)
        }
    }
}

void removeChildren() {
    def children = getChildDevices()
    log.debug(children)
    children.each {
        if (it != null) {
            log.debug(it)
            log.debug(it.getDeviceNetworkId())
            deleteChildDevice it.getDeviceNetworkId()
        }
    }
}

void deleteEventSubscription() {
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
    def deviceId = device.getDeviceNetworkId()
    def uri = 'https://smartdevicemanagement.googleapis.com/v1/enterprises/' + projectId + '/devices/' + deviceId
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def params = [ uri: uri, headers: headers, contentType: contentType ]
    asynchttpGet(handleDeviceGet, params, [device: device, params: params])
}

def handleDeviceGet(resp, data) {
    def respCode = resp.getStatus()
    def respJson = resp.getJson()
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpGet(handleDeviceGet, data.params, data)
    } else if (respCode == 429 && data.backoffCount < 5) {
        log.warn('Hit rate limit, backoff and retry')
        data.backoffCount = (data.backoffCount ?: 0) + 1
        runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
    } else if (respCode != 200 ) {
        log.error("Device-get response code: ${respCode}, body: ${respJson}")
    } else {
        processTraits(data.device, respJson)
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
    log.info("Setting coolPoint: ${coolPoint} and/or heatPoint: ${heatPoint} for device ${device}")
    if (device.currentValue('tempScale') == 'FAHRENHEIT') {
        coolPoint = coolPoint ? fahrenheitToCelsius(coolPoint) : null
        heatPoint = heatPoint ? fahrenheitToCelsius(heatPoint) : null
    }
    log.debug(coolPoint)
    log.debug(heatPoint)
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
    def respJson
    try {
        respJson = resp.getJson()
    } catch (IllegalArgumentException ignored) {
        log.warn("executeCommand ${data.command}: cannot parse JSON from response")
    }
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpPost(handlePostCommand, data.params, data)
    } else if (respCode == 429 && data.backoffCount < 5) {
        log.warn('Hit rate limit, backoff and retry')
        data.backoffCount = (data.backoffCount ?: 0) + 1
        runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
    } else if (respCode != 200) {
        log.error("executeCommand ${data.command} response code: ${respCode}, body: ${respJson}")
    } else {
        if (data.command == 'sdm.devices.commands.CameraEventImage.GenerateImage') {
            log.debug(respJson)
            def uri = respJson.results.url
            def query = [ width: data.device.currentValue('imgWidth') ]
            def headers = [ Authorization: "Basic ${respJson.results.token}" ]
            def params = [uri: uri, headers: headers, query: query]
            asynchttpGet(handleImageGet, params, [device: data.device])
        }
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
        log.debug(img.length())
        sendEvent(data.device, [name: 'rawImg', value: img])
        sendEvent(data.device, [name: 'image', value: "<img src=/apps/api/${app.id}/img/${data.device.getDeviceNetworkId()}?access_token=${state.accessToken} />", isStateChange: true])
//        sendEvent(data.device, [name: 'image', value: "<img src='data:image/jpeg;base64, ${img}' />"])
    } else {
        log.error("image download failed for device ${data.device}, response code: ${respCode}")
    }
}

def getDashboardImg() {
    log.debug('get image')
    def deviceId = params.deviceId
    def device = getChildDevice(deviceId)
    def img = device.currentValue('rawImg')
    render contentType: 'image/jpeg', data: img.decodeBase64(), status: 200
}