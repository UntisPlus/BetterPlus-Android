# UntisPlus

An Android client for the Untis timetable system — a hard fork of [BetterUntis](https://github.com/SapuSeven/BetterUntis) with its own identity (`com.deelabs.untisplus`), so it can be installed and used **side-by-side** with the original BetterUntis app.

## Features

- Modern, Material 3 interface built with Jetpack Compose
- Fast timetable loading with smart caching
- Multiple separate accounts/profiles
- RoomFinder to quickly find free rooms
- Info Center for events, contact hours, messages and own absences
- Class management features for teachers (absence checking, lesson topic editing, ...)
- Break notifications and auto-mute during lessons
- Home screen widgets

## Installation

**Direct download (always the latest release):**

```text
https://github.com/UntisPlus/UntisPlus-Android/releases/latest/download/UntisPlus.apk
```

[<img src="https://shields.io/badge/-Get%20it%20on%20Obtainium-blue?style=for-the-badge&logo=github&logoColor=white" alt="Get it on Obtainium" height="50">](obtainium://add?url=https%3A%2F%2Fgithub.com%2FUntisPlus%2FUntisPlus-Android)
<!-- ^ opens Obtainium directly on Android; on desktop browsers it does nothing. -->

The link above installs the signed release APK. For automatic updates, add the repo to [Obtainium](https://github.com/ImranR98/Obtainium) using the button (on a device with Obtainium installed) or manually via `UntisPlus/UntisPlus-Android`.

## Building

Requirements: Android Studio or the command line; the Gradle build pins a Java 21 toolchain and provisions it automatically if missing.

```sh
./gradlew assembleDebug           # fully FOSS build (no proprietary dependencies)
```

Debug builds use the application id suffix `.debug`, so debug and release builds install alongside each other too.

Run unit tests:

```sh
./gradlew :app:testDebugUnitTest
```

## Verify downloads

Release APKs are signed with the following keys. Obtainium and other stores show the signing certificate of installed apps — it should match one of these SHA-256 fingerprints:

| Channel | Certificate | SHA-256 |
|---|---|---|
| Release (`com.deelabs.untisplus`) | `CN=UntisPlus, O=deelabs, C=DE` | `1fb668f186c26fbb48675e24ecbad508ff450d28158dc80066f48617316727bb` |
| Debug (`.debug` app id) | `CN=UntisPlus Debug, O=deelabs, C=DE` | `90356be8bb6c85cb8650edc701dbe7979094bbfd27e74c0d4d487cd486aa2ff5` |

The fingerprints are also stamped into every GitHub release's notes automatically.

## Contact

- Email: [info@deelabs.tech](mailto:info@deelabs.tech)
- Issue tracker: this repository's issues page

## Credits & License

UntisPlus is based on the excellent work of the BetterUntis project by SapuSeven and its contributors. All credit for the original design and implementation goes there.

Licensed under the [GNU General Public License v3.0](LICENSE).
