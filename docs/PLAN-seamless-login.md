# PLAN — Seamless login (ALL PARTS IMPLEMENTED 2026-08-24)

_Parts A, B, and C are implemented, built, and verified on-device (debug build; Schuldorf Office365 SSO end-to-end confirmed). NOT yet committed. Read `docs/PROJECT-STATUS.md` first._

## User decisions (do not re-ask)

1. **UntisPlus API is the DEFAULT for new logins** — Official + Custom move into the **Advanced section of the add-profile page**. No separate provider page anymore.
2. **Scope = "Top 2 only"** → Part A (provider default / kill detour) + Part B (stale-session recovery). Parts from earlier backlog NOT in scope: recent schools, lazy anonymous probe, search-flicker fix, dead-code cleanup.
3. **QR codes / deep links must ALSO default to UntisPlus API** (explicit `apiUrl` param in payload keeps Custom semantics).
4. **NEW: SSO login** (Part C) — schools like Schuldorf use SSO instead of Untis passwords. User offers their Schuldorf account for on-device testing.

---

## PART A — Provider choice into Advanced, UntisPlus default, delete provider page

### A1. LoginData defaults
`ui/pages/login/datainput/LoginDataInputViewModel.kt`:
- `LoginData.useUntisPlusApi = mutableStateOf(false)` (L~422) → **flip to `true`**
- This automatically covers QR/deep-link path (`loadFromData` L171–192) since it doesn't touch the flag — EXCEPT: if payload has explicit `apiUrl` param, that means Custom: set `useUntisPlusApi=false`, keep `apiUrl` as custom host semantics (verify interplay with `advanced = apiUrl?.isNotEmpty()` at L186)

### A2. Advanced-section UI
`ui/pages/login/datainput/LoginDataInput.kt` (~L336–362 advanced area):
- Add provider selector inside existing Advanced section: radio/segmented `UntisPlus API (recommended)` | `Official Untis API` | `Custom endpoint…`
- Custom selected → reveal URL field (reuse validation pattern from deleted ApiProviderSelect: `User.normalizeApiEndpoint(raw)`; warn on plain http)
- Bind to `loginData.useUntisPlusApi` / new `loginData.customApiHost` state (field exists, default null)
- Existing-user edit (`loadFromUser` L426–435 already loads flags): map stored user → selection: `customApiHost!=null`→Custom+prefill origin; else `useUntisPlusApi||apiHost=="https://api-untis.deelabs.tech"`→Plus; else Official

### A3. loadData authenticates through FINAL endpoint directly
`loadData()` L194–246: currently builds `untisApiUrl` via `User.buildJsonRpcApiUrl(buildApiUrl(apiHost, schoolInfo), …)`. Change: apply same authority rewrite `resolveApiEndpoint` does (customApiHost > UNTISPLUS_API_HOST) to the jsonRpc URL before any network call. Simplest: construct the rewrite inline or expose a helper on User companion, e.g. `applyEndpoint(url: Uri, useUntisPlusApi: Boolean, customApiHost: String?): Uri`.
- Remove `userRepository.pendingApiProviderSelection = true` (L217–220); ALWAYS navigate Timetable popUpTo(0)
- preloadTimetable then runs through final endpoint — no purge needed for fresh logins

### A4. Delete provider-page flow (~400 lines net deletion)
- Delete files `ui/pages/login/apiprovider/ApiProviderSelect.kt` + `ApiProviderSelectViewModel.kt`
- `AppRoutes.kt`: remove `ApiProviderSelect` route (L31)
- `AppNavHost.kt`: remove composable registration
- `MainAppContent.kt` L86–96: drop `consumeApiProviderSelectionStart` param/start-destination logic → start destination always Timetable
- `MainViewModel.kt`: remove `consumeApiProviderSelectionStart`
- `MainActivity.kt`: remove passthrough arg
- `UserRepository.kt`: remove `pendingApiProviderSelection` + `consumePendingApiProviderSelection` (L45–55)

### A5. Edit-path cache purge (keep behavior when endpoint changes)
- Extract purge util → new small file e.g. `data/cache/ApiCacheCleaner.kt`: `fun clearApiCaches(cacheDir: File)` deleting subdirs `timetable`, `periodData`, `infocenter`, `messenger` (deleteRecursively)
- Call from `saveUser`/loadData edit path when effective endpoint changed (compare old user's resolved authority vs new). Fresh logins skip (cache empty anyway)

## PART B — Stale-session recovery

- `TimetableViewModel.kt`: `loadingExceptionHandler` (L151–154) currently LOGS ONLY. Surface errors:
  - classify auth-ish failures (UntisApiException codes: invalid credentials/-8505 family, bad secret) → exposed state e.g. `sessionError: Boolean` (+ generic `loadError: String?` for others)
  - add public `fun retryLoad()` / re-trigger onPageChange load
- `Timetable.kt`: consume state — MessageBubble pattern exists elsewhere (see LoginDataInput usage): auth error → banner text "session expired" + action navigating existing prefilled flow `AppRoutes.LoginDataInput(userId = …)` (drawer edit-profile does exactly this today, see TimetableViewModel L221–226); other errors → non-blocking "couldn't refresh — retry" bubble

## PART C — SSO login (Schuldorf test case)

### Design: WebView SSO that HARVESTS THE APP SECRET → normal profile downstream
Community-documented basis: after ANY web login (incl. IdP SSO), WebUntis serves the access data behind its own "show QR" feature at **`GET https://<server>/WebUntis/api/profile/access`** (authenticated by web session cookie, JSESSIONID). Response contains username + 16-char app shared secret — identical to QR payload contents (`untis://setschool?...&key=SECRET`). Also relevant: `/WebUntis/api/app/config` returns personId/type after session.

⚠️ UNVERIFIED against a real SSO school — MUST validate EARLY with user's Schuldorf account (logcat capture of responses) before polishing.

### Steps
1. New screen/composable reachable from LoginDataInput after school pick: button *"Mit Schulkonto anmelden (SSO)"* next to password field. Needs schoolInfo resolved (have it from search selection).
2. Embedded WebView via `AndroidView` (NO compose-webview dep; zero WebView usage exists today — add nothing beyond stock android.webkit). Load `https://<server>/WebUntis/index.html?school=<loginName>` (server from schoolInfo.serverUrl/mobileServiceUrl per buildApiUrl rules).
3. Detect success: CookieManager.getInstance().getCookie(server) contains JSESSIONID AND url left login pages (watch navigation for non-login route, e.g. `/index.html#` main app pages). IdP redirects happen inside WebView (enable JS + DOM storage).
4. With cookie header, fetch `api/profile/access` (plain Ktor call w/ manual Cookie header or WebView-injected). Parse user/key.
5. Feed into existing pipeline: set LoginData username/storedPassword-style fields so loadData() skips getAppSharedSecret and calls getUserData(user, key) directly → buildUser stores as normal `User.user`/`User.key` → EVERYTHING downstream unchanged (TOTP auth works, proxy works).
6. Fallback ONLY if profile/access blocked for SSO users: persist JSESSIONID per user + cookie-auth branch in `ApiClient.request()` (inject Cookie header from UserRepository lookup) + expiry handling. Bigger change — build only if step 4 fails live.

### Auth-surface facts (from research, verified line refs)
- All JSON-RPC auth travels IN BODY: `Auth(user, key)` secondary ctor → TOTP otp from key (`api/.../model/untis/Auth.kt` L13–66); anonymous = `"#anonymous#"`, otp 0
- `UserDataApi`: getAppSharedSecret(user,password,token), getAuthToken(user,key)→JWT (used only by messenger REST client, fresh-per-call), getUserData(user,key)
- Generated-but-unused token scaffolding exists: `api.mobile.AuthenticationApi` (`POST /{school}/authentication`→JWT, untis-mobile.yaml) + `User.restApiAuthUrl` (`/api/mobile/v2`) — dormant, no callers
- ApiClient: Ktor CIO, ContentNegotiation only, NO cookies/auth plugins (`api/.../client/ApiClient.kt`)
- Untis official OIDC platform SSO (`developer.untis.com/.../sso/`, `/WebUntis/api/sso/v3/{tenantId}/authorize|token`) is for REGISTERED platform partners (client_id in PAM) — NOT usable by us; school-level IdP SSO (Microsoft/Google buttons on school's WebUntis page) is what we target via WebView

## Verification checklist (all parts)

### Parts A+B — implemented as follows (2026-08-24)
- `LoginDataInputViewModel`: new top-level `enum ApiProvider {OFFICIAL, UNTISPLUS, CUSTOM}`; `apiProvider` state defaults **UNTISPLUS**; `resolveEndpointOrigin()` maps selection → null / plus-host / normalized custom origin; `loadData()` builds jsonRpc URL through final endpoint, purges caches via `ApiCacheCleaner` when editing and effective URL changed (`existingUser.jsonRpcApiUrl` comparison), always navigates Timetable popUpTo(0) + preload; QR/deep-link (`loadFromData`) keeps default PLUS unless payload has explicit non-empty `apiUrl` → CUSTOM + advanced open; demo login = CUSTOM w/ DEMO_API_URL; existing users derive provider from stored flags (customApiHost>useUntisPlusApi>official), advanced auto-opens when != UNTISPLUS
- `LoginDataInput.kt`: Advanced section now has 3 ProviderRows (Official/UntisPlus "(recommended)"/Custom); Custom reveals OutlinedTextField bound to `loginData.apiUrl` + invalid-url error + http warning
- DELETED: `ui/pages/login/apiprovider/` (both files), route, nav composable, `pendingApiProviderSelection` flag + all plumbing
- NEW: `data/cache/ApiCacheCleaner.kt`
- Strings removed from values/: apiprovider_title, apiprovider_continue, logindatainput_api_url (translations still carry them — Crowdin will sync)
- Part B: `TimetableViewModel` gained `sessionError`/`loadError` states; `SESSION_ERROR_CODES` = {-8504,-8502,-8520,-8521,-8525,-8526,-8511,-42000,-42002,-50001}; success clears both; actions `dismissSessionError/reloginFromSessionError (→ LoginDataInput(userId))/dismissLoadError/retryLoad`; `Timetable.kt` overlays MessageBubble banners top-center inside the ready-gated Box with Cancel/Re-login and Cancel/Retry TextButtons

### Part C — implemented as follows (2026-08-24)
- SSO entry: "Log in with school account (SSO)" TextButton under the password field in `LoginDataInput`, gated by `viewModel.ssoAvailable` (requires search-resolved schoolInfo)
- Route `AppRoutes.LoginSso(schoolInfoSerialized)` + nav (slide up/down); `ui/pages/login/sso/`: `SsoLogin.kt`, `SsoLoginViewModel.kt`, `SsoLoginResultBus.kt`
- **Provider detection**: VM fetches the school's landing HTML (`startUrl` = advertised full URL w/ school query; legacy → index.html) and regexes `"sso…LoginUrl":"…"` from bootstrap config (priority office365/o365 > google > azure > oidc > other), resolving relative against server root. No provider → `ssoNotAvailable` state + "Open web login instead" fallback button.
- WebView: stock `android.webkit`, JS+DOM storage+third-party cookies on, `onConsoleMessage` → log tag `SsoWebView`, 2s passive harvest ticker (throttle ~2.5s, runs while open), LinearProgressIndicator, SsoStatusBar
- **Harvest**: `GET {serverRoot}/api/profile/access` with `CookieManager` header; parser reads nested `data.appCredentials.{user,key}` first, then flatter `data|root` shapes. Success → close + publish via bus → LoginDataInput collector sets username/password + `skipAppSecret=true` → existing `loadData()` pipeline (Plus default, TOTP downstream). Verified E2E on Schuldorf (Office365 SSO).
- **Security cleanup left**: harvest logs full body incl. the secret at INFO (`Log.d`); demote to VERBOSE or strip key before committing.

### Still to verify interactively (user)
1. Fresh manual login (Schuldorf creds): zero extra taps, traffic via api-untis.deelabs.tech
2. Advanced selector: switch Official → hits schuldorf.webuntis.com; Custom http://IP → works + warning shows
3. QR without apiUrl → Plus API; with apiUrl → Custom
4. Edit profile switching provider → caches purged
5. Stale session (airplane mode / revoked secret) → banner visible, Re-login prefills editor
6. Part C SSO on Schuldorf — VERIFIED 2026-08-24 (Microsoft IdP → auto closes → timetable).


1. `./gradlew assembleDebug` clean; install on tablet (`adb install -r app/build/outputs/apk/debug/app-debug.apk`)
2. Fresh manual login (Schuldorf creds): lands on timetable with ZERO extra taps, traffic went through api-untis.deelabs.tech (logcat/Charles)
3. Advanced shows selector; switch to Official → login hits schuldorf.webuntis.com; Custom http://IP:PORT → works
4. QR import without apiUrl → Plus API used; with apiUrl → Custom respected
5. Edit profile switching provider → caches purged (check cacheDir), timetable refetches
6. Airplane-mode launch on stale session → visible recovery bubble/banner (not silent dead grid); Re-login routes to prefilled LoginDataInput
7. SSO: full Schuldorf IdP flow in WebView → profile created as normal → timetable loads. Capture `profile/access` response shape via logcat FIRST
