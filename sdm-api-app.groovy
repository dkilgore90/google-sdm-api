import groovy.json.JsonSlurper
import groovy.transform.Field

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
//        getAuthLink()
        section {
            input 'googleAuth', 'button', title: 'Authorize', submitOnChange: true
        }
//        section {
//            input 'refreshToken', 'button', title: 'Refresh Auth', submitOnChange: true
//        }
//        section {
//            input 'getToken', 'button', title: 'Log Access Token', submitOnChange: true
//        }
//        section {
//            input 'eventSubscribe', 'button', title: 'Subscribe to Events', submitOnChange: true
//        }
        section {
            input 'discoverDevices', 'button', title: 'Discover', submitOnChange: true
        }
        section {
            input 'deleteDevices', 'button', title: 'Delete', submitOnChange: true
        }
        listDiscoveredDevices()
    }
}

def getAuthLink() {
    def creds = getCredentials()
    section {
        href(
            name       : 'Auth Link',
            url        : 'https://nestservices.google.com/partnerconnections/' + projectId + 
                            '/auth?redirect_uri=' + getFullApiServerUrl() + '/handleAuth' +
                            '&access_type=offline&prompt=consent&client_id=' + creds.client_id + 
                            '&response_type=code&scope=https://www.googleapis.com/auth/sdm.service https://www.googleapis.com/auth/pubsub',
            description: 'Click this link to authorize with your Google Device Access Project'
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
    dynamicPage(name: "authPage", "Google Authorization Complete") {
        mainPageLink()
    }
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
    //asynchttpPost(handleLoginResponse, params)
    httpPost(params) { response -> handleLoginResponse(response) }
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
    //asynchttpPost(handleLoginResponse, params)
    httpPost(params) { response -> handleLoginResponse(response) }
}

def handleLoginResponse(resp, data=null) {
    def respCode = resp.getStatus()
    def respJson = resp.getData()
    if (respCode != 200) {
        log.warn('Login response code: ' + respCode + ', body: ' + respJson)
        return
    }
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
    asynchttpGet(handleDeviceList, params, params)
}

def handleDeviceList(resp, data) {
    def respCode = resp.getStatus()
    def respJson = resp.getJson()
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        initialize()
        data.isRetry = true
        asynchttpGet(handleDeviceGet, data, data)
    } else if (respCode != 200 ) {
        log.warn('Device-list response code: ' + respCode + ', body: ' + respJson)
    } else {
        respJson.devices.each {
            def device = [:]
            device.type = it.type.tokenize('.')[-1].toLowerCase().capitalize()
            device.id = it.name.tokenize('/')[-1]
            device.label = it.traits['sdm.devices.traits.Info'].customName
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

def processThermostatTraits(device, details) {
    log.debug(device)
    log.debug(details)
    def humidity = details.traits['sdm.devices.traits.Humidity']?.ambientHumidityPercent
    humidity ? sendEvent(device, [name: 'humidity', value: humidity]) : null
    def connectivity = details.traits['sdm.devices.traits.Connectivity']?.status
    connectivity ? sendEvent(device, [name: 'connectivity', value: connectivity]) : null
    def fanStatus = details.traits['sdm.devices.traits.Fan']?.timerMode
    fanStatus ? sendEvent(device, [name: 'thermostatFanMode', value: fanStatus == 'OFF' ? 'auto' : 'on']) : null
    def fanTimeout = details.traits['sdm.devices.traits.Fan']?.timerTimeout
    fanTimeout ? sendEvent(device, [name: 'fanTimeout', value: fanStatus == 'OFF' ? '' : fanTimeout]) : null
    def nestMode = details.traits['sdm.devices.traits.ThermostatMode']?.mode
    nestMode ? sendEvent(device, [name: 'thermostatMode', value: nestMode == 'HEATCOOL' ? 'auto' : nestMode.toLowerCase()]) : null
    def ecoMode = details.traits['sdm.devices.traits.ThermostatEco']?.mode
    ecoMode ? sendEvent(device, [name: 'ecoMode', value: ecoMode]) : null
    def nestHvac = details.traits['sdm.devices.traits.ThermostatHvac']?.status
    def operState = ''
    if (nestHvac == 'OFF') {
        operState = fanStatus == 'OFF' ? 'idle' : 'fan only'
    } else {
        operState = nestHvac?.toLowerCase()
    }
    nestHvac ? sendEvent(device, [name: 'thermostatOperatingState', value: operState]) : null
    def tempScale = details.traits['sdm.devices.traits.Settings']?.temperatureScale
    tempScale ? sendEvent(device, [name: 'tempScale', value: tempScale]) : null
    def coolPoint = ecoMode == 'OFF' ? details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.coolCelsius : details.traits['sdm.devices.traits.ThermostatEco']?.coolCelsius
    def heatPoint = ecoMode == 'OFF' ? details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.heatCelsius : details.traits['sdm.devices.traits.ThermostatEco']?.heatCelsius
    def temp = details.traits['sdm.devices.traits.Temperature']?.ambientTemperatureCelsius
    if (tempScale == 'FAHRENHEIT') {
        coolPoint = celsiusToFahrenheit(coolPoint)
        heatPoint = celsiusToFahrenheit(heatPoint)
        temp = celsiusToFahrenheit(temp)
    }
    coolPoint ? sendEvent(device, [name: 'coolingSetpoint', value: coolPoint]) : null
    heatPoint ? sendEvent(device, [name: 'heatingSetpoint', value: heatPoint]) : null
    temp ? sendEvent(device, [name: 'temperature', value: temp]) : null
    def room = details?.parentRelations?.getAt(0)?.displayName
    room ? sendEvent(device, [name: 'room', value: room]) : null
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
    asynchttpPut(putResponse, params)
}

def putResponse(resp, data) {
    log.debug(resp.getStatus())
    log.debug(resp.getData())
}

def postEvents() {
    log.debug('event received')
    def dataString = new String(request.JSON?.message.data.decodeBase64())
    log.debug(dataString)
    def dataJson = new JsonSlurper().parseText(dataString)
    def deviceId = dataJson.resourceUpdate.name.tokenize('/')[-1]
    def device = getChildDevice(deviceId)
    log.debug(device)
    if (device.hasCapability('Thermostat')) {
        processThermostatTraits(device, dataJson.resourceUpdate)
    } else {
        processCameraTraits(device, dataJson.resourceUpdate)
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