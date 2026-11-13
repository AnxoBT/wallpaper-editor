# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Android app (Kotlin + Jetpack Compose) with two parts: a photo editor that fits images to the phone screen, and a live wallpaper slideshow ("Fondos") with a home-screen clock widget. Device-specific details (test phone serial, devices to avoid, the user's photo folders) live in the untracked `CLAUDE.local.md`.

## Windows version (desktop/)

`desktop/` compiles the same sources as `app/` (editor, processing, tags, picker, settings) with Compose Desktop. Android APIs are provided by small classes with the same names under `desktop/src/main/kotlin/android*` (graphics on Skia, the gallery on `PhotoLibrary`). `desktop/build.gradle.kts` lists the app files it replaces; a replacement must not share that relative path, hence names like `DesktopPlatform.kt`. Differences inside shared screens go through `ui/Platform.kt`. `R` is generated from the same XML, and `strings_desktop.xml` overrides phone wording.

- Test: `gradle :desktop:test` (no window, generated photos, screenshots in `desktop/build/tour`). `WE_REAL_WALLPAPER=1` also changes the real Windows wallpaper and restores it.
- Package: `gradle :desktop:createDistributable` (portable folder) and `:desktop:packageMsi`.
- A new Android API in shared code needs its class here too, or `:desktop` stops compiling.

## iPhone version (ios/)

A small SwiftUI app with only the wallpaper part. iOS lets no app set the wallpaper, so it exposes App Intents ("Siguiente fondo", "Fondo anterior", "Fondo al azar") that return the photo fitted to the screen, for the user's own Shortcut with "Set Wallpaper Photo" (Back Tap, widget or time automation). Fallback: it can fill a "Wallpaper Editor" album.

- It can't be built on Windows: `.github/workflows/ios.yml` (macOS runner) runs XcodeGen (`ios/project.yml`; the .xcodeproj isn't committed), unit tests, a UI test that accepts the Photos prompt and saves screenshots (`simctl privacy` no longer grants full Photos access on iOS 26), and an unsigned IPA. Download with `gh run download`.
- Iterate on a throwaway branch with normal commits, then squash into the single commit on main and delete the branch.
- `context.fill(rect)` in UIGraphicsImageRenderer uses copy blend mode; use `fill(_:blendMode: .normal)` for translucent overlays. Draw upscaled images through CGContext with high interpolation (UIImage.draw shows blocks).

## Build and test

- Use the local Gradle install, not the wrapper: `"$LOCALAPPDATA/wf-tools/gradle-8.9/bin/gradle" assembleRelease testReleaseUnitTest --console=plain -q` (JDK 17). Filter output with `grep -aE "^e:|went wrong|FAILED"`.
- Lint: `gradle ktlintCheck` (report in `app/build/reports/ktlint/`; `ignoreFailures`, so it never breaks the build) and `gradle ktlintFormat` to auto-fix. Rules in `.editorconfig`; keep it at zero findings.
- If the build fails with "El proceso no tiene acceso al archivo" on `classes.dex`, run `gradle --stop` and rebuild.
- Never bundle system fonts. The lock-screen clock style loads the phone's own font at runtime from `Settings.Secure lock_screen_theme_id_os` (`LockClockFont`); elsewhere it falls back to Playfair Display Black.
- Release APK is signed with the project's own key (`~/.android/wallpapereditor-release.jks`, passwords in the untracked `keystore.properties`; without it the release build stops). v1.0 was signed with the debug key; 1.0.0 rotated it (`~/.android/wallpapereditor-lineage.bin`, `apksigner sign --lineage`), so later APKs need only the new key. Never lose the .jks: back it up outside this PC. After each build, copy `app/build/outputs/apk/release/app-release.apk` wherever `CLAUDE.local.md` says (the user installs from there).
- `/deploy-phone` builds, tests, installs and verifies on the phone.
- Publish (only when asked): `bash tools/publish.local.sh "message"` (normal commit as AnxoBT + push main; never rewrites history). For a release: bump `versionName`/`versionCode` in `app/build.gradle.kts` (and `MARKETING_VERSION` in `ios/project.yml`), publish, then `bash tools/publish.local.sh --tag` (tags `vX.Y.Z` from `versionName`; the in-app update check compares that tag with the installed version). Run it on its own, without `cd …;` in front, or the allow rule won't match.
- Each release: update the "what's new" points in `res/values*/strings_whats_new.xml` (shown once after updating; one line per point).
- Release assets (`gh release create vX.Y.Z`): `WallpaperEditor-X.Y.Z.apk`, `WallpaperEditor-X.Y.Z-Windows.msi` / `-Windows-portable.zip` (from `:desktop:packageMsi` + `:desktop:createDistributable`, zip the app folder), `WallpaperEditor-X.Y.Z-iOS.ipa` (from the iOS workflow). Release notes: English and Spanish in collapsible `<details>`.
- The iOS workflow runs on `macos-15-intel` (the arm64 runners often had no capacity). After a force push it usually doesn't trigger by itself: `gh workflow run ios.yml --ref main`, then `gh run download`. If it fails with "job was not acquired", it was GitHub's macOS capacity: `gh run rerun`.

## Testing on the phone

- `source tools/dev.sh` gives `adb` (pinned to `$ANDROID_SERIAL`, set in the untracked `tools/dev.local.sh`), `shot`, `bi`, and guarded taps (`dtap` only on the home screen, `atap` only when the app is in front).
- Only ever target the test phone named in `CLAUDE.local.md`; never other devices on the network (the ADB guard hook blocks those listed in `.claude/adb-guard.local.json`).
- Install with `adb install -r` only. Never `am force-stop` or `pm clear` the app: that removes the live wallpaper.
- Before tapping, check `dumpsys window | grep mCurrentFocus`. If the user is using the phone (another app in front), do not take it over; build/install and say what was not verified.
- Never modify or delete the user's real photos. Use test copies in `/sdcard/Download/WE_test` and delete them by exact id. MediaStore/`ls` queries are fine.
- Test on the phone, not an emulator.

## Conventions

- All user-facing text, KDoc and code comments are in Spanish; strings live in `res/values*/strings*.xml` (`values` = English, `values-es` = Spanish; keep both in step). Reply to the user in Spanish.
- `res/layout/widget_clock_*_{none,dark,light}.xml` and `wallpaper/ClockLayouts.kt` are generated by `python tools/gen_clock_layouts.py` (one layout per font × shadow, because RemoteViews cannot change fonts). Edit the script, not the output. `widget_clock_image.xml` is hand-written.
- Bundled clock fonts are declared once on the `ClockFont` enum (`res`, `weight`); `ClockBitmap`, `ClockWidget` and the preview read them from there.

## Platform gotchas (already handled — don't regress)

- vivo: setting the lock wallpaper (`FLAG_LOCK`) while the live wallpaper is on makes the launcher stop forwarding taps to it. The lock image is deferred (`WallpaperController.pendingLock`) and applied when the home screen is hidden or the screen turns off.
- vivo: `WallpaperManager.wallpaperInfo` is unreliable; whether the live wallpaper runs is tracked by `SlideshowWallpaperService.engineRunning`.
- vivo's launcher can't load app fonts in RemoteViews, so clocks with bundled fonts are drawn as a bitmap (`ClockBitmap`) and refreshed on `TIME_TICK` by the wallpaper engine (no alarms, no background wakeups).
- `MediaStore.createWriteRequest`/`createTrashRequest` reject generic `.../file/ID` URIs (what folder/SAF photos resolve to). `OriginalReplacer.resolve` always rebuilds the URI on `MediaCollections.images`.
- "Replace original" must keep the file name, `date_modified` and folder position (the user relies on folder order).
- Battery: nothing may run in the background while the wallpaper isn't visible (no periodic jobs/alarms).
- minSdk 26: guard APIs newer than 8.0 with `Build.VERSION` checks (`catch (e: Exception)` does not catch the `NoClassDefFoundError`); e.g. `WallpaperColors` is API 27. Run `gradle lintRelease` (NewApi) after touching platform APIs.
- Widgets (RemoteViews): `TextView.setGravity` is only remotable from Android 12 ("Problem loading widget" before). Alignment goes on the root `LinearLayout` with `wrap_content` children.
- Other devices: the emulator AVDs `we_small` (720×1280), `we_a8` (Android 8.0), `we_a16` (Android 16) and `we_tablet` cover what the vivo can't. The APK only ships ARM libraries; for the Android 8 x86 image, build a throwaway APK with `x86_64` added to `abiFilters` and revert it.

## Licensing

All bundled fonts must be freely redistributable (OFL, listed in `res/raw/font_licenses.txt`). Never bundle system fonts copied from a phone: the repo is public. The vivo lock-screen style uses Playfair Display Black (weight 900).
