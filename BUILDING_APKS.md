# Getting real .apk files

This repo can't be compiled inside a locked-down sandbox (no Android SDK,
no network access to Google's Maven repo). The easiest way to get real
`.apk` files without installing anything is to let GitHub build them for
you — GitHub's own runners already have full internet access and the
Android SDK preinstalled.

## Steps

1. Create a new repo on GitHub (public repos get free Actions minutes;
   private repos get a generous free monthly quota too).
2. Push this whole folder to it:
   ```bash
   git init
   git add .
   git commit -m "CloudX"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Go to the repo's **Actions** tab. The **"Build CloudX APKs"** workflow
   will already have run automatically (it triggers on push to `main`).
   If it hasn't, click it and hit **"Run workflow"**.
4. Open the finished run. Under **Artifacts** at the bottom of the
   summary page you'll see:
   - `cloudx-server-debug-apk`
   - `cloudx-client-debug-apk`
   Download each (they're zips containing the `.apk`), unzip, and
   install on your phones (`adb install app-debug.apk`, or copy the
   file to the phone and tap it — you'll need to allow "install from
   unknown sources" once).

## If you'd rather build locally instead

Open `cloudx-server-app` and `cloudx-client-app` in Android Studio
separately (see `cloudx-client-app/SETUP_GUIDE.md`), let Gradle sync
(this generates the missing Gradle wrapper and pulls all dependencies),
then **Build → Build Bundle(s) / APK(s) → Build APK(s)** for each
project. Debug APKs land in `app/build/outputs/apk/debug/`.

## Note

Both workflow jobs build **debug** APKs (`assembleDebug`), which are
unsigned except for Android's default debug key — fine for installing
on your own devices, not for distribution. If you ever want a signed
release build, that needs a keystore + signing config added to each
`app/build.gradle`, which isn't set up here yet.
