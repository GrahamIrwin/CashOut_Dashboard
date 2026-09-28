# Cashout Dashboard

Android app for servers: photograph the end-of-shift CASHOUT slip, the app transcribes it, and a
dashboard shows tips, tip %, $/hour, day-of-week averages and more over any period.

## Install on your phone
New to it? Follow [QUICKSTART.md](QUICKSTART.md) for a step-by-step walkthrough.

Download `CashoutDashboard.apk` from the [latest release](https://github.com/GrahamIrwin/CashOut_Dashboard/releases/latest), a signed release build (Android 8+, arm64).
Copy it to the phone (USB, Google Drive, email to yourself), tap it, and allow
"Install unknown apps" for whichever app you opened it from. Or with USB debugging on:
`adb install -r CashoutDashboard.apk`.

## Build
Requires the Android SDK (platform 34) and a JDK 17+ (Android Studio's bundled JBR works):
```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```
Keep `keystore/release.jks` — future updates must be signed with the same key to install over the old app.

## How scanning works
Everything runs on the phone; nothing is uploaded.
- ML Kit text recognition → `RowBuilder` stitches OCR fragments back into printed rows (handles
  tilted / curled / sideways photos) → `ReceiptParser` extracts the fields.
- `Validation` cross-checks the slip's own arithmetic (payments sum to total, total − tips = net,
  food + L/W/B = sales, sales ÷ covers = avg check) and offers one-tap fixes where the maths gives the answer.
- Anything that couldn't be read comes through blank and highlighted on the review screen for manual entry.
  Every field (including payment lines and transfers) is editable, before and after saving.
- The handwritten number on the slip is the **cash take-home**; it's left blank for you to enter.

## Tests
- `./gradlew testDebugUnitTest` — stats and validation tests, plus `ParserAccuracyTest`, which replays real OCR output
  of the 22 sample photos (`testdata/ocr_dump.json`) against a hand-made answer key (`testdata/ground_truth.json`).
- `OcrAccuracyTest` (instrumented) regenerates `ocr_dump.json` on a device/emulator.
