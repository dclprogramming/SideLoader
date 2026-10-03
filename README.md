<p align="center"><img src="assets/icon.svg" width="96" alt="SideLoader icon"></p>

# SideLoader

A small, dependency-light Android app for downloading files and sideloading APKs.
Works on phones, tablets, Android TV and Fire TV (D-pad friendly).

## Features
- Enter a URL, a bare domain, or a search term
- Built-in browser; file links are downloaded automatically
- Download progress, redirect handling, sanitized filenames
- One-tap APK install via the system installer
- **Favorites:** save URLs with a custom name (★ Save), reopen from the Favorites list, long-press to delete

## Build
Requires JDK 17 and Android Studio (or the Android SDK + Gradle 8.7).

```
gradle wrapper        # one-time: generates ./gradlew
./gradlew assembleDebug
```
APK output: `app/build/outputs/apk/debug/`. Open the folder in Android Studio to run it directly.

## CI / Releases
`.github/workflows/build.yml` builds on every push and PR. Pushing a tag like `v1.0.0` attaches the APK to a GitHub Release.

## Usage notes
- On first install, Android sends you to *Install unknown apps* to allow this app to install APKs.
- Only download and install software you trust and have the right to use.
- Google Play restricts `REQUEST_INSTALL_PACKAGES`; this project targets direct and sideload distribution.

## Roadmap
Download manager with resume, favorites import/export, history, TV pointer mode.

## License
MIT
