# WiFiAware Android

Android app for local file transfer using Wi-Fi Aware.

## Requirements

- A Wi-Fi Aware capable Android device(Android 15 later)

## Build

1. Open this `android/` folder in Android Studio.
2. Wait for Gradle sync to finish.
3. Build the app from Android Studio.
4. Install the app on the test devices.

## Test Environment

- Tested with Android 15 or later API level devices
- Wi-Fi Aware capable hardware is required for discovery and transfer

## Test Step

1. Install the app on two Android devices.
2. Open the app on both devices.
3. Allow Nearby Wi-Fi and location permissions.
4. Enter the same passphrase on both devices.
5. On the receiver device, select `Receive flow`.
6. On the sender device, select `Send flow`.
7. On the sender device, choose a file.
8. Press `Scan` on both devices.
9. Select the receiver device from `Nearby devices`.
10. Press `Send file` on the sender device.
11. Press `Accept` on the receiver device.
12. Confirm that the file is saved successfully.

## Notes

- After transfer completes, the app closes the current discovery and data path session.
- To send another file, start again from `Scan`.
