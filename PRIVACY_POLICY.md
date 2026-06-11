# Privacy Policy for PeBluelink Companion

This Privacy Policy describes how your information is handled by the PeBluelink Companion app.

## 1. Information Collection and Use
PeBluelink Companion is designed to be a direct bridge between your Pebble smartwatch and Hyundai's Bluelink services.

*   **Local-Only Storage of Credentials**: The app requires your Hyundai Bluelink username, password, VIN, and PIN to function. The information you enter is stored **locally on your device**. They are never sent to any server other than the official Hyundai Bluelink API endpoints.
*   **No Third-Party Tracking**: This app does not use any analytics, tracking, or advertising frameworks. The app will not collect or share any information about how you use it.
*   **No Account Support**: There is no way to create or modify a bluelink account from this application. The developer and this application are not affiliated with Hyundai Motor Company.

## 2. Service Providers
The app communicates directly with Hyundai Bluelink servers to retrieve vehicle status and send remote commands. By using this app, your credentials and vehicle information are transmitted to Hyundai Motor Company as they would be when using the official Bluelink application.

## 3. Security
This app uses Android's `EncryptedSharedPreferences` to ensure your data is stored securely on your device. [You can view the Android source code here.](https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:security/security-crypto/src/main/java/androidx/security/crypto/EncryptedSharedPreferences.java?q=file:androidx%2Fsecurity%2Fcrypto%2FEncryptedSharedPreferences.java%20class:androidx.security.crypto.EncryptedSharedPreferences)

## 4. Contact
If you have any questions about this Privacy Policy, do not hesitate to contact the project maintainers via the GitHub repository.
