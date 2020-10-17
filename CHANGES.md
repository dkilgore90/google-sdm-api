# Release notes
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## 2020-10-17

### sdm-api-camera:0.2.1
* Remove extraneous preference for chimeImageCapture

### sdm-api-display:0.2.1
* Remove extraneous preference for chimeImageCapture

## 2020-10-16

### sdm-api-app:0.3.0
* Support different attributes per event type (Person=presence, Sound=sound)
* Catch exception parsing empty HTTP response body on error codes

### sdm-api-doorbell:0.2.0
* Add attributes per event type
* Toggle settings for image capture per event type
* Add debug logging and toggle

### sdm-api-camera:0.2.0
* Add attributes per event type
* Toggle settings for image capture per event type
* Add debug logging and toggle

### sdm-api-display:0.2.0
* Add attributes per event type
* Toggle settings for image capture per event type
* Add debug logging and toggle

## 2020-10-12

### sdm-api-app:0.2.5
* Logging cleanup
* Add settings toggle to enable/disable debug logs
* lastEventTime in local time zone

## 2020-10-09

### sdm-api-thermostat:0.1.2
* Make the default fan timer for fanOn a configurable setting

### sdm-api-app:0.2.4
* Handle and log HTTP Error responses

## 2020-10-08

### sdm-api-app:0.2.3
* [#15](https://github.com/dkilgore90/google-sdm-api/issues/15) - Evemt timestamp comparison -- allow multiple events with same timestamp
* [#16](https://github.com/dkilgore90/google-sdm-api/issues/16) - Fix NPE in timestamp comparison when no previous timestamp has been recorded -- introduced in fix for [#12](https://github.com/dkilgore90/google-sdm-api/issues/12)

## 2020-10-07

### sdm-api-app:0.2.2
* [#12](https://github.com/dkilgore90/google-sdm-api/issues/12) - Fix event timestamp comparison issue
* Fix NPE in getWidthFromSize

### sdm-api-app:0.2.1
* Preferences input for downloaded image size
* Push lastEventType attribute update to camera devices
* Enhancement to force dynamic image refresh in dashboards
* New debug button: Delete Event Subscription

### sdm-api-doorbell:0.1.1
* lastEventType attribute

### sdm-api-camera:0.1.1
* lastEventType attribute

### sdm-api-display:0.1.1
* lastEventType attribute

## 2020-10-06

### sdm-api-app:0.2.0
* Camera event processing and image capture
* Support local image display for dashboards
* Round temperature values to single decimal precision for thermostat devices

### sdm-api-thermostat:0.1.1
* lastEventTime attribute

### sdm-api-doorbell:0.1.0
* Chime support (via pushableButton)
* Motion support (active for configurable period after event)
* Image attribute available for dashboard tile

### sdm-api-camera:0.1.0
* Motion support (active for configurable period after event)
* Image attribute available for dashboard tile

### sdm-api-display:0.1.0
* Motion support (active for configurable period after event)
* Image attribute available for dashboard tile

## 2020-10-04

### sdm-api-app:0.1.0
* Account OAuth2 connection
* Event subscription setup
* Device discovery
* Thermostat commands

### sdm-api-thermostat:0.1.0
* Full command support

### sdm-api-doorbell:0.0.1
* Driver shell for discovery

### sdm-api-camera:0.0.1
* Driver shell for discovery

### sdm-api-display:0.0.1
* Driver shell for discovery