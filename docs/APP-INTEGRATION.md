# APP-INTEGRATION — finishing BetterPlus-Android against the untis-proxy backend

This documents what remains to wire the app **fully** against the Go
`untis-proxy` backend (the server now deployed at `https://api-untis.deelabs.tech`
on a NAS). It is grounded in both codebases — the app at
`~/Projects/BetterPlus-Android` and the backend at `~/Projects/untis-api`.

> Status legend: ✅ done · ⚠️ needs a fix · 🔲 TODO

> **Endpoint contract:** the exact request/response bodies, auth and wire formats
> for every proxy endpoint (homework flags, absence notes, ntfy/webhook
> subscriptions, change poll/SSE, `/me`, `/status`, calendar) are in
> [`PROXY-API.md`](PROXY-API.md). Use it as the source of truth when implementing
> the `:api` clients; this file tracks the app-side work and status.

---

## 1. Critical: hostname reconciliation ⚠️

The **backend** is deployed at `api-untis.deelabs.tech`. The **app** currently
points at `api-beta.deelabs.tech` (repo `dev` branch history / README). Before
real devices can talk to the NAS, the app's default server must be
`api-untis.deelabs.tech`.

- App host constant: `app/src/main/java/com/deelabs/untisplus/data/database/entities/User.kt`
  (`apiHost` / `buildApiUrl`, L83-134).
- Verify with: app `Settings → Server` and the connectivity flags.

**Accept**: logged-in user's timetable renders from the NAS host.

---

## 2. Critical: session cookies are not persisted 🔲 (blocks everything new)

Every new endpoint (`POST /api/calendar/token`, `GET /api/timetable/changes`,
`GET /api/timetable/stream`) authenticates with the **`JSESSIONID`** cookie the
proxy sets during `getUserData2017` login.

- `:api` Ktor client is **stateless — no cookie jar**:
  `api/src/main/kotlin/com/deelabs/untisplus/api/client/ApiClient.kt`.
- The only cookie code today lives in the login WebView
  (`app/.../ui/pages/login/sso/SsoLoginViewModel.kt`) and is never reused.

Add `ktor-client-http-cookies` (or an OkHttp cookie jar / a shared cookie store)
to the DI client modules so `JSESSIONID` + `schoolname` persist after login and
are sent on the `/api/*` calls.

- Wiring: `app/src/main/java/com/deelabs/untisplus/modules/JsonRpcApiClientModule.kt`
  (`RestApiClientModule.kt` for REST).
- Note proxy session TTL is 24h; reuse the app's existing stale-session
  re-login path (`sessionError` / `reloginFromSessionError`) to re-auth.

**Accept**: after a successful login, the same session can hit the `/api/*`
endpoints (not 401).

---

## 3. `/status` health probe 🔲

Backend `GET /status` is **public** (no auth) and returns
`{status, mode, version, uptime_sec}`.

Add a small `:api` model + call and use it as a connectivity pre-flight (e.g. in
`DailyWorker` / `TimetableDependantWorker`) to decide "server reachable" rather
than probing a real login.

**Accept**: app can show/decide server reachability cheaply.

---

## 4. Calendar subscription (`.ics`) ✅

Backend contract (verified in `calendar.go`):

```
POST /api/calendar/token
  body: {"classId": N}           class timetable (pool members)
        {"personal": true}        personal student timetable (self)
        {"personal": true, "personId": N}  personal timetable for user N
        {"teacherId": N}          teacher timetable (requires recon or boosted)
        {"roomId": N}             room timetable (requires recon or boosted)
        {"subjectId": N}          subject timetable (requires recon or boosted)
        {"timezone": "Europe/Berlin"}  optional (default Europe/Berlin)
        → {type, id, name, token, url, timezone, created, lastUsed}

GET  /api/calendar/{token}.ics   public (token is the password)
GET  /me                          current user + {level, permissions}
```

- ✅ Backend supports all element types (class/personal/teacher/room/subject).
- 🔲 App: add a `CalendarTokenApi` (POST with the persisted cookie), store the
  returned `url` on the `User`, and expose it in **Settings → Timetable**
  (copy / share / open). Recommend out-of-app subscription (Outlook/Google)
  instead of in-app ICS rendering.

**Accept**: user can grab their `...ics` URL from Settings and
subscribe from Outlook.

---

## 5. Timetable change-detection + notifications 🔲

Backend contract (`notify.go`, poll `-poll-interval` 60s, window `-1..+4d`):

```
GET /api/timetable/changes?since=<ver>&classId=<id>&school=<s>   → 304 if none,
        else {school, classId, since, current, changes:[PeriodRow]}
GET /api/timetable/stream   SSE: event:snapshot (on connect), then event:change,
        :heartbeat every 30s
PeriodRow = {periodId, kind: ADDED|CHANGED|REMOVED, start, end, subject, room, description, modVer}
```

App work:
- Dependency: `ktor-client-sse` (not currently included).
- `TimetableChangesApi` poller: call `changes?since=`, keep the current version
  persisted (DataStore/protostore) so it can resume.
- SSE client: likely a **foreground service** / long-running worker bound to
  `UserRepository.currentUser`, so background "timetable updated" notifications
  work (iOS-style). Wire a refresh callback into `TimetableViewModel`.
- Map `PeriodRow` to/from the app's existing `api/.../model/untis/Period.kt`.
- Note the proxy only returns changes for the **requester's own class**
  (403 otherwise) — the app should pass its own `classId`.

**Accept**: app gets a push/notification within ~60s (one poll interval) of a
timetable change, with the changed periods.

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

## 9. `GET /me` — current user's tier ✅

Endpoint: `GET {base}/me` (requires `JSESSIONID` cookie from login).

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

1. Reconcile hostname → `api-untis.deelabs.tech` (app).
2. Add cookie persistence (`:api` client + DI) — unlocks #3/#4/#5.
3. `/status` probe model + connectivity pre-flight.
4. Calendar token API + Settings UI (incl. fixing backend README `classId`).
5. Change-detection: poller + persisted version, then SSE foreground service
   + notification + `TimetableViewModel` refresh.

Each step ends with its **Accept** criteria above being greener in the app on a
real device against `api-untis.deelabs.tech`.
