# AutoClicker

A minimal Android auto-clicker with a floating overlay UI:
- Draggable circular **target** — position it over the spot you want tapped.
- Floating **bubble** — tap to expand the control panel (drag the bubble to move the whole panel).
- Adjustable **interval** (milliseconds or seconds).
- Adjustable **run duration** (minutes, hours, or "forever" until you hit Stop).

It works using Android's Accessibility API to dispatch tap gestures, which is
the only way a non-rooted app can tap on behalf of the user system-wide.

## 1. Open and build the project

1. Install **Android Studio** (free): https://developer.android.com/studio
2. Open Android Studio → `Open` → select this project folder (the one containing
   `settings.gradle`). The Gradle wrapper (`gradlew`, pinned to **Gradle 8.7**)
   is already included, so it will just sync.
3. Plug in an Android phone with USB debugging on, or use an emulator, and press
   **Run ▶**. This installs a debug build directly.

### JDK version
The project is pinned to **Gradle 8.7**, which supports running on **JDK 21**
just fine (AGP 8.5.0, used here, requires Gradle 8.7+ and JDK 17+). In Android
Studio: `File → Settings → Build, Execution, Deployment → Build Tools → Gradle`
→ set "Gradle JDK" to 17 or 21, either works. The app's own compiled bytecode
stays targeted at Java 17 (`compileOptions` in `app/build.gradle`) regardless
of which JDK you use to run Gradle — that's intentional and shouldn't be changed,
since Android's toolchain doesn't benefit from a higher bytecode target here.

### Building from a terminal / VS Code instead of Android Studio
Since the wrapper is included, you don't need Android Studio installed at all —
just the Android SDK command-line tools and a JDK. From the project root:
```bash
./gradlew assembleDebug
```
(or `gradlew.bat` on Windows). See the SDK setup notes below if `ANDROID_HOME`
isn't configured yet.

### Android SDK setup (only needed if you're not using Android Studio)
Google has deprecated the old `sdkmanager` tool in favor of a unified
**Android CLI** (the `android` command). If your terminal tells you
`sdkmanager` is deprecated, use the Android CLI instead:

1. Download the Android CLI from https://developer.android.com/cli
2. Install what's needed:
   ```bash
   android sdk install "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   ```
   Other handy subcommands: `android sdk list`, `android sdk update`,
   `android sdk remove <package>`. Run `android sdk --help` if the exact
   flags differ from what's shown here — this tool is new and still evolving.
3. Create `local.properties` in the project root:
   ```
   sdk.dir=/path/to/your/Android/sdk
   ```
4. Then `./gradlew assembleDebug` as above.

   (If you already have the older `cmdline-tools` package installed with a
   working `sdkmanager`, that still functions too — you don't have to switch
   immediately, it's just the path Google now points new setups toward.)

## 2. Make the installable APK file

To get a `.apk` file you can hand to anyone (not just run from Android Studio):

1. In Android Studio: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.
2. When it finishes, click the **"locate"** link in the notification, or find it at:
   `app/build/outputs/apk/debug/app-debug.apk`

This debug APK works fine for personal use and sharing. If you want a proper
signed release build (recommended before sharing widely):

1. `Build` → `Generate Signed Bundle / APK` → `APK`.
2. Click `Create new...` to make a keystore (a password-protected signing key —
   keep this file and password safe, you'll need the *same* keystore for every
   future update of the app).
3. Choose `release`, finish. The signed APK appears at:
   `app/release/app-release.apk`

## 3. First run on the phone

1. Install the APK (you'll need to allow "install unknown apps" for whichever
   app you used to open the APK file — Android will prompt you for this).
2. Open AutoClicker. On the setup screen:
   - Tap **Grant overlay permission** → enable it for AutoClicker.
   - Tap **Enable accessibility service** → find AutoClicker in the list → turn it on.
     (Android shows a warning dialog about accessibility services being powerful —
     this is normal; the service here only performs taps, it doesn't read your screen.)
   - Tap **Start AutoClicker Overlay**.
3. A floating bubble and a draggable target circle appear. Drag the target circle
   onto the exact spot you want clicked, tap the bubble to open settings, set your
   interval/duration, and press **Start**. Press **Stop** on the bubble panel or
   pull down the notification and tap **Stop** to stop it anytime.

## 4. Publish it on GitHub so others can download it (no Google account needed)

GitHub only requires a free GitHub account (sign up with any email) — no Google
account involved, and this is a completely normal way to distribute an APK
outside the Play Store ("sideloading").

### Push the source code
```bash
cd autoclicker
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-username>/<your-repo>.git
git push -u origin main
```
(Create the empty repo first at github.com → "New repository".)

### Attach the APK as a downloadable Release
This is the part that lets people *download and install* the app — GitHub's
code view doesn't give a nice download button for a binary, but **Releases** do:

1. On your repo page, click **Releases** (right sidebar) → **Create a new release**.
2. Pick a tag, e.g. `v1.0`.
3. Drag your `app-release.apk` (or `app-debug.apk`) into the "Attach binaries"
   box at the bottom of the release form.
4. Publish the release.

Anyone can now go to your repo's Releases page and download the `.apk` directly,
then install it on their phone (they'll also need to allow "install unknown apps"
once, same as you did).

### Optional: automate building the APK on every push
If you don't want to build locally every time, add a GitHub Actions workflow
(`.github/workflows/build.yml`) that runs Gradle and uploads the APK as a build
artifact or attaches it to a release automatically — ask if you'd like that
workflow file written out too.

## Notes / limitations
- Some apps/games with anti-automation protections detect or block
  accessibility-dispatched gestures — this won't bypass those.
- Minimum supported Android version: 7.0 (API 24).
- The accessibility service here never reads screen content; it only ever
  calls `dispatchGesture()` at the coordinates you set.
