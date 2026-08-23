# AGENTS.md

UntisPlus (`com.deelabs.untisplus`): fork of BetterUntis, an Android client for the Untis timetable system. Upstream code packages were fully renamed `com.sapuseven.untis` → `com.deelabs.untisplus`; the external `com.sapuseven.compose:protostore` library is unrelated and keeps its name. Kotlin, Jetpack Compose, Hilt, Room.

## Modules
- `:app` — the Android app. UI in `ui/pages/*`, data layer in `data/{database,repository,cache}`, use cases in `domain/`.
- `:api` — pure JVM module: Untis API client (Ktor + kotlinx.serialization). Mostly OpenAPI-generated code.
- `:material-color-utils` — vendored Kotlin port of material-color-utilities.

Toolchain: JVM toolchain pinned to 21 in all modules (bytecode target stays 17; foojay auto-provisioning enabled in `settings.gradle.kts`). The daemon JVM is pinned via `gradle/gradle-daemon-jvm.properties` (version 21, any vendor; foojay download URLs baked in as fallback). Gradle wrapper 8.13; AGP/Kotlin versions in `gradle/libs.versions.toml` (all dependencies go through this catalog).

## Commands
Fully FOSS — no Google Play Services anywhere (QR scanning uses ZXing embedded). No product flavors; build types are `debug` and `release` (debug appId suffixed `.debug`):

```sh
./gradlew assembleDebug                     # build (CI matrix: Debug/Release)
./gradlew :app:testDebugUnitTest            # unit tests (single class: --tests "com.deelabs.untisplus.helpers.TextUtilsTest")
./gradlew :api:test                         # api module tests (plain JVM)
./gradlew connectedDebugAndroidTest         # instrumented tests, needs device/emulator
```

Indentation is tabs for `.kt`/`.gradle`/`.xml` (`.editorconfig`) — match existing files.

## Generated code — do not hand-edit
- `:api`: each `api/spec/untis-intern/untis-*.yaml` gets an `openApiGenerate<Name>` task (e.g. `openApiGenerateUntisMobile`) writing Kotlin into `api/build/generated/src/main/kotlin`; `compileKotlin` depends on them automatically. To change API models/clients, edit the YAML spec or the handwritten code under `api/src/main/kotlin`, never files under `build/`. Note `OffsetDateTime` is type-mapped to the custom `com.deelabs.untisplus.api.serializer.DateTime`.
- `:app`: protobuf-lite code is generated from `app/src/main/proto/settings.proto` (persisted via protostore); extend settings by editing the `.proto`.
- Room schemas are exported to `app/schemas/<DatabaseClass>/` — commit the new schema JSON with any Room migration.

## Testing quirks
- Unit tests: JUnit 5 (Jupiter) with vintage engine in `:app`; MockK and Mockito both available. The v4-era unit tests targeting removed classes (`UntisAuth`, `UntisRequest`, `UntisDateTime`, `ErrorMessageDictionary.getErrorMessage`, ...) were deleted during the UntisPlus rename — CI never compiled them, so they had been broken upstream for a long time.
- `:api` serializer tests are environment-sensitive: empty-string fallbacks use `ZoneId.systemDefault()` (fail unless `TZ=UTC`), and `LocalDateTimeSerializerTest.serialize` fails everywhere (its formatter always emits `:00`). CI never runs `:api:test`, so these failures exist upstream.
- Instrumented tests require `HiltTestRunner` (configured as the test runner) for Hilt injection.
- Screenshot generation (`generate-screenshots.sh`, zsh): runs the `com.deelabs.untisplus.screenshots` instrumented tests on a device against the **debug** build (appId suffixed `.debug`), pulls PNGs via `adb exec-out run-as ...`; `process-screnshots.sh` / `combine-main-screenshots.sh` post-process with ImageMagick (`magick`). `android.injected.androidTest.leaveApksInstalledAfterRun=true` in `gradle.properties` is required for pulling screenshots.

## Workflow & releases
- Branch model: all development happens on `dev`; `master` holds stable code only. **PRs must target `dev`, never `master`.**
- Releases: every push to `master` auto-publishes a GitHub Release named after `versionName` from `app/build.gradle.kts` (`.github/workflows/deploy.yml`; dispatch accepts a version override). Bump `versionName` when a new release is wanted.
- Dev builds: manual only — `gh workflow run dev.yml -f build_type=debug` or `-f build_type=debug-and-release` — published to the rolling `dev` pre-release (`.github/workflows/dev.yml`). Debug app id is suffixed `.debug` so it installs alongside release builds.
- `versionCode` is regenerated per-minute at build time; only the F-Droid flow pins it.

## Gotchas
- Signing: `app/UntisPlus.jks` + `app/signing.properties` are optional; without them builds are just unsigned — don't try to fix missing-signing errors locally.
- `app/build.gradle.kts` forces `androidx.sqlite:sqlite(-ktx):2.5.1` and excludes `sentry-android-sqlite` (crash workaround, see comment there) — don't remove.
- Lint disables `MissingTranslation`: translations come from Crowdin (`crowdin.yml`, source `values/strings.xml`). Don't hand-edit `res/values-*` translations or chase missing-translation issues.
