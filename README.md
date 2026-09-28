# Cashout Dashboard

Android app for servers: photograph the end-of-shift CASHOUT slip, the app transcribes it, and a
dashboard shows tips, tip %, $/hour, day-of-week averages and more over any period.

## Install on your phone
New to it? Follow [QUICKSTART.md](QUICKSTART.md) for a step-by-step walkthrough.

The easy way is [Obtainium](https://github.com/ImranR98/Obtainium). Add this repo's URL and it installs the app and
notifies you about updates. Or download `CashoutDashboard.apk` from the
[latest release](https://github.com/GrahamIrwin/CashOut_Dashboard/releases/latest) (Android 8+, arm64) and tap it. With
USB debugging on, `adb install -r CashoutDashboard.apk` also works.

## Try it with demo data
`demo/demo-backup.json` is about five months of made-up shifts. Copy it to the phone, then in the app go to
**Settings → Restore from backup** and pick it. Remove the demo shifts later with **Delete all shifts**.

## Build
Requires the Android SDK (platform 34) and a JDK 17+ (Android Studio's bundled JBR works):
```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```
Signing needs `keystore/release.jks` and its passwords in `local.properties` (both gitignored, never commit them):
```
storePassword=...
keyPassword=...
```
**Back up both somewhere safe outside this folder, such as a password manager.** Every update must be signed with this
exact key. If you lose it, nobody can update. They'd have to back up, uninstall and reinstall.

## Releasing an update
Obtainium watches this repo's GitHub Releases, so publishing a release *is* pushing the update. Everyone who added the
app gets a notification.

1. **Bump the version** in `app/build.gradle.kts`. Raise `versionCode` by 1 (Android refuses an update whose
   `versionCode` isn't higher) and set `versionName` to the new version, e.g. `1.4`. Settings shows `versionName` automatically.
2. **Build and test:**
   ```
   JAVA_HOME="C:/Program Files/Android/Android Studio/jbr" ./gradlew testDebugUnitTest assembleRelease
   cp app/build/outputs/apk/release/app-release.apk CashoutDashboard.apk
   ```
   Install it on your own phone first (`adb install -r CashoutDashboard.apk`) and check it works.
3. **Commit and push:**
   ```
   git commit -am "Short description (1.4)"
   git push
   ```
4. **Publish the release.** The tag is `v` plus `versionName`. The APK has to be attached, since that's what Obtainium downloads:
   ```
   gh release create v1.4 CashoutDashboard.apk --title 1.4 --notes "What changed, in plain words for your friends."
   ```

Don't mark it as a pre-release. Obtainium skips those by default, so nobody would get it. A bad release can't be taken
back from phones that already updated. Fix it forward with a new version and a higher `versionCode`.

## How scanning works
Everything runs on the phone. The manifest strips the `INTERNET` permission that ML Kit adds for its usage metrics,
so the app can't upload anything. See [PRIVACY.md](PRIVACY.md).
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
