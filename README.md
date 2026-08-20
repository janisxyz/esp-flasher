# ESP Flasher for Android

Native USB-OTG firmware flasher for **ESP8266** and **ESP32** (S2 / S3 / C3 / C6 / H2).

Connect board → Detect chip → Select firmware → Flash → Verify → Done

This is a real bootloader implementation (SLIP + Espressif ROM commands). It is **not** a simulator.

Package: `espflasher.shizoghost.com`

Step-by-step publish guide: [PLAY.md](PLAY.md)

Privacy policy (GitHub Pages): https://janisxyz.github.io/esp-flasher/

Listing copy lives in `fastlane/metadata/android/en-US/`.

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug / 2024.2+).
2. **File → Open** this folder.
3. Let Gradle sync.
4. Plug in a phone with USB debugging (USB host requires a real device).
5. Run `app`.

## Hardware

Use a USB-C OTG adapter (or a phone with USB-C dual-role). Supported USB-UART chips:

- CH340 / CH341
- Silicon Labs CP210x
- FTDI
- CDC ACM
- Espressif native USB on ESP32-S2 / S3 / C3 and related chips

## First flash

1. Plug the board into the phone.
2. Grant USB permission when Android asks.
3. The app syncs with the ROM bootloader and shows the chip + flash size.
4. Choose a `.bin` (Files, Downloads, Drive, or **Share / Open with → ESP Flasher**).
5. Tap **FLASH**. Progress is the real write cursor, not a timer.

If automatic reset fails, hold **BOOT**, reconnect USB, tap **Try again**.

## Tests

```
./gradlew test
```

Debug APK: workflow **Build APK** on every `main` push. Artifact + GitHub Release `apk-v1.0-*`. Signed Play bundles: **Release AAB**.
## Architecture

```
Compose UI
    ↓
ViewModel (StateFlow)
    ↓
FlashingRepository
    ↓
Esp8266Flasher / Esp32Flasher
    ↓
EspCommand + SLIP
    ↓
UsbSerialTransport (usb-serial-for-android)
    ↓
Android USB Host API
```

Not affiliated with Espressif Systems.
