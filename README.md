# ESP Flasher for Android

Native USB-OTG firmware flasher for **ESP8266** and **ESP32** (S2 / S3 / C3 / C6 / H2).

Connect board → Detect chip → Select firmware → Flash → Verify → Done

This is a real bootloader implementation (SLIP + Espressif ROM commands). It is **not** a simulator.

Package name: `dev.espflasher.app`

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug / 2024.2+).
2. **File → Open** this folder.
3. Let Gradle sync.
4. Plug in a phone with USB debugging, or use an emulator (USB host requires a real device).
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

Covers ESP8266 / ESP32 image validation, chip magic, SLIP, state machine, and error mapping.

## Google Play

CI, listing assets, and the exact Console steps are in **[PLAY.md](PLAY.md)**.

| Workflow | When | What |
| --- | --- | --- |
| [Android CI](.github/workflows/ci.yml) | push / PR | unit tests + debug APK |
| [Publish to Google Play](.github/workflows/release.yml) | tag `v*` or manual | signed AAB → internal track (draft) |
| [Play Store listing](.github/workflows/play-listing.yml) | metadata change / manual | icon, feature graphic, screenshots, copy |
| [GitHub Pages](.github/workflows/pages.yml) | `docs/` | privacy policy |

Privacy policy (after Pages is enabled): https://janisxyz.github.io/esp-flasher/privacy.html

### Store assets (already in the repo)

- High-res icon 512×512
- Feature graphic 1024×500
- 6 phone screenshots 1080×1920
- 7-inch and 10-inch tablet screenshots
- EN + DE listing text

Path: [`fastlane/metadata/android/`](fastlane/metadata/android/)

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

## Protocol notes

- ESP8266 and ESP32 use different `FLASH_BEGIN` layouts and reset defaults.
- Detection reads `CHIP_DETECT_MAGIC_REG` (`0x40001000`), same as esptool.
- Firmware headers are inspected (`0xE9` magic + ESP-IDF chip id) so a mismatched image requires an explicit **Flash anyway**.

Not affiliated with Espressif Systems.
