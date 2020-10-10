# Google SDM Api - App and Drivers for Hubitat Elevation

## Google Pre-requisite setup:
Follow the [Get Started QuickStart](https://developers.google.com/nest/device-access/get-started) from Google

Click on the blue "Enable the API and get an OAuth 2.0 Client ID" and follow the directions immediately above that blue button, with the following modification:

**Enter _https://cloud.hubitat.com/oauth/stateredirect_ as the value for _Authorized redirect URIs_**

Go to the [Cloud Pub/Sub API](https://console.developers.google.com/apis/library/pubsub.googleapis.com) page, and enable the Pub/Sub API
for your Google Cloud Platform (GCP) project.

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

Copy and paste the contents of your Oauth2 _credentials.json_ file downloaded from GCP into the `Google credentials.json` input field

Copy and paste your Google Device Access project ID into the `Google Device Access - Project ID` input field for the Google SDM API app.

####NOTE: Don't forget to press the **Done** button to make sure the app sticks around!

### Authorization and Discovery
On the Apps page, click the link to **Google SDM API**

Now that the previous steps are complete, you should now see a section: **Auth Link** - _Click this link to authorize with your Google Device Access Project_.

Click on this link, and walk through the steps to authorize with Google.  When complete, you will be redirected back to a basic page provided by this HE app.

Click the specified link to return to the App configuration page.  It will now display a **Discover** button

Press the **Discover** button on the Google SDM API app page to discover your authorized Nest devices.

## Features

### Camera images
The latest still image is downloaded when a device event is received (Doorbell, Camera, Display).  This can be displayed in a HE dashboard
using the `attribute` tile.  Simply select the device, and the attribute to use is `image`.

Note: this currently only works for *Local* dashboards -- support for cloud dashboards coming in a future update.

### Motion "sensor"
Any camera event will trigger a motion *Active* event.  Since the current API does not send another event when motion is no longer detected,
a preferences entry is defined for each device, which determines the amount of time before the motion is deemed *Inactive*.  If another event
is received before this timer expires, the *Inactive* transition is deferred for the specified interval.

### Doorbell Chime
The doorbell chime will generate a *pushed* event on `button 1` for the device.  This can be used for Rule Machine or Notification triggers.

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