# ESP Flasher for Android

Native USB-OTG firmware flasher for **ESP8266** and **ESP32** (S2 / S3 / C3 / C6 / H2).

**Connect board → Detect chip → Select firmware → Flash → Verify → Done**

This is a real Espressif ROM bootloader implementation (SLIP framing + `SYNC` / `FLASH_BEGIN` / `FLASH_DATA` / `MD5`). It is **not** a simulator and does not fake progress.

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug / 2024.2 or newer).
2. **File → Open** this repository.
3. Let Gradle sync. Android Studio generates the Gradle wrapper if it is missing.
4. Use a **physical phone** (USB host / OTG is required).
5. Run the `app` configuration.

Minimum SDK 26. Compile SDK 35.

## Hardware

USB-C OTG adapter (or a phone with USB-C dual-role). Supported adapters:

| Chip | Notes |
| --- | --- |
| CH340 / CH341 | Very common on NodeMCU / Wemos clones |
| Silicon Labs CP210x | Official Espressif devkits |
| FTDI | FT232 and relatives |
| CDC ACM | Generic USB serial |
| Espressif native USB | ESP32-S2 / S3 / C3 and later |

## First flash

1. Plug the board into the phone.
2. Grant USB permission when Android asks.
3. The app syncs with the ROM bootloader and shows chip + flash size.
4. Choose a `.bin` from Files, Downloads, Drive, or **Share / Open with → ESP Flasher**.
5. Tap **FLASH**. The percentage is the real write cursor.

If automatic DTR/RTS reset fails, hold **BOOT**, reconnect USB, tap **Try again**.

Incompatible images (ESP32 file on an ESP8266, etc.) are blocked unless you explicitly tap **Flash anyway**.

## Architecture

```
Compose UI  (Material 3)
    ↓
ViewModel   (StateFlow)
    ↓
FlashingRepository
    ↓
Esp8266Flasher / Esp32Flasher
    ↓
EspCommand + SLIP
    ↓
UsbSerialTransport  (usb-serial-for-android)
    ↓
Android USB Host API
```

State machine: `Disconnected → Connecting → Detecting → Ready → EnteringBootloader → Erasing → Writing → Verifying → Resetting → Success | Error`

## Tests

```
./gradlew test
```

Covers ESP8266 / ESP32 image validation, chip magic, SLIP encode/decode, the flash state machine, and error mapping.

## Package

`dev.espflasher.app`
