# APP-INTEGRATION — finishing BetterPlus-Android against the untis-proxy backend

This documents what remains to wire the app **fully** against the Go
`untis-proxy` backend (the server now deployed at `https://api-untis.deelabs.tech`
on a NAS). It is grounded in both codebases — the app at
`~/Projects/BetterPlus-Android` and the backend at `~/Projects/UntisPlus-Proxy`.

> Status legend: ✅ done · ⚠️ needs a fix · 🔲 TODO

> **Endpoint contract:** the exact request/response bodies, auth and wire formats
> for every proxy endpoint (homework flags, absence notes, ntfy/webhook
> subscriptions, change poll/SSE, `/me`, `/status`, calendar) are in
> [`PROXY-API.md`](PROXY-API.md). Use it as the source of truth when implementing
> the `:api` clients; this file tracks the app-side work and status.

---

## 1. Hostname reconciliation ✅

The app already targets `api-untis.deelabs.tech`. The `UntisPlus` provider
resolves its API host to `User.UNTISPLUS_API_HOST`; the `CUSTOM` provider uses the
user-entered host.

- App host constant: `app/src/main/java/com/deelabs/untisplus/data/database/entities/User.kt`
  (`UNTISPLUS_API_HOST` L67, `resolveApiEndpoint`, `buildApiUrl`).
- `proxyBaseUrl` (origin, no `/WebUntis`, no trailing slash) and
  `usesUntisPlusProxy` were added on `User` as the base for the proxy's own
  endpoints.

**Accept**: logged-in user's timetable renders from the NAS host. (Done — no
`api-beta` reference remains anywhere.)

---

## 2. Session cookies persist ✅

Every new endpoint (`POST /api/calendar/token`, `GET /api/timetable/changes`,
`GET /api/timetable/stream`) authenticates with the **`JSESSIONID`** cookie the
proxy sets during `getUserData2017` login. That cookie is now captured and
replayed.

- `:api` has a shared, clearable in-memory store:
  `ProxyCookieStorage` (`api/.../api/client/ProxyCookieStorage.kt`) and an
  `HttpClientConfig.installCookieStorage(storage)` helper.
- DI wires one store into every proxy-facing Ktor client
  (`JsonRpcApiClientModule` — including `UserDataApi`, which performs the login —
  and `RestApiClientModule`), so the login cookie rides on later `/api/…` calls.
- Both login paths funnel through Ktor `getUserData` (password at
  `LoginDataInputViewModel.kt:284`; SSO publishes credentials then re-enters the
  same `loadData`), so the session is captured either way.
- `GET /status`/`/healthz`/`/me` are reachable via `ProxyStatusApi`;
  `ProxyRepository` maps a `401` → silent re-login from the stored app secret
  (retry once), else surfaces the existing stale-session path.
- The store is cleared on real user switches and on user deletion
  (`UserRepository`), so a session never leaks across accounts. Proxy session TTL
  is 24h; the silent re-login covers expiry.

> Note: no new dependency is needed — `HttpCookies`/`AcceptAllCookiesStorage` ship
> in `ktor-client-core` on Ktor 3.x (the separate `ktor-client-http-cookies`
> artifact is a 2.x thing).

**Accept**: after a successful login, the same session hits the `/api/…`
endpoints (not 401). (Done — unit-tested in `ProxyCookieStorageTest`.)

---

## 3. `/status` health probe ✅

Backend `GET /status` is **public** (no auth) and returns
`{status, mode, version, uptime_sec}` (always `"status":"ok"` while the process
is up — it is a liveness probe, not a degraded-mode signal; that is `/healthz`).

Client: `ProxyStatusApi.status()` / `.health()` (`:api`), callable via
`ProxyRepository.status()`. `ProxyRepository.isReachable(user)` is the cheap
pre-flight (per-user, no session), wired into **change detection**
(`TimetableChangeWorker`, `TimetableChangeService`) so a poll/stream attempt is
skipped cheaply while the proxy is unreachable.

It is deliberately **not** wired into `DailyWorker`'s timetable load: that load
goes straight to WebUntis (intern), not through the proxy, so gating it on proxy
reachability would drop a working load when only the proxy is down.

**Accept**: app can show/decide server reachability cheaply. ✅ (Settings → About
diagnostics + background pre-flight.)

---

## 4. Calendar subscription (`.ics`) ✅

Backend contract (verified in `calendar.go`):

```
POST   /api/calendar/token
  body: {"classId": N}           class timetable (pool members)
        {"classId": 2000000000}   synthetic Technik aggregate (techniker only)
        {"personal": true}        personal timetable (self)
        {"personal": true, "personId": N}  personal timetable for user N
        {"teacherId": N}          teacher timetable (requires recon or boosted)
        {"roomId": N}             room timetable (requires recon or boosted)
        {"subjectId": N}          subject timetable (requires recon or boosted)
        {"timezone": "Europe/Berlin"}  optional (default Europe/Berlin)
        {"days": 30}              optional lookahead window (clamped 1..365)
        → {type, id, name, token, url, timezone, days, horizon,
           created, lastUsed, createdBy, classId?, personId?}

GET    /api/calendar/options      session — subscribable targets
       → {school, personal?, class[], teacher[], room[], subject[], technik?}
GET    /api/calendar/tokens       session — the caller's own tokens (list)
PATCH  /api/calendar/{token}      session, owner — {"days":90}
DELETE /api/calendar/{token}      session, owner
GET    /api/calendar/{token}.ics  public (token is the password)
GET    /week/{token}              public JS-free week page
```

- ✅ Backend supports all element types (class/personal/teacher/room/subject)
  plus the synthetic Technik class and the lookahead window.
- ✅ App: `ProxyCalendarApi` (`options`/`tokens`/`updateDays`/`revoke` with the
  persisted cookie) behind **Settings → Timetable → Calendar subscriptions**.
  A picker sourced from `GET /api/calendar/options` (personal, classes,
  teachers, rooms, subjects, Technik) mints a link; the account's own
  subscriptions are listed with copy / share / open, a lookahead-window editor
  (PATCH), and revoke (DELETE). Out-of-app subscription (Outlook/Google) is
  recommended instead of in-app ICS rendering.
- Tokens are **not** stored locally: the list always comes from
  `GET /api/calendar/tokens`, so it stays correct across devices. A non-owner
  token `404`s server-side, so the client only needs the session cookie.
- Target visibility mirrors the app's master-data visibility: pooled classes,
  recon elements (teacher/room/subject), everything for `boosted`/staff, and the
  synthetic Technik class only for a `techniker`. Verified live: `personal` →
  `200` with a valid `text/calendar` feed; an unknown `classId` → `403`.
  Settings → About also exposes a **proxy diagnostics** menu (`/status` + `/me`).

**Accept**: user can pick a timetable they can see, grab its `...ics` URL from
Settings, subscribe from Outlook, change the window, and revoke it.

---

## 5. Timetable change-detection + notifications ✅

Backend contract (`notify.go`, poll `-poll-interval` 60s, window `-1..+4d`):

```
GET /api/timetable/changes?since=<ver>&classId=<id>&school=<s>   → 304 if none,
        else {school, classId, since, current, changes:[PeriodRow]}
GET /api/timetable/stream?school=<s>   SSE (the server derives the class from the
        session); frames: snapshot | change | student-events, heartbeat 30s
PeriodRow = {periodId, kind: ADDED|CHANGED|REMOVED, start, end, subject, room, description, modVer}
```

App work:
- ✅ `ProxyTimetableApi` poller: `changes?since=`, current version persisted per
  user in `settings.proto` (`timetableChangeSince` / `timetableChangeSinceEvents`)
  so it resumes across restarts. The poll needs a `classId`, which students lack
  (`klassenIds` empty); for them only the SSE path applies, so the worker
  early-returns.
- ✅ `TimetableChangeWorker` (WorkManager, 15 min, network-constrained) posts a
  notification when changes arrive; toggled in Settings → Notifications. A
  `ProxyRepository.isReachable()` pre-flight skips the poll while the proxy is
  down.
- ✅ Live path: `TimetableChangeService` — a `dataSync` foreground service that
  keeps `ProxyTimetableApi.stream()` open, notifies within seconds, and bumps
  `TimetableChangeBus` (observed by `TimetableViewModel`, which reloads the
  visible week). Started by the Settings toggle and re-armed in
  `MainActivity.onCreate` (a FGS cannot be started from the background on
  Android 12+). On Android 15 `dataSync` is bounded by the OS' 6 h/day budget;
  the worker remains the backstop. The stream is server-class-scoped, so it works
  without an app-side `classId`.
- ✅ Change rows are shown as a **digest** (subject · room, `+`/`~`/`–` per kind)
  in the notification. A full `ProxyTimetableChangeRow` → WebUntis `Period`
  mapping is **not derivable** (the wire row carries just the changed fields —
  not colors/elements/rights), so none is attempted.
- ✅ Self-service delivery targets (`docs/PROXY-API.md` §5): Settings →
  Notifications → *Change subscriptions* manages the caller's own webhooks
  (`/api/webhooks`) and ntfy topics (`/api/ntfy`). Targets come from
  `GET /api/notifications/options` (the calendar catalog minus the synthetic
  Technik aggregate); the shared two-step picker is reused from the calendar
  screen. Lists return only the caller's rows; each can be tested
  (`POST …/{id}/test`) and deleted (`DELETE …/{id}`). Technik is excluded because
  the change poller cannot deliver it.

**Accept**: app gets a change notification within seconds while running (≤15 min
backstop otherwise). ✅

---

## 6. Reconstruction perms are already wired client-side ✅

The app already honors `masterData.displayAllowed`/`displayable`
(`app/.../data/repository/MasterDataRepository.kt` L144-154). The proxy sets
these flags from the user's effective access, so:
- class pool → everyone sees all classes,
- teacher/room/subject → only for users granted the type (Evadee has
  ROOM + TEACHER today).

No app change needed here.

---

## 7. Version / deploy drift to reconcile ⚠️

- `untis-api/compose.yaml` still stamps `UNTIS_VERSION: v1.1.0`; NAS uses
  `v1.2.0`. Align.
- `untis-api/compose.yaml` still hardcodes `schuldorf`/`schuldorf.webuntis.com`;
  NAS compose drops it (auto-resolve). Fine, just be consistent for the docs.
  (Backend repo is at `~/Projects/UntisPlus-Proxy`; the older `untis-api` path no
  longer exists.)

---

## 8. Permission tiers (backend)

The backend has a tiered permission model. The app can query the user's effective
tier via `GET /me` (see section 9). Tiers:

| Level | Flags | What the user gets |
|---|---|---|
| **Basic** (default) | *(none)* | Class pool + own stock student timetable + **own absence reads** |
| **Reconstruction** | `recon` (global or per-user) | + teacher/room/subject timetables **reconstructed** from pooled class data |
| **Boosted** | `boosted` (per-user only) | + **raw teacher-account forwarding** for ALL class/teacher/room/subject timetables **and own personal student timetable** (teacher-grade future horizon). **No editing.** Falls back to Basic if no teacher account saved. |
| **Editor** | `editor` (per-user only) | + **absence + lesson/subject write (editing) methods** (`set*`, `add*`, `update*`, `delete*`, `change*`). Independent — pair with `boosted` for full experience. |

Rules:
- **Boosted XOR Recon** — mutually exclusive per user (granting one revokes the other).
- `editor` is independent and never triggers the XOR.
- Non-student accounts without `boosted` are **stock WebUntis passthrough** for all timetables; their masterData keeps upstream flags untouched.
- **Absence reads (own data) are always allowed** — no gate.

---

## 9. `GET /me` — current user's tier ⚠️

Endpoint: `GET {base}/me` (requires `JSESSIONID` cookie from login).

Client done: `ProxyStatusApi.me()` → `ProxyMe` (`:api`), reachable via
`ProxyRepository.refreshMe()`, which records the tier in
`ProxyRepository.permissions` (refreshed when Settings opens).

**No editor-tier gating is applied, deliberately.** The proxy gates forwarded
WebUntis *write* methods (`rest.go`/`jsonrpc*.go` → `isEditor`), but the app's
only write UI (`AbsenceCheck`, the class register) talks to WebUntis intern
**directly**, not through the proxy, and the proxy's `/api/absence/notes` store
is the student's own, session-scoped and *not* editor-gated. So there is no
proxy write surface to hide; `boosted`/`recon` need no badge either (their effect
is already visible as reconstructed timetables via §6).

**Response**:
```json
{
  "username": "evadee",
  "level": "boosted",
  "permissions": { "recon": false, "boosted": true, "editor": false }
}
```

**`level` values**: `basic` | `recon` | `boosted` | `editor` | `boosted+editor`

**Use in app**:
- Call `/me` once after login (or cache briefly) to know the tier.
- Hide edit buttons (absence, lesson-topic) when `editor` is `false`.
- Show "Boosted" badge / feature when `boosted` is `true`.
- Could gate UI for reconstructed timetables on `recon` / `boosted`.

**Auth**: needs the persisted `JSESSIONID` + `schoolname` cookies (see section 2).  
**Failure**: 401 `{"error":"not logged in"}` — trigger re-login flow.

**Accept**: app reads tier on login, caches it, and correctly hides/shows edit affordances.

---

## Suggested build order

1. ✅ Reconcile hostname → `api-untis.deelabs.tech` (app).
2. ✅ Add cookie persistence (`:api` client + DI + `ProxyRepository` silent
   refresh) — unlocks #3/#4/#5.
3. ✅ `/status` probe wired as a per-user reachability pre-flight in change
   detection (not in `DailyWorker` — its load is direct-to-WebUntis).
4. ✅ Calendar subscription API + Settings → Timetable management (options
   picker, own-token list, copy/share/open, lookahead window, revoke).
5. ✅ Change-detection: polling backstop + SSE `dataSync` foreground service,
   persisted version shared by both, in-app refresh via `TimetableChangeBus`.

Each step ends with its **Accept** criteria above being greener in the app on a
real device against `api-untis.deelabs.tech`.
