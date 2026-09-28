# Cashout Dashboard

Android app for servers: photograph the end-of-shift CASHOUT slip, the app transcribes it, and a
dashboard shows tips, tip %, $/hour, day-of-week averages and more over any period.

## Install on your phone
New to it? Follow [QUICKSTART.md](QUICKSTART.md) for a step-by-step walkthrough.

Download `CashoutDashboard.apk` from the [latest release](https://github.com/GrahamIrwin/CashOut_Dashboard/releases/latest), a signed release build (Android 8+, arm64).
Copy it to the phone (USB, Google Drive, email to yourself), tap it, and allow
"Install unknown apps" for whichever app you opened it from. Or with USB debugging on:
`adb install -r CashoutDashboard.apk`.

## Try it with demo data
`demo/demo-backup.json` is about five months of made-up shifts. Copy it to the phone, then in the app go to
**Settings → Restore from backup** and pick it. Remove the demo shifts later with **Delete all shifts**.

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
- `./gradlew testDebugUnitTest` runs the stats and validation tests, plus `ParserAccuracyTest`. That test replays OCR output
  of fake cashout slips (`testdata/ocr_dump.json`) against their answer key (`testdata/ground_truth.json`).
- The fake slips, their photos (`testdata/photos/`) and the demo backup all come from `python testdata/make_fake_data.py`.
  The fake OCR output is simulated; `OcrAccuracyTest` (instrumented) runs real on-device OCR over the photos to regenerate it.
- Real slips stay off GitHub: put their `ocr_dump.json` and `ground_truth.json` in `testdata/private/` (gitignored) and
  `ParserAccuracyTest` checks them too.
