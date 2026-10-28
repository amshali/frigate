# Privacy Policy for Frigate Mobile

**Effective date:** October 28, 2026

Frigate Mobile (package `org.frigate.mobile`, "the app") is a client that displays the web interface of a Frigate NVR server that you run and control. This policy explains what data the app handles.

## Summary

- The app does not collect, transmit, or sell personal data to the developer or to any third party.
- The app has no analytics, advertising, crash-reporting, or tracking services.
- All network traffic goes only to the server address that you enter.

## Data the app stores on your device

- **Server URL:** the address you enter is saved locally in the app's private storage so you do not have to re-enter it. It is not sent to the developer or any third party.
- **Web content data:** the app displays your Frigate server in an embedded web view. Cookies, login sessions, and local storage created by your server's web interface are stored on your device by the web view and are used only to keep you signed in to your own server.

You can remove all of this data at any time by clearing the app's storage in Android settings or by uninstalling the app.

## Data sent over the network

The app connects only to the server URL you provide. It loads the Frigate web interface and periodically sends a request to `/api/` on that server to check that it is reachable. Any credentials you type are sent only to your server. Camera streams, recordings, events, and other content shown in the app are provided by your server and are not accessible to the developer.

Whether this traffic is encrypted depends on your server address. Using an `https://` address is strongly recommended. The app allows `http://` addresses so that it can connect to servers on a private network, but traffic over plain HTTP is not encrypted.

## Permissions

- **Internet (`INTERNET`):** required to connect to your Frigate server.

The app does not request access to your location, contacts, camera, microphone, photos, files, or other personal data.

## Third parties

The app does not share data with third parties and does not include third-party SDKs for analytics or advertising. Your Frigate server and any services it connects to are under your control and are not covered by this policy.

## Children's privacy

The app is not directed at children and does not knowingly collect any information from anyone.

## Data retention and deletion

The developer holds no data about you. Data stored on your device remains until you clear the app's storage or uninstall the app.

## Changes to this policy

If this policy changes, the updated version will be published at the same location with a new effective date.

## Contact

Questions about this policy can be sent through the project's issue tracker at https://github.com/amshali/frigate/issues.
