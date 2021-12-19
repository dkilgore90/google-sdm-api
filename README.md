# Google SDM Api - App and Drivers for Hubitat Elevation

## Google Pre-requisite setup:
Follow the [Get Started QuickStart](https://developers.google.com/nest/device-access/get-started) from Google. Steps are outlined below, along with modifications specific to integrating with Hubitat.

1. [Go to the Device Access Console](https://console.nest.google.com/device-access)
2. Login using the same Google account used to manage your Nest devices, and accept the **Google API** and **Device Access Sandbox** terms of service. (You do not need to create a project yet).
3. Click on the blue "Enable the API and get an OAuth 2.0 Client ID" button in [this page section](https://developers.google.com/nest/device-access/get-started#set_up_google_cloud_platform).
4. If you have an existing GCP project that you would like to use for Device Access, make sure you are logged into the admin account for that project first. Then select the existing project from the list of available projects after clicking the button. Otherwise, create a new project.
5. Select Web Server when it asks "Where are you calling from?".
6. Enter _https://cloud.hubitat.com/oauth/stateredirect_ as the value for **Authorized redirect URIs**
7. Once setup is complete, copy the **OAuth 2.0 Client ID** and **Client Secret** values, and download the **Credentials JSON** to your local machine.  You will need these values later.
8. [Return to the Device Access Console](https://console.nest.google.com/device-access)
9. At the Console home screen, select **+ Create project**
10. Enter a name for your project.
11. Enter the **OAuth 2.0 Client ID** generated above during _Set up Google Cloud Platform_
12. Enable events so that asynchronous events will be sent via Google Cloud Pub/Sub
13. Upon completion, your project is assigned a **Project ID**, in the form of a UUID. You will need this when creating the Hubitat App later.
14. Go to the [Cloud Pub/Sub API](https://console.developers.google.com/apis/library/pubsub.googleapis.com) page, and enable the Pub/Sub API for your Google Cloud Platform (GCP) project.

## Installation

### SDM Api App
On the Apps Code page, press the **New App** button

Paste the code for `sdm-api-app.groovy` and press the **Save** button (or type **Ctrl-S**)

Press the **OAuth** button, then press **Enable OAuth in App**, then press the **Update** button.

### Individual Device Drivers
On the Devices Code page, press the **New Driver** button 

Paste the code for `sdm-api-thermostat.groovy` and save

Repeat this process for each of the device handlers you want to install.

### Create the app
On the Apps page press the **Add User App** button then click on **Google SDM API** in the list of available apps.

Copy and paste the contents of your Oauth2 _credentials.json_ file downloaded from GCP (step 7 in Google Pre-requisites above) into the `Google credentials.json` input field

Copy and paste your Google Device Access project ID (step 13 in Google Pre-requisites above) into the `Google Device Access - Project ID` input field for the Google SDM API app.

**NOTE**: Don't forget to press the **Done** button to make sure the app sticks around!

**NOTE 2**: If you want to use Google Drive to store still images from Nest devices, please refer to the additional steps below in the [**Google Drive**](#google-drive) section.

### Authorization and Discovery
On the Apps page, click the link to **Google SDM API**

Now that the previous steps are complete, you should now see a section: **Auth Link** - _Click this link to authorize with your Google Device Access Project_.

Click on this link, and walk through the steps to authorize with Google.  When complete, you will be redirected back to a basic page provided by this HE app.

Click the specified link to return to the App configuration page.  It will now display a **Discover** button

Press the **Discover** button on the Google SDM API app page to discover your authorized Nest devices.

## Upgrading to v1.0.0 (or higher) from v0.x.y
Many previous device attributes are migrated into the device state variables in order to reduce event noise.  It is recommended that you run a fresh "Discover" after applying the new code to Hubitat for the App/Drivers.  If this is not done, you may see some transient errors, when the code attempts to retrieve data from state -- if the desired data is not found, the device _will_ automatically refresh in order to populate the state. 

## Features

### Camera images
The latest still image is downloaded when a device event is received (Doorbell, Camera, Display).  This can be displayed in a HE dashboard
using the `attribute` tile.  Simply select the device, and the attribute to use is `image`.

Most of Google's 2021 models -- battery cam (indoor/outdoor), indoor hardwired cam, and floodlight cam -- do not support any image capture via the API, even on device events.

Google's 2021 battery doorbell supports a 10-frame mp4 clip preview -- this is handled using the same attributes as prior still image captures

#### Google Drive
New feature in App version 0.6.0, and corresponding Camera/Display/Doorbell driver versions 0.4.0, which allows the user to use
Google Drive as storage for the still images downloaded from Nest events.  This feature enables the latest image to be displayed in
both Local *and* Cloud dashboards.  Before enabling this feature, the user needs to enable the Drive API:

1. Login to the [Google API Console](https://console.developers.google.com/)
2. In the top menu bar, click the `Select a Project` drop-down. Select the existing Project for your SDM API/Nest integration
3. On the Google Dashboard that loads, click `+ ENABLE APIS and SERVICES` at the top
4. Enter `Drive` in the search bar, then select the `Google Drive API`
5. Click `ENABLE` on the next screen

After enabling this feature using the preferences toggle, the user is required to re-authorize with Google in order to obtain the updated auth scopes.  An additional input will be displayed to set the number of days for retention of images (default 7) -- a cleanup job is scheduled to run at 11pm every night, which will clean up any files the App has uploaded that are older than this setting.

### Motion "sensor"
A "Motion" event from Google will trigger a motion *Active* event in HE.  Since the current API does not send another event when motion is no longer detected,
a preferences entry is defined for each device, which determines the amount of time before the motion is deemed *Inactive*.  If another motion event
is received before this timer expires, the *Inactive* transition is deferred for the specified interval.

### "Presence sensor"
A "Person" event from Google will trigger a presence *Present* event in HE. A preferences entry determining the length of "presence" is defined
as for motion above.

### Sound "sensor"
A "Sound" event from Google will trigger a sound *detected* event in HE.  A preferences entry determining the length of sound "detection" is defined
as for motion above.

### Doorbell Chime
The doorbell chime will generate a *pushed* event on `button 1` for the device.

Any of these HE events can be used for Rule Machine or Notification triggers.

## Debug buttons -- not required for normal operation -- but useful to retry actions that might have failed during setup, such as the event subscription

### Log Access Token
This button will log your current Google access-token, so that you can use it to manually make calls to google for testing

### Force token refresh
This button will trigger the App to refresh your Google access-token.

### Subscribe to Events
This button will attempt to create the event subscription with Google pub/sub -- useful if this initially failed for any reason.  
If a matching subscription already exists, it will log a 409 warning.  If this occurs and you are not receiving events, it's possible
that the existing subscription does not match the _local_ state.accessToken which is being used by HE to authorize the incoming POST.

### Delete event subscription
This button will delete the current Google pub/sub event subscription.  If you need to use this button, immediately follow up with the
above **Subscribe to Events** button to re-create your event stream.

### Delete all devices
This button will delete all child devices from HE, which represent your physical Nest devices.  Useful if you need/want to clean up and
start over, without needing to re-authorize with Google.  You can follow this up with the **Discover** button on the main App page to
re-discover your devices.

## Troubleshooting

### Google Auth link/button not showing up
The App checks that all required prerequisites are present before revealing this button. This includes the following:

#### App Settings
* Google Device Access - Project ID
* Google credentials.json

#### Application State
* accessToken -- this _should_ be created automatically when the app is first installed

You can validate these entries by going to the App page in HE, then click on the settings **gear** icon in the top-right corner.