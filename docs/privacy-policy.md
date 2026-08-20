# Privacy Policy for ESP Flasher

**Effective date:** 20 August 2026  
**App:** ESP Flasher  
**Package:** `espflasher.shizoghost.com`  
**Developer:** Janis Schelling (shizoghost)  
**Contact:** [shizoghost@exdonuts.com](mailto:shizoghost@exdonuts.com)

Canonical HTML: <https://janisxyz.github.io/esp-flasher/privacy-policy>

ESP Flasher is a local utility. It flashes ESP8266 and ESP32 firmware from your phone to a USB-OTG connected board.

## Data we collect

ESP Flasher does **not** collect, sell, share, or transmit personal data.

- No accounts
- No analytics or crash reporting SDKs
- No advertising
- No internet permission

## Data that stays on the device

Firmware `.bin` files you pick are read locally and written to the connected chip. They are not uploaded anywhere.

Language, theme, verify-after-flash, erase-before-flash, and similar settings are stored in the app’s private preferences on this phone. Android backup is disabled for this app, so settings are not copied to cloud backup. Uninstalling the app deletes them.

USB serial traffic stays between the phone and the board. Technical logs shown in the app are kept in memory and are not sent anywhere.

## Permissions

- **USB host** is required to talk to an ESP board.
- Storage access is limited to the firmware file you pick through the system file picker.

## Children

The app is not directed at children and does not knowingly collect data from anyone.

## Changes

If this policy changes, we will update this page and the date above.

## Not affiliated

ESP Flasher is an independent tool. It is not affiliated with, endorsed by, or sponsored by Espressif Systems.
