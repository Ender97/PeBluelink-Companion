# PeBluelink Companion

This app is the open source companion application for [PeBluelink](https://github.com/Ender97/PeBluelink). It is required for Pebble watches to interact with Hyundai Bluelink compatible vehicles. It passes commands from your watch to the Hyundai servers. Requires a Bluelink subscription and is only confirmed to work in the USA. Canada and Europe regions are currently in BETA and are not guaranteed to work. 

Android only -- I don't know how to program for iOS and don't know if this solution is even possible there.

## Features
- Fetches status updates and allows for your Pebble to issue remote commands.
- Optimized to listen for Pebble watch commands without a constant foreground service.
- Secure credential storage using `EncryptedSharedPreferences`.

## Requirements
- **Android Device**: Requires Android 8.0 (Oreo) or higher.
- **Pebble Watch**: All models supported. Install PeBluelink watchapp from Pebble appstore.
- **Hyundai Bluelink Account**: Active subscription with a compatible vehicle.

## Setup
1. Download and install the latest APK from the [Releases](#) section.
2. Open the app, select your region, and enter your Bluelink credentials (Email, Password, VIN, and PIN).
3. Tap **Save** and then **Test Connection** to verify everything is working.
4. After a successful test, you can close the app. The service will wake when commands are issued from your watch.

## Credits
Developed and maintained by Kevin A. Bizzoco (aka EnderAccord or Ender97)

API logic is based on the fantastic `egmp-bluelink-scriptable` project by andyfase. https://github.com/andyfase/egmp-bluelink-scriptable

This application and its creator are not affiliated with Hyundai Motor Company. 

This project uses:
- **Kotlin** with **Jetpack Compose** for UI.
- **Retrofit 2** for API interactions.
- **PebbleKit** for communication with the watch.

**AI Disclosure:** 
This app was developed with assistance from Anthropic's Claude Code 4.6 and Google's Gemini 3.

## Privacy Policy and License Information
See the [Privacy Policy](PRIVACY_POLICY.md) for more details on how your credentials are handled.

GNU License - see [LICENSE](LICENSE) file for details.
