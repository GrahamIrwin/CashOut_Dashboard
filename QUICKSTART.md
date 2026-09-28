# Quickstart: Cashout on your Android phone

About 5 minutes from download to your first dashboard. Requires Android 8 or newer.
Most phones from the last several years qualify.

## 1. Get the app onto your phone

The app is the file `CashoutDashboard.apk` (about 25 MB), attached to the [latest release](https://github.com/GrahamIrwin/CashOut_Dashboard/releases/latest). Pick whichever of these is easiest:

- **Google Drive:** upload the APK from your computer, then open Drive on the phone and tap the file.
- **Email:** send it to yourself, then open the email on the phone and tap the attachment.
- **USB cable:** plug the phone in, choose **File transfer** on the phone's notification, and copy
  the APK into `Download`. Then open the **My Files** or **Files** app on the phone and tap it.

## 2. Install it

1. Tap the APK. Android will say it can't install apps from this source. Tap **Settings**, turn on
   **Allow from this source**, then press back.
2. Tap **Install**.
3. If **Google Play Protect** warns that it doesn't recognise the app, tap **More details**, then
   **Install anyway**. It's flagged only because it didn't come from the Play Store.
4. Tap **Open**, or find **Cashout** in your app drawer.

> Afterwards you can turn "Allow from this source" back off. The app keeps working.

## 3. Add your first cashout

Tap **Add cashout** (bottom right) and choose one of:

| Option | Use it for |
|---|---|
| **Take a photo** | Tonight's slip. Lay it flat in good light and fill the frame. |
| **Choose from gallery** | A stack of old slips you've already photographed (up to 50 at once). |
| **Enter manually** | No slip, or you'd rather type it in. |

**Shortcut:** in your Gallery or Photos app, select one or more slip photos and tap
**Share → Cashout**. They go straight into the review queue.

## 4. Review and save

The app reads the slip and shows what it found next to the photo. Tap the photo to zoom in and
compare the numbers.

- **Blank or highlighted fields** couldn't be read or didn't add up. Fix them by hand, or tap the
  suggested one-tap fix.
- **Cash take-home** is the handwritten number on the slip (the cash you walked out with). The app
  always leaves it blank, so type it in yourself.
- Tap **Save shift**. With a batch, **Save all N** saves every scan that passed all checks at once.
  **Discard duplicates** removes slips you'd already saved.

Everything stays editable after saving. Open the shift from the **Shifts** tab to change it.

## 5. Read your dashboard

- **Period chips** along the top: This week, 2 weeks, This month, 30 days, This year, All time,
  Custom, and Pay period.
- **Tabs:** *Overview* (totals, tip %, $/hour, goal progress), *Trends*, *Days* (day-of-week
  averages) and *Records* (best shifts).
- Tap any shift in a chart or list to open it.

## 6. Settings worth a minute

In the **Settings** tab:

- **Pay period start:** pick the first day of any pay period so the *Pay period* filter works
  (it assumes pay periods are every 2 weeks).
- **Tip goal:** a weekly, per-pay-period, monthly or yearly target shown on the Overview tab.
- **Use cash take-home for tip stats:** base your stats on what you actually took home instead of
  the POS tip figure.

## Keep your data safe

All data lives **only on this phone**. Nothing is uploaded anywhere.

- **Settings → Back up** saves a `.json` file. Put it somewhere like Google Drive.
  **Restore from backup** brings it back on a new or reset phone.
- **Export spreadsheet (CSV)** gives you a file you can open in Excel or Google Sheets.
- **Uninstalling the app deletes your shifts.** Back up first.

## Updating to a new version

Install the new `CashoutDashboard.apk` the same way as before, over the existing app. Don't
uninstall first. Your shifts are kept.

## Troubleshooting

| Problem | Fix |
|---|---|
| "App not installed" | An older copy signed with a different key is on the phone. Back up, uninstall it, then install again. |
| "There was a problem parsing the package" | The download was cut short. Copy the APK over again. |
| Lots of blank fields after a scan | Retake the photo flatter, closer, with no glare. Or tap **Retry**. |
| "Couldn't read this one" | Tap **Retry**, or type the numbers into the fields below it. |

---

*Using a USB cable with developer mode on? `adb install -r CashoutDashboard.apk` does steps 1–2 in
one go.*
