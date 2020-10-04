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
 */

definition(
        name: 'Google SDM API',
        namespace: 'dkilgore90',
        author: 'David Kilgore',
        description: 'Provides for discovery and control of Google Nest devices',
        importUrl: 'https://raw.githubusercontent.com/dkilgore90/google-sdm-api/master/sdm-api-app.groovy',
        category: 'Discovery',
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
}

def mainPage() {
    dynamicPage(name: "mainPage", title: "Setup", install: true, uninstall: true) {
        section {
            input 'projectId', 'text', title: 'Google Device Access - Project ID', required: true, submitOnChange: true
            input 'auth_code', 'text', title: 'Google authorization code', required: true, submitOnChange: true
            input 'credentials', 'text', title: 'Google credentials.json', required: true, submitOnChange: true
        }
//        section {
//            paragraph "Add this link in Google as valid redirect_uri: '" + getFullApiServerUrl() + "/handleAuth'"
//        }
        getAuthLink()
//        section {
//            input 'googleAuth', 'button', title: 'Authorize', submitOnChange: true
//        }
//        section {
//            input 'refreshToken', 'button', title: 'Refresh Auth', submitOnChange: true
//        }
//        section {
//            input 'getToken', 'button', title: 'Log Access Token', submitOnChange: true
//        }
//        section {
//            input 'eventSubscribe', 'button', title: 'Subscribe to Events', submitOnChange: true
//        }
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
            input 'deleteDevices', 'button', title: 'Delete', submitOnChange: true
        }
    }
}

def getAuthLink() {
    def creds = getCredentials()
    if (projectId != null && creds.client_id != null && state.accessToken) {
        section {
            href(
                name       : 'Auth Link',
                url        : 'https://nestservices.google.com/partnerconnections/' + projectId + 
                                '/auth?redirect_uri=https://cloud.hubitat.com/oauth/stateredirect' +
                                '&state=' + getFullApiServerUrl() + '/handleAuth?access_token=' + state.accessToken
                                '&access_type=offline&prompt=consent&client_id=' + creds.client_id + 
                                '&response_type=code&scope=https://www.googleapis.com/auth/sdm.service https://www.googleapis.com/auth/pubsub',
                description: 'Click this link to authorize with your Google Device Access Project'
            )
        }
    } else {
        section {
            paragraph "Authorization link is hidden until the required projectId and credentials.json inputs are provided."
        }
    }
}

def getDiscoverButton() {
    if (state.googleAccessToken != null) {
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
            name       : 'Debug buttons',
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
    unschedule()
    def authCode = params.code
    login(authCode)
    runEvery1Hour refreshLogin
    createEventSubscription()
    def http =  """
    <!DOCTYPE html>
    <html>
        <head><title>Hubitat Elevation - Google SDM API</title></head>
        <body>
            <p>Congratulations! Google SDM API has authenticated successfully</p>
            <p><a href=https://${location.hub.localIP}/installedapp/configure/${app.id}/mainPage>Click here</a> to return to the App main page.</p>
        </body>
    </html>"""

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
    log.debug 'Google SDM API updating'
    initialize()
}

def installed() {
    log.debug 'Google SDM API installed'
    initialize()
    createAccessToken()
}

def uninstalled() {
    log.debug 'Google SDM API uninstalling - removing children'
    removeChildren()
    deleteEventSubscription()
    unsubscribe()
}

def initialize() {
    unschedule()
    refreshLogin()
    runEvery1Hour refreshLogin
}

def login(String authCode) {
    def creds = getCredentials()
    log.debug(creds)
    def uri = 'https://www.googleapis.com/oauth2/v4/token'
    def query = [
                    client_id    : creds.client_id,
                    client_secret: creds.client_secret,
                    code         : authCode,
                    grant_type   : 'authorization_code',
                    redirect_uri : 'https://www.google.com'  //getFullApiServerUrl() + '/handleAuth'
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
        runIn(10, handleBackoffRetryGet, [callback: handleDeviceList, data: data])
    } else if (respCode != 200 ) {
        log.warn('Device-list response code: ' + respCode + ', body: ' + respJson)
    } else {
        respJson.devices.each {
            def device = [:]
            device.type = it.type.tokenize('.')[-1].toLowerCase().capitalize()
            device.id = it.name.tokenize('/')[-1]
            device.label = it.traits['sdm.devices.traits.Info'].customName ?: it.parentRelations[0].displayName
            def dev = makeRealDevice(device)
            if (dev) {
                switch (it.type) {
                case 'sdm.devices.types.THERMOSTAT':
                    processThermostatTraits(dev, it)
                    break
                case 'sdm.devices.types.DOORBELL':
                case 'sdm.devices.types.CAMERA':
                case 'sdm.devices.types.DISPLAY':
                    processCameraTraits(dev, it)
                    break
                }
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
    ecoCoolPoint ? sendEvent(device, [name: 'ecoCoolPoint', value: ecoCoolPoint]) : null
    ecoHeatPoint ? sendEvent(device, [name: 'ecoHeatPoint', value: ecoHeatPoint]) : null
    coolPoint ? sendEvent(device, [name: 'coolingSetpoint', value: coolPoint]) : null
    heatPoint ? sendEvent(device, [name: 'heatingSetpoint', value: heatPoint]) : null
    temp ? sendEvent(device, [name: 'temperature', value: temp]) : null
    def room = details?.parentRelations?.getAt(0)?.displayName
    room ? sendEvent(device, [name: 'room', value: room]) : null
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
    
}

def createEventSubscription() {
    createAccessToken()
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
    log.debug(resp.getStatus())
    if (resp.hasError()) {
        log.error(resp.getErrorJson())
    } else {
        log.debug(resp.getJson())
    }
    log.debug(resp.getData())
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
    log.debug(device)
    processTraits(device, dataJson.resourceUpdate)
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
        runIn(10, handleBackoffRetryGet, [callback: handleDeviceGet, data: data])
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
    try {
        def respJson = resp.getJson()
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
        runIn(10, handleBackoffRetryPost, [callback: handlePostCommand, data: data])
    } else if (respCode != 200) {
        log.error("executeCommand ${data.command} response code: ${respCode}, body: ${respJson}")
    }
}

def handleBackoffRetryPost(map) {
    asynchttpPost(map.callback, map.data.params, map.data)
}