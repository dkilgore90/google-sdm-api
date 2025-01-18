# Release notes
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## 2025-01-18

### sdm-api-app:1.1.3
* bugfix: avoid duplicate device creations by retrieving before creating.

## 2024-05-07

### sdm-api-app:1.1.2
* `image` attribute iframe -- set height/width to "95%" to scale to dashboard tile

## 2024-04-18

### sdm-api-app:1.1.1
* Fix `image` events for Google drive -- iframe preview due to 3rd-party cookie restrictions

## 2023-11-12

### sdm-api-app:1.1.0
### sdm-api-camera:1.1.0
### sdm-api-display:1.1.0
### sdm-api-doorbell:1.1.0
* Support zone events

### sdm-api-zoneChild:0.0.1
* New driver for child devices to represent individual zones.

## 2023-04-12

### sdm-api-app:1.0.8
* Improve robustness for incorrect App inputs during setup, added details/examples to README

## 2023-04-11

### sdm-api-app:1.0.7
* Fix `switch` syntax for `image` events for Google Drive -- legacy cameras/jpg broken due to duplicate events

## 2023-04-06

### sdm-api-app:1.0.6
* Remove expensive debug log of mp4 clip data -- possible cause of hub load
* Publish proper `image` event for mp4 clips in Google Drive

## 2023-04-02

### sdm-api-app:1.0.5
* Only trigger doorbell "push" if eventThreadState is STARTED (or not specified for legacy doorbell)

### sdm-api-thermostat:1.0.1
* Set **supportedThermostatFanModes** to ["auto"] if not reported by device -- workaround for Basic Rules handling

## 2023-03-19

### sdm-api-app:1.0.4
* Define **supportedThermostatModes** and **supportedThermostatFanModes** as JSON_OBJECT instead of List<String>

## 2023-03-17

### sdm-api-app:1.0.3
* Send auth token when requesting clip preview

## 2022-10-29

### sdm-api-doorbell:1.0.4
* Add **push()** command definition in doorbell driver for local HE (there is no command sent to Google)

## 2022-10-09

### sdm-api-camera:1.0.3
* Bugfix: event triggers broken for older Nest devices

### sdm-api-display:1.0.3
* Bugfix: event triggers broken for older Nest devices

### sdm-api-doorbell:1.0.3
* Bugfix: event triggers broken for older Nest devices

## 2022-09-30

### sdm-api-app:1.0.2
* Bugfix: #85 - handle threadId for more reliable event clearing

### sdm-api-camera:1.0.2
* Bugfix: #85 - handle threadId for more reliable event clearing

### sdm-api-display:1.0.2
* Bugfix: #85 - handle threadId for more reliable event clearing

### sdm-api-doorbell:1.0.2
* Bugfix: #85 - handle threadId for more reliable event clearing

## 2022-07-11

### sdm-api-camera:1.0.1
* Bugfix: #82 - correct videoFmt to videoFormat when attempting to generate stream

### sdm-api-display:1.0.1
* Bugfix: #82 - correct videoFmt to videoFormat when attempting to generate stream

### sdm-api-doorbell:1.0.1
* Bugfix: #82 - correct videoFmt to videoFormat when attempting to generate stream

## 2022-02-02

### sdm-api-app:1.0.1
* Bugfix: #76 - Reflect login failure in OAuth2 redirect page

## 2021-12-19

### sdm-api-app:1.0.0
* For certain parameters, set device state values instead of sending events
* Store device "captureType" based on capabilities
* Handle event thread states
* Handle new ClipPreview for battery doorbell

### sdm-api-camera:1.0.0
* Migrate certain device attributes to state map
* Remove "lastEventType" -- redundant with actual device events
* Handle event thread states

### sdm-api-display:1.0.0
* Migrate certain device attributes to state map
* Remove "lastEventType" -- redundant with actual device events
* Handle event thread states

### sdm-api-doorbell:1.0.0
* Migrate certain device attributes to state map
* Remove "lastEventType" -- redundant with actual device events
* Handle event thread states

### sdm-api-thermostat:1.0.0
* Migrate certain device attributes to state map

## 2021-09-24

### sdm-api-app:0.6.3
* Normalize setpoints to full-degrees (\*F) and half-degrees (\*C)
* Fix line intended to log warning for missing driver, which was instead returning the string

## 2021-08-08

### sdm-api-app:0.6.2
* Bugfix: #59 -- Retry event subscription every hour
* log/drop relationUpdate events since they are not handled in code

## 2021-07-07

### sdm-api-app:0.6.1
* Fix event processing logic so that thermostatOperatingState isn't clobbered by temp/humidity updates

## 2021-05-17

### sdm-api-app:0.6.0
* Feature: Use Google Drive for image storage
* Fix case-sensitivity issue in fan timer state
* Improve logging where JSON is dumped to remove newlines, extra spaces

### sdm-api-camera:0.4.0
* Feature: Use Google Drive for image storage

### sdm-api-display:0.4.0
* Feature: Use Google Drive for image storage

### sdm-api-doorbell:0.4.0
* Feature: Use Google Drive for image storage

## 2021-04-13

### sdm-api-app:0.5.1
* Fix event processing logic to correctly update thermostatOperatingState for fan timer events
* Add units to temperature and humidity attributes

## 2020-12-10

### sdm-api-app:0.5.0
* Support device commands to create, refresh, and stop video streams

### sdm-api-camera:0.3.0
* New preference to enable/disable video stream
* New attribute: streamUrl

### sdm-api-display:0.3.0
* New preference to enable/disable video stream
* New attribute: streamUrl

### sdm-api-doorbell:0.3.0
* New preference to enable/disable video stream
* New attribute: streamUrl

## 2020-12-05

### sdm-api-app:0.4.0
* Subscribe to startup events -- refresh all devices and discard stale updates from Google
* Modify Google event subscription to reduce the retained backlog in the event of an outage

## 2020-11-25

### sdm-api-app:0.3.3
* Fix MissingMethodException in discovery by calling `toString()` on device type

## 2020-11-02

### sdm-api-thermostat:0.1.5
* [#46](https://github.com/dkilgore90/google-sdm-api/issues/46) - Check deadband only if in "auto" mode, catch NPE for additional safety

## 2020-10-31

### sdm-api-app:0.3.2
* Normalize timestamps in milliseconds (some devices report microseconds)

### sdm-api-thermostat:0.1.4
* Match Nest app behavior in HEATCOOL mode -- automatically bump opposing setpoint to maintain minimum deadband

## 2020-10-26

### sdm-api-thermostat:0.1.3
* Fix default duration setting when fanOn is called without any args

## 2020-10-19

### sdm-api-app:0.3.1
* Log current event timestamp and lastEventTime when claiming events out of order for better troubleshooting

### sdm-api-camera:0.2.2
* Reset image and rawImg attribute values when all toggles are disabled

### sdm-api-display:0.2.2
* Reset image and rawImg attribute values when all toggles are disabled

### sdm-api-doorbell:0.2.1
* Reset image and rawImg attribute values when all toggles are disabled

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