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

No public releases yet. Build it yourself (see below) or grab a debug APK from CI artifacts.

## Building

Requirements: Android Studio or the command line; the Gradle build pins a Java 21 toolchain and provisions it automatically if missing.

```sh
./gradlew assembleGmsDebug        # build with Google Play services flavor
./gradlew assembleFossDebug       # build the FOSS variant (no proprietary dependencies)
```

Product flavors: `gms` (default) and `foss`. Debug builds use the application id suffix `.debug`, so debug and release builds install alongside each other too.

Run unit tests:

```sh
./gradlew :app:testGmsDebugUnitTest
```

## Contact

- Email: [info@deelabs.tech](mailto:info@deelabs.tech)
- Issue tracker: this repository's issues page

## Credits & License

UntisPlus is based on the excellent work of the BetterUntis project by SapuSeven and its contributors. All credit for the original design and implementation goes there.

Licensed under the [GNU General Public License v3.0](LICENSE).
