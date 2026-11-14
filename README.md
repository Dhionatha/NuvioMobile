<div align="center">

  <img src="https://nuvio.tv/assets/nuvio-app-logo-wordmark.webp" alt="Nuvio" width="320" />

  <p>
    A free, open-source media app for your phone, your desktop, and the TV you already own.
    <br />
    Bring your own sources. Nuvio turns them into a library with artwork, ratings, subtitles, and your place saved on every screen.
  </p>

  [Website](https://nuvio.tv) · [GitHub releases](https://github.com/NuvioMedia/NuvioMobile/releases/latest) · [Support Nuvio](https://nuvio.tv/support)

</div>

## Nuvio Custom (this repository)

This repository publishes its own Android builds as **Nuvio Custom**, under the application ID `com.dhionatha.nuvio`, so they can be installed alongside the official Nuvio app. Inside the app the branding stays "Nuvio".

- [Nuvio Custom releases](https://github.com/Dhionatha/NuvioMobile/releases/latest) — per-ABI APKs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
- Release tags use a custom suffix, for example `0.6.12-custom.1-beta`. The base version comes from upstream; one release is published per upstream `MARKETING_VERSION`.
- The in-app updater follows this repository's beta channel, so these builds update themselves.
- `store.json` and iOS builds are not published here.

## Get Nuvio Mobile

- [Android on Google Play](https://play.google.com/store/apps/details?id=com.nuvio.app)
- [Android APK](https://github.com/NuvioMedia/NuvioMobile/releases/latest)
- iOS via AltStore or SideStore: add [this source URL](https://raw.githubusercontent.com/NuvioMedia/NuvioMobile/cmp-rewrite/store.json) in the app's Sources section, then install Nuvio.

## Build from source

```bash
git clone https://github.com/NuvioMedia/NuvioMobile.git
cd NuvioMobile
```

### Android

Android development requires Android Studio and the Android SDK.

```bash
./gradlew :androidApp:assembleFullDebug
```

### iOS

iOS development requires macOS and Xcode.

```bash
env NUVIO_IOS_DISTRIBUTION=full xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -derivedDataPath build/ios-derived-full-simulator \
  CODE_SIGNING_ALLOWED=NO \
  build
```

The shared app is built with Kotlin Multiplatform and Compose Multiplatform.

## License

[GNU General Public License v3.0](./LICENSE)
