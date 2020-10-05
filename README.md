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
