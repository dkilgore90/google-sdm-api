import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 *
 *  Copyright 2020-2022 David Kilgore. All Rights Reserved
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
 *  version: 1.0.5
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
        }
        getGoogleDriveOptions()
        section{
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
        section {
            input 'cleanupDrive', 'button', title: 'Manually run Google Drive retention cleanup', submitOnChange: true
        }
    }
}

def getAuthLink() {
    if (projectId && credentials && state?.accessToken) {
        section {
            href(
                name       : 'authHref',
                title      : 'Auth Link',
                url        : buildAuthUrl(),
                description: 'Click this link to authorize with your Google Device Access Project'
            )
        }
    } else {
        section {
            paragraph "Authorization link is hidden until the required projectId and credentials.json inputs are provided, and App installation is saved by clicking 'Done'"
        }
    }
}

def buildAuthUrl() {
    def creds = getCredentials()
    url = 'https://nestservices.google.com/partnerconnections/' + projectId + 
            '/auth?redirect_uri=https://cloud.hubitat.com/oauth/stateredirect' +
            '&state=' + getHubUID() + '/apps/' + app.id + '/handleAuth?access_token=' + state.accessToken +
            '&access_type=offline&prompt=consent&client_id=' + creds.client_id + 
            '&response_type=code&scope=https://www.googleapis.com/auth/sdm.service https://www.googleapis.com/auth/pubsub'
    if (googleDrive) {
        url = url + ' https://www.googleapis.com/auth/drive.file'
    }
    return url
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

def getGoogleDriveOptions() {
    section {
        input 'googleDrive', 'bool', title: 'Use Google Drive for image storage?', required: false, defaultValue: false, submitOnChange: true
    }
    if (googleDrive) {
        section {
            input 'retentionDays', 'number', title: 'Days to retain images in Google Drive', required: false, defaultValue: 7, submitOnChange: true
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
    unschedule(refreshLogin)
    def authCode = params.code
    String err = login(authCode)
    runEvery1Hour refreshLogin
    createEventSubscription()
    def builder = new StringBuilder()
    builder << "<!DOCTYPE html><html><head><title>Hubitat Elevation - Google SDM API</title></head><body>"
    if (err == "") {
        builder << "<h1>Congratulations!</h1>"
        builder << "<p>Google SDM API has authenticated successfully</p>"
    } else {
        builder << "<h1 style=\"color:red;\">Uh oh...</h1>"
        builder << "<p>Google SDM API received redirect from Google, but authorization is not yet complete.<br>"
        builder << "<b>${err}</b></p>"
    }
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
    rescheduleLogin()
    runEvery10Minutes checkGoogle
    schedule('0 0 23 ? * *', driveRetentionJob)
    subscribe(location, 'systemStart', initialize)
}

def installed() {
    log.info 'Google SDM API installed'
    //initialize()
    createAccessToken()
    runEvery10Minutes checkGoogle
    schedule('0 0 23 ? * *', driveRetentionJob)
    subscribe(location, 'systemStart', initialize)
}

def uninstalled() {
    log.info 'Google SDM API uninstalling'
    removeChildren()
    deleteEventSubscription()
    unschedule()
    unsubscribe()
}

def initialize(evt) {
    log.debug(evt)
    recover()
}

def recover() {
    rescheduleLogin()
    refreshAll()
}

def rescheduleLogin() {
    unschedule(refreshLogin)
    if (state?.googleRefreshToken) {
        refreshLogin()
        runEvery1Hour refreshLogin
        if (state.eventSubscription != 'v2') {
            updateEventSubscription()
        }
    }
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
        String err = "Login failed -- ${e.getLocalizedMessage()}: ${e.response.data}"
        log.error(err)
        return err
    }
    return ""
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
    case 'cleanupDrive':
        driveRetentionJob()
        break
    }
}

private void discover(refresh=false) {
    if (refresh) {
        log.info("Refreshing all device states")
    } else {
        log.info("Discovery started")
    }
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
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpGet(handleDeviceList, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
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
        log.warn("${e.message} - you need to install the appropriate driver: ${device.type}")
    } catch (IllegalArgumentException ignored) {
        //Intentionally ignored.  Expected if device id already exists in HE.
        getChildDevice(device.id)
    }
}

def processTraits(device, details) {
    logDebug("Processing data for ${device}: ${details}")
    def room = details.parentRelations?.getAt(0)?.displayName
    room ? device.setDeviceState('room', room) : null
    if (device.hasCapability('Thermostat')) {
        processThermostatTraits(device, details)
    } else {
        processCameraTraits(device, details)
    }
}

def processThermostatTraits(device, details) {
    def humidity = details.traits['sdm.devices.traits.Humidity']?.ambientHumidityPercent
    humidity ? sendEvent(device, [name: 'humidity', value: humidity, unit: '%']) : null
    def connectivity = details.traits['sdm.devices.traits.Connectivity']?.status
    connectivity ? sendEvent(device, [name: 'connectivity', value: connectivity]) : null
    def fanStatus = details.traits['sdm.devices.traits.Fan']?.timerMode
    fanStatus ? sendEvent(device, [name: 'thermostatFanMode', value: fanStatus == 'OFF' ? 'auto' : 'on']) : null
    fanStatus ? sendEvent(device, [name: 'supportedThermostatFanModes', value: JsonOutput.toJson(['auto', 'on'])]) : null
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
    def hvacRunning = isHvacRunning(device)
    if (nestHvac == 'OFF' || (nestHvac == null && !hvacRunning)) {
        operState = fanStatus == 'on' ? 'fan only' : 'idle'
    } else {
        operState = nestHvac?.toLowerCase()
    }
    operState ? sendEvent(device, [name: 'thermostatOperatingState', value: operState]) : null
    def tempScale = details.traits['sdm.devices.traits.Settings']?.temperatureScale
    tempScale ? sendEvent(device, [name: 'tempScale', value: tempScale]) : null
    if (tempScale && tempScale.substring(0, 1) != getTemperatureScale()) {
        log.warn("Overriding ${device} tempScale: ${tempScale} with HE config: ${getTemperatureScale()}")
        tempScale = getTemperatureScale() == 'F' ? 'FAHRENHEIT' : 'CELSIUS'
    }
    def coolPoint = details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.coolCelsius
    def heatPoint = details.traits['sdm.devices.traits.ThermostatTemperatureSetpoint']?.heatCelsius
    def temp = details.traits['sdm.devices.traits.Temperature']?.ambientTemperatureCelsius
    ecoCoolPoint ? sendEvent(device, [name: 'ecoCoolPoint', value: convertAndRoundTemp(ecoCoolPoint)]) : null
    ecoHeatPoint ? sendEvent(device, [name: 'ecoHeatPoint', value: convertAndRoundTemp(ecoHeatPoint)]) : null
    coolPoint ? sendEvent(device, [name: 'coolingSetpoint', value: convertAndRoundTemp(coolPoint)]) : null
    heatPoint ? sendEvent(device, [name: 'heatingSetpoint', value: convertAndRoundTemp(heatPoint)]) : null
    temp ? sendEvent(device, [name: 'temperature', value: convertTemperatureIfNeeded(temp, 'C', 1), unit: '°' + getTemperatureScale()]) : null
}

def isHvacRunning(device) {
    def hvac = device.currentValue('thermostatOperatingState')
    if (hvac == 'fan only' || hvac == 'idle') {
        return false
    } else {
        return true
    }
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
    return JsonOutput.toJson(trModes)
}

def convertAndRoundTemp(value) {
    if (getTemperatureScale() == 'F') {
        return new Double(celsiusToFahrenheit(value)).round()
    } else {
        return new Double(value * 2).round() / 2
    }
}

def processCameraTraits(device, details) {
    if (details?.traits?.get('sdm.devices.traits.CameraEventImage') != null) {
        device.setDeviceState('captureType', 'image')
    } else if (details?.traits?.get('sdm.devices.traits.CameraClipPreview') != null) {
        device.setDeviceState('captureType', 'clip')
    } else {
        device.setDeviceState('captureType', 'none')
    }
    def imgRes = details?.traits?.get('sdm.devices.traits.CameraImage')?.maxImageResolution
    imgRes?.width ? device.setDeviceState('imgWidth', imgRes.width) : null
    imgRes?.height ? device.setDeviceState('imgHeight', imgRes.height) : null
    def videoFmt = details?.traits?.get('sdm.devices.traits.CameraLiveStream')?.supportedProtocols?.getAt(0)
    videoFmt ? device.setDeviceState('videoFormat', videoFmt) : null
}

def processCameraEvents(com.hubitat.app.DeviceWrapper device, Map events, String threadState='', String threadId='') {
    events.each { key, value -> 
        if (key == 'sdm.devices.events.DoorbellChime.Chime') {
            if (threadState in ['STARTED', '', null]) {
                device.processChime()
            }
            device.processPerson(threadState, threadId) //assume person must be present in order to push doorbell
        } else if (key == 'sdm.devices.events.CameraPerson.Person') {
            device.processPerson(threadState, threadId)
        } else if (key == 'sdm.devices.events.CameraMotion.Motion') {
            device.processMotion(threadState, threadId)
        } else if (key == 'sdm.devices.events.CameraSound.Sound') {
            device.processSound(threadState, threadId)
        } else if (key == 'sdm.devices.events.CameraClipPreview.ClipPreview') {
            if (events.size() == 1) {
                // If we hit this case, need to add sessionId lookup/handling so that we can correlate for `shouldGetImage()`
                log.error('Unhandled ClipPreview event without another event type, please notify developer')
            }
        }
        def abbrKey = key.tokenize('.')[-1]
        if (device.shouldGetImage(abbrKey)) {
            String captureType = device.getDeviceState('captureType')
            if (captureType == 'image') {
                deviceSendCommand(device, 'sdm.devices.commands.CameraEventImage.GenerateImage', [eventId: value.eventId])
            } else if (captureType == 'clip' && events.containsKey('sdm.devices.events.CameraClipPreview.ClipPreview')) {
                // TODO: determine how to download/upload the clip to Google Drive for archive
                String clipUrl = events.get('sdm.devices.events.CameraClipPreview.ClipPreview').previewUrl
                logDebug("Received ClipPreview url ${clipUrl}, downloading video clip")
                def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
                def params = [uri: clipUrl, headers: headers]
                asynchttpGet(handleClipGet, params, [device: device])
                //sendEvent(device, [name: 'image', value: '<video autoplay loop><source src="' + clipUrl + '"></video>', isStateChange: true])
            }
        }
    }
}

def createEventSubscription() {
    log.info('Creating Google pub/sub event subscription')
    def params = buildSubscriptionRequest()
    asynchttpPut(putResponse, params, [params: params])
}

def retryEventSubscription() {
    log.info('Retrying Google pub/sub event subscription, which failed previously')
    createEventSubscription()
}

def buildSubscriptionRequest() {
    def creds = getCredentials()
    def uri = 'https://pubsub.googleapis.com/v1/projects/' + creds.project_id + '/subscriptions/hubitat-sdm-api'
    def headers = [ Authorization: 'Bearer ' + state.googleAccessToken ]
    def contentType = 'application/json'
    def body = [
        topic: 'projects/sdm-prod/topics/enterprise-' + projectId,
        pushConfig: [
            pushEndpoint: getFullApiServerUrl() + '/events?access_token=' + state.accessToken
        ],
        messageRetentionDuration: '600s',
        retryPolicy: [
            minimumBackoff: "10s",
            maximumBackoff: "600s"
        ]
    ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    return params
}

def putResponse(resp, data) {
    def respCode = resp.getStatus()
    if (respCode == 409) {
        log.info('createEventSubscription returned status code 409 -- subscription already exists')
    } else if (respCode != 200) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        log.error("createEventSubscription returned status code ${respCode} -- ${respError}")
        runIn(3600, retryEventSubscription)
    } else {
        logDebug(resp.getJson())
        state.eventSubscription = 'v2'
    }
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        rescheduleLogin()
        data.isRetry = true
        asynchttpPut(putResponse, data.params, data)
    }
}

def updateEventSubscription() {
    log.info('Updating Google pub/sub event subscription')
    def params = buildSubscriptionRequest()
    params.body = [subscription: params.body]
    params.body.updateMask = 'messageRetentionDuration,retryPolicy'
    asynchttpPatch(patchResponse, params, [params: params])
}

def patchResponse(resp, data) {
    def respCode = resp.getStatus()
    if (respCode != 200) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        log.error("updateEventSubscription returned status code ${respCode} -- ${respError}")
    } else {
        logDebug(resp.getJson())
        state.eventSubscription = 'v2'
    }
    if (respCode == 401 && !data.isRetry) {
        log.warn('Authorization token expired, will refresh and retry.')
        rescheduleLogin()
        data.isRetry = true
        asynchttpPatch(patchResponse, data.params, data)
    }
}

def postEvents() {
    logDebug('Event received from Google pub/sub')
    def dataString = new String(request.JSON?.message.data.decodeBase64())
    logDebug(dataString.replaceAll('[\n]', '').replaceAll('[ \t]+', ' '))
    def dataJson = new JsonSlurper().parseText(dataString)
    // format back to millisecond decimal places in case the timestamp has micro-second resolution
    int periodIndex = dataJson.timestamp.lastIndexOf('.')
    if (periodIndex) {
        dataJson.timestamp = dataJson.timestamp.substring(0, (periodIndex + 4))
        dataJson.timestamp = dataJson.timestamp+"Z" 
    } else {
        log.warn("unexpected timestamp resolution: ${dataJson.timestamp}")
    }

    try {
        if (toDateTime(dataJson.timestamp) < new Date(state.lastRecovery)) {
            logDebug("Dropping event as its timestamp ${dataJson.timestamp} is before lastRecovery ${state.lastRecovery}")
            return
        }
    } catch (java.text.ParseException e) {
        log.warn("Timestamp parse error -- timestamp: ${dataJson.timestamp}, lastRecovery: ${state.lastRecovery}")
    } catch (IllegalArgumentException) {
        //state.lastRecovery is null
        state.lastRecovery = 0
    }
    if (dataJson.relationUpdate) {
        logDebug("Dropping unhandled 'relationUpdate' event. This generally represents a device added/deleted in your home, or a change to its room assignment in Google.")
        return
    }
    def deviceId = dataJson.resourceUpdate.name.tokenize('/')[-1]
    def device = getChildDevice(deviceId)
    if (device != null) {     
        if (device.hasCapability('Thermostat')) {
            def lastEvent = device.getLastEventTime() ?: '1970-01-01T00:00:00.000Z'
            def timeCompare = -1
            try {
                timeCompare = (toDateTime(dataJson.timestamp)).compareTo(toDateTime(lastEvent))
            } catch (java.text.ParseException e) {
                //don't expect this to ever fail - catch for safety only
                log.warn("Timestamp parse error -- timestamp: ${dataJson.timestamp}, lastEventTime: ${lastEvent}")
            }
            if ( timeCompare >= 0) {
                def utcTimestamp = toDateTime(dataJson.timestamp)
                device.setDeviceState('lastEventTime', utcTimestamp.format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", location.timeZone))
                processThermostatTraits(device, dataJson.resourceUpdate)
            } else {
                log.warn("Received event out of order -- timestamp: ${dataJson.timestamp}, lastEventTime: ${lastEvent} -- refreshing device ${device}")
                getDeviceData(device)
            }
        } else {
            processCameraEvents(device, dataJson.resourceUpdate.events, dataJson.eventThreadState, dataJson.eventThreadId)
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

def refreshAll() {
    log.info('Dropping stale events with timestamp < now, and refreshing devices')
    state.lastRecovery = now()
    discover(refresh=true)
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
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpGet(handleDeviceGet, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryGet, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Device-get response code: ${respCode}, body: ${respError}")
        }
    } else {
        fullDevice = getChildDevice(data.device.getDeviceNetworkId())
        processTraits(fullDevice, resp.getJson())
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

def deviceGenerateStream(com.hubitat.app.DeviceWrapper device) {
    deviceSendCommand(device, 'sdm.devices.commands.CameraLiveStream.GenerateRtspStream', [:])
}

def deviceExtendStream(com.hubitat.app.DeviceWrapper device, String token) {
    deviceSendCommand(device, 'sdm.devices.commands.CameraLiveStream.ExtendRtspStream', [streamExtensionToken: token])
}

def deviceStopStream(com.hubitat.app.DeviceWrapper device, String token) {
    deviceSendCommand(device, 'sdm.devices.commands.CameraLiveStream.StopRtspStream', [streamExtensionToken: token])
}

def deviceSendCommand(com.hubitat.app.DeviceWrapper device, String command, Map cmdParams) {
    if (command == 'sdm.devices.commands.CameraEventImage.GenerateImage' || command == 'sdm.devices.commands.CameraLiveStream.ExtendRtspStream') {
        //log at debug as it is triggered automatically
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
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPost(handlePostCommand, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else if (respCode == 400 & data.command == 'sdm.devices.commands.CameraLiveStream.ExtendRtspStream') {
            log.warn("${data.device} stream expired, generating new stream")
            deviceGenerateStream(data.device) 
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
        } else if ((data.command == 'sdm.devices.commands.CameraLiveStream.GenerateRtspStream') || (data.command == 'sdm.devices.commands.CameraLiveStream.ExtendRtspStream')) {
            //def respJson = resp.getJson()
            def device = getChildDevice(data.device.getDeviceNetworkId())
            device.updateStreamData(resp.getJson())
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
        return device.getDeviceState('imgWidth') ?: 1920
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
        if (googleDrive) {
            def fullDevice = getChildDevice(data.device.getDeviceNetworkId())
            if (fullDevice.getFolderId()) {
                createFile(img, 'jpg', data.device)
            } else {
                log.warn("Folder is being created for device: ${data.device}, this image will be dropped.")
            }
        } else {
            sendEvent(data.device, [name: 'rawImg', value: img])
            sendEvent(data.device, [name: 'image', value: "<img src=/apps/api/${app.id}/img/${data.device.getDeviceNetworkId()}?access_token=${state.accessToken}&ts=${now()} />", isStateChange: true])
        }
    } else {
        log.error("image download failed for device ${data.device}, response code: ${respCode}")
    }
}

def handleClipGet(resp, data) {
    def respCode = resp.getStatus()
    if (respCode == 200) {
        def clip = resp.getData()
        logDebug(clip)
        if (googleDrive) {
            def fullDevice = getChildDevice(data.device.getDeviceNetworkId())
            if (fullDevice.getFolderId()) {
                createFile(clip, 'mp4', data.device)
            } else {
                log.warn("Folder is being created for device: ${data.device}, this clip will be dropped.")
            }
        } else {
            sendEvent(data.device, [name: 'rawImg', value: clip])
            sendEvent(data.device, [name: 'image', value: "<video autoplay loop><source src=/apps/api/${app.id}/img/${data.device.getDeviceNetworkId()}?access_token=${state.accessToken}&ts=${now()}></video>", isStateChange: true])
        }
    } else {
        log.error("clip download failed for device ${data.device}, response code: ${respCode}")
    }
}

def getDashboardImg() {
    def deviceId = params.deviceId
    def device = getChildDevice(deviceId)
    logDebug("Rendering image from raw data for device: ${device}")
    def img = device.currentValue('rawImg')
    render contentType: 'image/jpeg', data: img.decodeBase64(), status: 200
}

def checkGoogle() {
    def params = [
        uri: 'https://smartdevicemanagement.googleapis.com',
        timeout: 5
    ]
    asynchttpGet(handleCheckGoogle, params)
}

def handleCheckGoogle(resp, data) {
    if (resp.hasError() && (resp.getStatus() != 404)) {
        if (state.online) {
            log.warn('Google connection outage detected')
        }
        state.online = false
    } else {
        if (!state.online) {
            log.info('Google connection recovered')
            recover()
        }
        state.online = true
    }
}

def createFile(img, type, device) {
    def uri = 'https://www.googleapis.com/drive/v3/files'
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def ts = now()
    def fullDevice = getChildDevice(device.getDeviceNetworkId())
    def mime = 'image/jpeg'
    if (type == 'mp4') {
        mime = 'video/mp4'
    }
    def body = [
        mimeType: mime,
        name: "${device}-${ts}.${type}",
        parents: [
            fullDevice.getFolderId()
        ]
    ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    logDebug("Creating Google Drive file for device image: ${device}")
    asynchttpPost(handleCreateFile, params, [device: device, params: params, img: img])
}

def handleCreateFile(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPost(handleCreateFile, data.params, data)
        } else if (respCode == 404) {
            log.warn("Known folder id not found for device: ${data.device} -- resetting. A new folder will be created automatically.")
            def fullDevice = getChildDevice(data.device.getDeviceNetworkId())
            fullDevice.setDeviceState('folderId', '')
            fullDevice.getFolderId()
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Create file -- response code: ${respCode}, body: ${respError}")
        }
    } else {
        def respJson = resp.getJson()
        uploadDrive(respJson.id, data.img, data.device)
    }
}

def uploadDrive(id, img, device) {
    def uri = "https://www.googleapis.com/upload/drive/v3/files/${id}"
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def query = [ uploadType: 'media' ]
    def contentType = 'application/octet-stream'
    def body = img.decodeBase64()
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    logDebug("Uploading image data to Google Drive file for device: ${device}")
    asynchttpPatch(handleUploadDrive, params, [device: device, params: params, photoId: id])
}

def handleUploadDrive(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPatch(handleUploadDrive, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Upload image data to file -- response code: ${respCode}, body: ${respError}")
        }
    } else {
        getPhotoDataDrive(data.photoId, data.device)
    }
}

def getPhotoDataDrive(photoId, device) {
    def uri = "https://www.googleapis.com/drive/v3/files/${photoId}"
    def headers = [ Authorization: 'Bearer ' + state.googleAccessToken ]
    def contentType = 'application/json'
    def query = [ fields: 'webContentLink']
    def params = [ uri: uri, headers: headers, contentType: contentType, query: query ]
    logDebug("Retrieving photo by id to get image url for device: ${device}")
    asynchttpGet(handleGetPhotoDataDrive, params, [device: device, params: params])
}

def handleGetPhotoDataDrive(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpGet(handlePhotoGet, data.params, data)
        } else {
            log.warn("Photo-get response code: ${respCode}, body: ${respError}")
        }
    } else {
        def respJson = resp.getJson()
        sendEvent(data.device, [name: 'image', value: '<img src="' + "${respJson.webContentLink}" + '" />', isStateChange: true])
    }
}

def createFolder(device) {
    def uri = 'https://www.googleapis.com/drive/v3/files'
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def body = [
        mimeType: 'application/vnd.google-apps.folder',
        name: "Nest images: ${device}"
    ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    log.info("Creating Google Drive folder for device: ${device}")
    asynchttpPost(handleCreateFolder, params, [device: device, params: params])
}

def handleCreateFolder(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPost(handleCreateFolder, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Create folder -- response code: ${respCode}, body: ${respError}")
        }
    } else {
        def fullDevice = getChildDevice(data.device.getDeviceNetworkId())
        def respJson = resp.getJson()
        fullDevice.setDeviceState('folderId', respJson.id)
        setFolderPermissions(respJson.id, data.device)
    }
}

def setFolderPermissions(folderId, device) {
    def uri = "https://www.googleapis.com/drive/v3/files/${folderId}/permissions"
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def body = [
        role: 'reader',
        type: 'anyone',
        allowFileDiscovery: false
    ]
    def params = [ uri: uri, headers: headers, contentType: contentType, body: body ]
    log.info("Setting Google Drive folder permissions for device: ${device}")
    asynchttpPost(handleSetPermissions, params, [device: device, params: params])
}

def handleSetPermissions(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPost(handleSetPermissions, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Set permissions -- response code: ${respCode}, body: ${respError}")
        }
    }
}

def getFilesToDelete(device) {
    def retentionDate = new Date(now() - (1000 * 3600 * 24 * retentionDays)).format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", TimeZone.getTimeZone("UTC"))
    def fullDevice = getChildDevice(device.getDeviceNetworkId())
    def folderId = fullDevice.getFolderId()
    def uri = 'https://www.googleapis.com/drive/v3/files'
    def headers = [ Authorization: "Bearer ${state.googleAccessToken}" ]
    def contentType = 'application/json'
    def query = [ q: "modifiedTime < '${retentionDate}' and '${folderId}' in parents" ]
    def params = [ uri: uri, headers: headers, contentType: contentType, query: query ]
    log.info("Retrieving files to delete for device: ${device}, based on retentionDays: ${retentionDays}")
    logDebug(params)
    asynchttpGet(handleGetFilesToDelete, params, [device: device, params: params])
}

def handleGetFilesToDelete(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        if (respCode == 401 && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpGet(handleGetFilesToDelete, data.params, data)
        //} else if (respCode == 429 && data.backoffCount < 5) {
            //log.warn("Hit rate limit, backoff and retry -- response: ${respError}")
            //data.backoffCount = (data.backoffCount ?: 0) + 1
            //runIn(10, handleBackoffRetryPost, [overwrite: false, data: [callback: handleDeviceGet, data: data]])
        } else {
            log.error("Files to delete retrieval -- response code: ${respCode}, body: ${respError}")
        }
    } else {
        def respJson = resp.getJson()
        def nextPage = respJson.nextPageToken ? true : false
        def idList = []
        respJson.files.each {
            idList.add(it.id)
        }
        if (idList) {
            deleteFilesBatch(data.device, idList, nextPage)
        } else {
            log.info("No files found to delete -- device: ${data.device}")
        }
    }
}

def deleteFilesBatch(device, idList, nextPage) {
    def uri = 'https://www.googleapis.com/batch/drive/v3'
    def headers = [
        Authorization: "Bearer ${state.googleAccessToken}",
        'Content-Type': 'multipart/mixed; boundary=END_OF_PART'
    ]
    def requestContentType = 'text/plain'
    def builder = new StringBuilder()
    idList.each {
        builder << '--END_OF_PART\r\n'
        builder << 'Content-type: application/http\r\n\r\n'
        builder << "DELETE https://www.googleapis.com/drive/v3/files/${it}\r\n\r\n"
    }
    builder << '--END_OF_PART--'
    def body = builder.toString()
    def params = [ uri: uri, headers: headers, body: body, requestContentType: requestContentType ]
    log.info("Sending batched file delete request -- count: ${idList.size()} -- for device: ${device}")
    logDebug(body)
    asynchttpPost(handleDeleteFilesBatch, params, [device: device, params: params, nextPage: nextPage])
}

def handleDeleteFilesBatch(resp, data) {
    def respCode = resp.getStatus()
    if (resp.hasError()) {
        def respError = ''
        try {
            respError = resp.getErrorData().replaceAll('[\n]', '').replaceAll('[ \t]+', ' ')
        } catch (Exception ignored) {
            // no response body
        }
        // batch error at top-level is unexpected at any time -- log for further analysis
        log.error("Batch delete -- response code: ${respCode}, body: ${respError}")
    } else {
        def respData = new String(resp.getData().decodeBase64())
        logDebug(respData)
        def unauthorized = respData =~ /HTTP\/1.1 401/
        if (unauthorized && !data.isRetry) {
            log.warn('Authorization token expired, will refresh and retry.')
            rescheduleLogin()
            data.isRetry = true
            asynchttpPost(handleDeleteFilesBatch, data.params, data)
        }
        /** parse response for additional handling
        def headers = resp.getHeaders()
        def boundary = '--' + headers['Content-Type'].split('boundary=')[1]
        respData.split(boundary).each{
            def codeMatch = it =~ /HTTP\/1.1 (\d+)/
            if (codeMatch && codeMatch[0][1] == '401') {
                log.warn('Authorization token expired, will refresh and retry')
                rescheduleLogin()
                data.isRetry = true
                asynchttpPost(handleDeleteFilesBatch, data.params, data)
                return
            }
        }*/
        if (data.nextPage) {
            log.info("Additional pages of files to delete for device: ${data.device} -- will run query sequence again")
            getFilesToDelete(data.device)
        }
    }
}

def driveRetentionJob() {
    if (googleDrive) {
        log.info('Running Google Drive retention cleanup job')
        def children = getChildDevices()
        children.each {
            if (it.hasCapability('ImageCapture')) {
                getFilesToDelete(it)
            }
        }
    } else {
        log.info('Google Drive is not used for image archive, skipping retention job')
    }
}