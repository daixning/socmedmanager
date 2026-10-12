# SocMed Manager

A personal, **offline-first Android app** for social media planning: inspiration → planning → production → publishing → performance review, all in one place.

* Kotlin · Jetpack Compose (Material 3) · Room (SQLite) · no backend, no account, no subscription, **no INTERNET permission**
* Application ID: `app.socmed.manager` (never change it) · minSdk 26 (Android 8+) · targetSdk 35

## Getting the APK

### Option A — GitHub Actions (no setup on your computer)
1. Push to this repository (or open **Actions → Build APK → Run workflow**).
2. When the run is green, open it and download the **SocMedManager-apk** artifact. Unzip it to get `SocMedManager.apk`.
3. Copy it to your phone and open it (allow "Install unknown apps" for your file manager/browser when asked).

### Option B — Android Studio
1. Install **Android Studio (Ladybug or newer)**; it brings JDK 17+ and the SDK (platform 35).
2. *File → Open* this folder, wait for Gradle sync.
3. Plug in your phone (USB debugging on) and press **Run ▶**, or build an APK: *Build → Build Bundle(s)/APK(s) → Build APK(s)*.
   The file is at `app/build/outputs/apk/release/app-release.apk`.

### Option C — command line
```bash
./gradlew :core:test          # logic tests (analytics, CSV, scheduling…)
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```
Requires JDK 17+ and the Android SDK (`ANDROID_HOME`, platform 35). Run `adb install -r app/build/outputs/apk/release/app-release.apk` to install over an existing copy.

## Keeping your data when you update (important)

Android only installs an update over an existing app when **both** match:
1. the same application ID (`app.socmed.manager`, set in `app/build.gradle.kts` — don't edit it), and
2. the **same signing key**.

| Build | Key it uses |
|---|---|
| Any build with no secrets/`keystore.properties` | the committed `app/debug.keystore` — one fixed key, so successive builds from this repo update each other. It is a convenience key (its password is public), fine for a personal sideloaded app you build yourself. |
| Recommended: your own release key | create it once with `scripts/create-keystore.sh`; use it via `keystore.properties` (local) or the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` GitHub secrets (CI). |

**Pick one key and stay with it.** If you start with the committed key and later switch to your own, Android will refuse the update ("package conflicts") — you would have to uninstall first, which deletes local data. So: either use your own key from the very first install, or never switch. Back the key file up in two places.

Every update also needs `versionCode` increased in `app/build.gradle.kts` (the app is currently `1`).

**Before every update: Settings → Backup & restore → Export full backup**, and keep the .zip off the phone.

### Database safety
* Room database `socmed.db`, currently version 1, `exportSchema = true` (schemas go to `app/schemas/` — commit them).
* Changing a table = bump `version` + add a `Migration` in `AppDatabase.MIGRATIONS` (policy and example are in that file). `fallbackToDestructiveMigration` is deliberately **not** used.
* Backups made by an older app version are upgraded by the same migrations when restored.

## What works now vs. what is planned

**Working (all offline):** see *Settings → What works now* in the app. In short: full content records with platform versions; All Posts table/list; Calendar (month/week/agenda, filters, date-picker rescheduling with confirmation, unscheduled area, overdue never auto-moved); Visual Plan with 12-month overview; Platform & Status board with "Move to"; Visuals Needed; Ideas & References → planned post; Instagram profile mockup with schedule preview, layout experiment (review before applying), cover/crop, aspect ratios and date cutoff; Events with before/during/after content, relative deadlines, reviewed date shifting and post-event review; manual analytics + CSV import with mapping and preview, formulas shown, rule-based insights; photo picker/camera with local copies and thumbnails; optional local reminders; full zip backup/restore; CSV export; removable sample data; palettes and light/dark mode.

**Not built (and not faked):** Google sign-in, cloud backup, device sync, social account connections, automatic analytics retrieval, automatic publishing, link previews, true drag-and-drop (use *Move to*, the ◀ ▶ buttons, or the date picker).

See [docs/FUTURE_INTEGRATIONS.md](docs/FUTURE_INTEGRATIONS.md) for what each future feature will require before it can be built.

## Project layout
```
core/   pure Kotlin (JVM) logic with unit tests: analytics, rates, insights, CSV, event shifting, feed planning
app/    Android app
  data/   Room entities/DAO, Repository (the only writer), MediaFiles, Backup, CsvExport, sample data
  ui/     Compose screens: Home, Content (7 tabs), Post editor, Events, Analytics, Metrics, CSV import, Settings
  notify/ local reminders
```
One principle: **every view reads the same records** (`AppData`) and every edit goes through `Repository`, so a change in one tab appears in all of them and nothing is duplicated.
