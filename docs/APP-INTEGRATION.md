# APP-INTEGRATION — finishing BetterPlus-Android against the untis-proxy backend

This documents what remains to wire the app **fully** against the Go
`untis-proxy` backend (the server now deployed at `https://api-untis.deelabs.tech`
on a NAS). It is grounded in both codebases — the app at
`~/Projects/BetterPlus-Android` and the backend at `~/Projects/untis-api`.

> Status legend: ✅ done · ⚠️ needs a fix · 🔲 TODO

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

## 4. Calendar subscription (personal `.ics`) ⚠️ + 🔲

Backend contract (verified in `calendar.go`):

```
POST /api/calendar/token            body: {"personal":true}  (or {"classId": N})
        → {school, classId|personId, token, url, created, lastUsed}
GET  /api/calendar/{token}.ics      public (token is the password)
```

- ⚠️ **Backend README is stale** — it shows `{"class": true}`, but the code
  requires `classId` (or `personal`). Fix `untis-api/README.md`.
- 🔲 App: add a `CalendarTokenApi` (POST with the persisted cookie), store the
  returned `url` on the `User`, and expose it in **Settings → Timetable**
  (copy / share / open). Recommend out-of-app subscription (Outlook/Google)
  instead of in-app ICS rendering.
- Note: **personal** tokens are bound to the session user's own student
  timetable (`STUDENT` endpoint) — that's the per-student feed.

**Accept**: user can grab their personal `...ics` URL from Settings and
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

## 8. God-Api permission tiers (new)

The backend now has a tiered permission model. The app should be aware of these
for future feature flags / UI gates:

| Level | User has | What they see |
|---|---|---|
| 1 (default) | no perms | Class pool + own stock timetable + info-center |
| 2/3 | `ROOM`/`TEACHER`/`SUBJECT` | + **reconstructed** room/teacher/subject timetables (per type granted) |
| 4 | non-student, no perms | **Stock WebUntis passthrough only** (logged, saved, no pool) |
| 5 | `god-api` | **Full raw superuser**: all teacher/room/class/student timetables + masterData served **raw from pooled teacher accounts** (no reconstruction). Cannot hold recon perms. Non-student donates to pool. |
| 6 | `god-api-editor` | + **absence-checking + lesson/subject writes** (blocked for all other tiers). |

**Absence/write methods** (blocked unless `god-api-editor`):
- JSON-RPC: `getStudentAbsences2017`, `getPersonAbsence`, `getOwnAbsence`, `getTimetableWithAbsences`, plus any method prefixed `set*`, `add*`, `update*`, `delete*`, `change*`.
- REST: `POST/PUT/DELETE` to `/absences/...` or timetable mutation endpoints.

The app's existing `displayAllowed` handling (section 6) works unchanged for
levels 2/3. Levels 5/6 are new server-side paths — the app doesn't need changes
unless you want to expose god features in the UI.

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
