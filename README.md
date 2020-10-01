# Google SDM Api - App and Drivers for Hubitat Elevation

## Google Pre-requisite setup:
Follow the [Get Started QuickStart](https://developers.google.com/nest/device-access/get-started) from Google

Only the first page of steps is needed.

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

Copy and paste the contents of your Oauth2 credentials.json file downloaded from GCP into the `Google credentials.json` input field

Copy and paste your Google Device Access project ID into the `Google Device Access - Project ID` input field for the Google SDM API app.

Paste the following URL into your browser, substituting the {{projectId}} with your Google Device Access project ID,
and the {{client_id}} with your GCP Oauth2 client_id.

```https://nestservices.google.com/partnerconnections/{{projectId}}/auth?redirect_uri=https://www.google.com&access_type=offline&prompt=consent&client_id={{client_id}}&response_type=code&scope=https://www.googleapis.com/auth/sdm.service https://www.googleapis.com/auth/pubsub```

NOTE: you need both "scope" urls, with the space between - to enable access to the sdm api AND pub/sub api (for event subscription)

Walk through the steps of authorizing with Google.  When you are redirected to the Google search page, the URL will include a `code=` query param.
Copy the code, and paste it into the `Google authorization code` input field for the Google SDM API app.

####NOTE: Don't forget to press the **Done** button to make sure the app sticks around!

### Authorization and Discovery
Press the **Authorize** button on the Google SDM API app page.

Press the **Discover** button on the Google SDM API app page.
