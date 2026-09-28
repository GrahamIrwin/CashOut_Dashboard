# Quickstart: Cashout on your Android phone

About 5 minutes from download to your first dashboard. Requires Android 8 or newer.
Most phones from the last several years qualify.

## 1. Install Obtainium (one time)

Cashout isn't on the Play Store. [Obtainium](https://github.com/ImranR98/Obtainium) is a free app
that installs it straight from GitHub and tells you when there's an update.

1. On your phone, open the [latest Obtainium release](https://github.com/ImranR98/Obtainium/releases/latest)
   and tap the file with **arm64-v8a** in its name to download it.
2. Tap the download. Android will say it can't install apps from this source. Tap **Settings**, turn
   on **Allow from this source**, then press back and tap **Install**.
3. If **Google Play Protect** warns that it doesn't recognise the app, tap **More details**, then
   **Install anyway**. It's flagged only because it didn't come from the Play Store.
4. Open Obtainium. Allow notifications when asked. That's how it tells you about updates.

## 2. Add Cashout in Obtainium

1. In Obtainium, tap **Add App**.
2. Paste `https://github.com/GrahamIrwin/CashOut_Dashboard` into **App Source URL** and tap **Add**.
3. Tap **Install**. Android asks once more for permission, this time for Obtainium to install
   apps. Allow it, the same way as in step 1. Play Protect may warn again, so tap **Install anyway**.
4. Find **Cashout** in your app drawer.

Already have Cashout from an earlier download? Add it anyway. Obtainium finds the copy you have,
and your shifts are kept.

<details>
<summary>Rather not use Obtainium?</summary>

Download `CashoutDashboard.apk` from the [latest release](https://github.com/GrahamIrwin/CashOut_Dashboard/releases/latest)
on your phone and tap it. Allow the install the same way as in step 1. You won't get update
notifications. To update, download and install the new APK over the old one.
</details>

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

All data lives **only on this phone**. The app has no internet access, so nothing is uploaded anywhere
([privacy policy](PRIVACY.md)).

- **Settings → Back up** saves a `.json` file. Put it somewhere like Google Drive.
  **Restore from backup** brings it back on a new or reset phone.
- **Export spreadsheet (CSV)** gives you a file you can open in Excel or Google Sheets.
- **Uninstalling the app deletes your shifts.** Back up first.

## Updating to a new version

Obtainium checks for updates in the background and sends you a notification. Tap it, then
**Update**. To check yourself, open Obtainium and pull down on the list. Your shifts are kept.
Don't uninstall first.

## Troubleshooting

| Problem | Fix |
|---|---|
| "App not installed" | An older copy signed with a different key is on the phone. Back up, uninstall it, then install again. |
| "There was a problem parsing the package" | The download was cut short. Download it again. |
| Obtainium says "rate limit" | GitHub limits how often a phone can check. Wait an hour and try again. |
| Lots of blank fields after a scan | Retake the photo flatter, closer, with no glare. Or tap **Retry**. |
| "Couldn't read this one" | Tap **Retry**, or type the numbers into the fields below it. |

---

*Using a USB cable with developer mode on? `adb install -r CashoutDashboard.apk` does steps 1–2 in
one go.*
