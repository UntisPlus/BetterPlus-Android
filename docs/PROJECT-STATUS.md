# PROJECT STATUS — UntisPlus (BetterPlus-Android)

_Last updated: 2026-08-23. Written as a compaction-safe handoff; read together with `docs/PLAN-seamless-login.md`._

## What this project is

UntisPlus (`com.deelabs.untisplus`): hard fork of BetterUntis, Android client for Untis timetables.
Kotlin + Jetpack Compose + Hilt + Room. Modules: `:app`, `:api` (pure JVM Ktor client, mostly OpenAPI-generated), `:material-color-utils`. See `AGENTS.md` for build commands/toolchain.

**Owner's proxy**: user runs their own Untis API proxy at `https://api-untis.deelabs.tech` (const `User.UNTISPLUS_API_HOST`). It serves `<host>/WebUntis/jsonrpc_intern.do?school=<name>`. User's school: **Schuldorf** (`schuldorf.webuntis.com`) — has **SSO login**, user offered their account for testing.

## Completed work (all merged to master unless noted)

### Login-flow overhaul (v1.0.0)
- Welcome screen = `ui/pages/login/Login.kt` (was skipping straight to LoginDataInput)
- API provider page after fresh logins: `ui/pages/login/apiprovider/ApiProviderSelect(.kt|ViewModel.kt)` — Official/UntisPlus/Custom; non-official choice purges JSON caches (`timetable`, `periodData`, `infocenter/*`, `messenger/*` under `@Named("cacheDir")` = `context.cacheDir`) and force-relogins via cached creds (`UserDataApi.getUserData(url, user.user, user.key)`); refreshes masterData in DB
- Provider choice persisted INTO profile: `apiHost` baked to full origin (`https://api-untis.deelabs.tech` or custom origin) + flags kept (`useUntisPlusApi`, `customApiHost`). Edit-profile → "Untis API path" field shows what's in use
- `User.buildApiUrl()` preserves scheme+port of full URLs BUT always appends `/WebUntis` path (dropping it caused nginx 405 on `/jsonrpc_intern.do`)
- Fresh-login nav-host swap lands on provider page via transient flag: `UserRepository.pendingApiProviderSelection` + `consumePendingApiProviderSelection()`, plumbed MainViewModel→MainActivity→MainAppContent start destination
- DB v13 (auto-migration 12→13, schema JSON committed): `user.useUntisPlusApi`, `user.customApiHost`

### Versioning / releases
- `versionNameValue = "v1.0.0"` single source in `app/build.gradle.kts`; `deriveVersionCode(name)`: M*1e6+m*1e4+p*100 (+betaN 1–98, stable ends 99). v1.0.0 = **1000099**
- deploy.yml greps `versionNameValue = "\K[^"]+` (NOT `versionName =`) — was fixed after breaking CI once; has empty-check guard now
- Release assets: versioned `UntisPlus-<ver>.apk` + **stable `UntisPlus.apk`** → permanent URL `https://github.com/UntisPlus/BetterPlus-Android/releases/latest/download/UntisPlus.apk`
- CI also builds AAB (`bundleRelease`) uploaded as workflow artifact `UntisPlus-<ver>-aab`
- deploy.yml fingerprint-append step: rewritten file-based (old inline multiline had column-0 `---` which TERMINATES the YAML document — bash got truncated script → EOF error)
- Old BetterUntis releases + tags deleted; only tag/release `v1.0.0` remains; "Dev Build" workflow deleted earlier (dev snapshots built locally)

### Distribution state
- **Obtainium**: works via GitHub releases; README has obtainium:// button
- **Google Play**: ABANDONED for now — user is under 18; identity verification wall. $25 fee refund requested via support ticket (form submitted, awaiting ~3–5 days). Revisit at 18 or via parent-owned account (parent registers, user operates). Full console walkthrough knowledge: closed-testing 12 testers/14 days requirement for new personal accounts, Data Safety answers per PRIVACY.md (repo root), target audience must be declared 13+/not-for-children, account deletion N/A (accounts live at school's Untis)
- **Privacy policy**: `PRIVACY.md` at repo root (URL for stores: github blob link)
- Trademark note: "Untis" is Untis GmbH's mark; listing says unofficial client — keep disclaimer prominent

### Devices
- User's tablet: testing device, debug build installed (`com.deelabs.untisplus.debug`, appId suffix `.debug`), wiped repeatedly during tests
- Family member's Pixel 9a (`58301JEBF14766`): RELEASE v1.0.0 installed via adb, launched, working

## Known quirks / gotchas (beyond AGENTS.md)

- `adb devices` sometimes empty until phone re-plugged/authed; use `/home/evan/Android/Sdk/platform-tools/adb`
- `gh` CLI prints mise noise line on every call (`mise ~/.config/mise/config.toml tools: gh@...`) — pollutes stdout/command substitution; filter or use `--jq` carefully. Always set `GH_REPO=UntisPlus/BetterPlus-Android` (default repo resolution once hit SapuSeven upstream!)
- Tablet loses connection when unplugged — can't verify installs remotely
- Room schema change ⇒ fold ALL new columns into same migration bump; folding extra columns into an ALREADY-shipped version number changes identity hash at same version → crash on update; fix = `adb shell pm clear <pkg>` (wipes data)
- Anonymous-support probe (`LoginDataInputViewModel.checkAnonymousSupport`) fires full getUserData(null,"") per school selection; result only moves the Anonymous switch placement
- Timetable preload at fresh login: fire-and-forget, current week, CACHED_THEN_LOAD
