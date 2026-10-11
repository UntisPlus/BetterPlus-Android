# PROXY-API — contract for the untis-proxy endpoints the app must call

This is the **integration contract** for the endpoints the `untis-proxy` backend
adds on top of stock WebUntis. It states, for each one: how the app reaches it,
what auth it needs, the exact request body and the exact response body. It is the
input for implementing the `:api` clients; it deliberately contains no app code.

Companion reading: `docs/APP-INTEGRATION.md` (current wiring state and open app
work) and `docs/PROJECT-STATUS.md` (project context).

Backend base: `https://api-untis.deelabs.tech` (the deployed proxy).

> All of these are **proxy-implemented** (not WebUntis). They only exist when the
> user selected the **UntisPlus** / custom provider, never on stock Untis.

---

## 1. How the app reaches the proxy's own endpoints

Stock WebUntis calls go to `buildApiUrl()` = `<origin>/WebUntis/...` and the
JSON-RPC entry point `<origin>/WebUntis/jsonrpc_intern.do?school=<name>`
(`User.kt:83` / `User.kt:108`).

The proxy's **own** endpoints live at the **origin root**, *not* under
`/WebUntis`:

```
<scheme>://<authority>/api/homework/done
<scheme>://<authority>/me
<scheme>://<authority>/status
```

So the app should derive a REST base from the resolved API host — the same
`scheme` + `authority` that `resolveApiEndpoint(apiUrl)` produces (`User.kt:49`),
with the path dropped. Concretely, for a `User`:

```
restBase = apiUrl.buildUpon().path("/").build()   // e.g. https://api-untis.deelabs.tech
```

(`apiUrl` is `origin + /WebUntis`; strip the `/WebUntis` path segment. Do **not**
reuse `jsonRpcApiUrl` — its path is `/WebUntis/jsonrpc_intern.do`.)

Keep this consistent with the provider rules already in `User.kt`: when the user
is on stock Untis, none of these endpoints exist and the features must be hidden.

---

## 2. Authentication

Every endpoint in §3–§5 (except the two marked **public**) is authenticated by
the **`JSESSIONID` cookie** the proxy sets during the intern login
(`jsonrpc_intern.do` → `getUserData2017` / `keyLogin`).

- **Send the cookie**, not an `auth` block. These are plain REST endpoints:
  there is no `{"auth":{"user":…,"otp":…}}` body and passing one does nothing.
- The cookie is `HttpOnly`, `Path=/`, `SameSite=Lax`, and `Secure` when the
  request arrived over TLS. A cookie-aware Ktor `HttpClient`
  (`install(HttpCookies){ storage = ... }`) handles it transparently; a manual
  header would have to replicate the whole session.
- The proxy also sets a `schoolname` cookie at login. Calls may still pass
  `?school=<name>` explicitly; when omitted the server's default school is used.
  **Send `?school=`** — it is cheap and removes ambiguity in a multi-school
  deployment.
- There is **no username parameter** anywhere in this API. The server takes the
  identity from the session and ignores any `username` field in a body. A
  request that 401s (`{"error":"not logged in"}`) must trigger the existing
  re-login path (`sessionError` / `reloginFromSessionError`).

> This is exactly the cookie persistence gap called out in `APP-INTEGRATION.md`
> §2. None of §3–§5 works until the `:api` client keeps `JSESSIONID`.

### Errors

| Status | Body | Meaning |
|---|---|---|
| `200` | JSON | success |
| `304` | *(empty)* | change poll: nothing moved |
| `400` | `{"error":"…"}` | bad request / missing field |
| `401` | `{"error":"not logged in"}` / empty | no valid session → re-login |
| `403` | `{"error":"forbidden"}` | session valid, not allowed for this target |
| `404` | `{"error":"not found"}` | unknown id/token |
| `405` | `{"error":"method not allowed"}` | wrong HTTP method |
| `500` | `{"error":"store error"}` | backend |

Timestamps are **RFC 3339 UTC strings** (`2026-10-01T12:34:56Z`). Durations/horizons
are done in whole days. Field naming is **camelCase** for everything the proxy
defined itself — with one exception: the timetable `changes` rows are Go structs
with no JSON tags and therefore come out **PascalCase** (`PeriodID`, `Kind`, …).
Do not "fix" that casing in a client model.

---

## 3. Homework "done" flags

Untis' own `homeWorks[].completed` is the **teacher's** flag for the whole class
and must never be written. The proxy stores the **student's own** mark separately
and (a) exposes it as a small JSON list and (b) injects it into the upstream
homework payload (§6).

### 3.1 `GET /api/homework/flags`

```
GET /api/homework/flags?school=<name>
Cookie: JSESSIONID=…
```

`200`:
```json
{
  "school": "schuldorf",
  "flags": [
    { "homeworkId": 12345, "done": true,  "doneAt": "2026-10-01T12:34:56Z" },
    { "homeworkId": 12346, "done": true,  "doneAt": "2026-10-02T08:00:00Z" }
  ]
}
```

Only **done** entries appear; a cleared flag is simply absent. Sorted by
`homeworkId` (stable for diffing). `doneAt` is the moment the mark was set.

### 3.2 `POST /api/homework/done`

```
POST /api/homework/done?school=<name>
Cookie: JSESSIONID=…
Content-Type: application/json

{ "homeworkId": 12345, "done": true }    // set
{ "homeworkId": 12345, "done": false }   // clear
```

`200`:
```json
{ "homeworkId": 12345, "done": true,  "doneAt": "2026-10-01T12:34:56Z" }
{ "homeworkId": 12345, "done": false, "doneAt": null }
```

Errors: `400` if `homeworkId` ≤ 0 or `done` is missing; `401` without session.
Both directions are idempotent. A `username` in the body is **ignored** — the
write can only ever hit the session user.

### 3.3 App integration

- Map to the existing homework feature (class-register / `HomeWork` model,
  `api/.../model/untis/classreg/HomeWork.kt`). The proxy adds `done`
  (Boolean) and `doneAt` (nullable string) to each `homeWorks[]` entry in
  `getHomeWork2017` and `getPeriodData2017` responses (§6), so either read the
  injected fields from the normal homework call **or** call `GET
  /api/homework/flags` to sync just the checkboxes.
- Toggle → `POST /api/homework/done`, optimistic UI, roll back on non-2xx.

---

## 4. Absence notes

A private, per-student note keyed by the absence `id`. Read/written only for the
session user's own absences. The proxy also injects the note, plus a derived
"what/when/why" block, into the upstream absence list (§6).

### 4.1 `GET /api/absence/notes`

```
GET /api/absence/notes?school=<name>
Cookie: JSESSIONID=…
```

`200`:
```json
{
  "school": "schuldorf",
  "notes": [
    { "absenceKey": 300001, "note": "bring workbook", "updatedAt": "2026-10-01T12:34:56Z" }
  ]
}
```

Stable order by `absenceKey`.

### 4.2 `POST /api/absence/notes`

```
POST /api/absence/notes?school=<name>
Cookie: JSESSIONID=…
Content-Type: application/json

{ "absenceKey": 300001, "note": "bring workbook" }   // set
{ "absenceKey": 300001, "note": "" }                 // clear (empty/whitespace)
```

`200` (set):
```json
{ "absenceKey": 300001, "note": "bring workbook", "updatedAt": "2026-10-01T12:34:56Z" }
```
`200` (clear):
```json
{ "absenceKey": 300001, "note": null }
```

Errors: `400` `{"error":"absenceKey is required"}`, `400`
`{"error":"note is too long"}` (cap **2000** chars after trimming), `401`
without a session. A `username` in the body is ignored.

### 4.3 App integration

- Add `note: String?` / `noteUpdatedAt: String?` to the absence model
  (`api/.../model/untis/absence/StudentAbsence.kt`) — the proxy injects them into
  `getStudentAbsences2017` responses for the viewer's own entries (§6). Optional:
  the `derived` block (below) to show subject/weekday/reason.
- Keep a note editor in the absence detail screen; save via
  `POST /api/absence/notes`. Clearing sends `""`, not `null`.

---

## 5. Notifications (self-service subscriptions)

A user can register **webhook** or **ntfy** targets for their own class timetable
changes. The proxy owns delivery (durable outbox, retries, dead-letter); the app
only manages the subscription. Brokered out of the change poller, so it works
with the phone asleep.

Entitlement: the user's **own class** always; any pooled class, or school-wide
`classId 0`, only with `editor`/`boosted`/admin (`APP-INTEGRATION.md` §8). A
student app therefore only ever uses its own `classId`.

### 5.1 Webhooks

```
GET    /api/webhooks?school=<name>          → list
POST   /api/webhooks?school=<name>          → create
POST   /api/webhooks/{id}/test?school=<name>→ send one test delivery
DELETE /api/webhooks/{id}?school=<name>     → delete
Cookie: JSESSIONID=… on all
```

`GET` `200`:
```json
{ "webhooks": [ { "id": 7, "school": "schuldorf", "classId": 5000, "url": "https://example/hook" } ] }
```

`POST` body:
```json
{ "classId": 5000, "url": "https://example/hook", "secret": "optional-hmac-secret" }
```
`200` → `{ "id": 7 }`. `400` if the URL is rejected by the SSRF guard
(private/loopback host) — the body is `{"error":"<reason>"}`. `403` when the
`classId` is not the user's and they are not privileged.

`POST …/{id}/test` `200` → `{ "ok": true, "status": 200 }` (or
`{ "ok": false, "error": "…" }`).

`DELETE` `200` → `{ "deleted": true }`; `403` if the subscription is not the
caller's and they are not privileged.

When a delivery fires, the proxy POSTs the class-change payload to `url` with
these headers (the receiver side — not the app — verifies `X-Untis-Signature`):

```
Content-Type: application/json
User-Agent: untis-proxy/1.0
X-Untis-Event: timetable-change
X-Untis-Summary: <ASCII summary line>
X-Untis-Signature: sha256=<hex HMAC-SHA256(body, secret)>
```

### 5.2 ntfy

```
GET    /api/ntfy?school=<name>            → list
POST   /api/ntfy?school=<name>            → create
POST   /api/ntfy/{id}/test?school=<name>  → publish one test notification
DELETE /api/ntfy/{id}?school=<name>       → delete
Cookie: JSESSIONID=… on all
```

`GET` `200`:
```json
{ "topics": [
  { "id": 3, "school": "schuldorf", "elementType": "CLASS", "elementId": 5000,
    "classId": 5000, "topic": "my-topic", "baseUrl": "https://ntfy.sh" }
] }
```

`POST` body:
```json
{ "classId": 5000, "topic": "my-topic", "baseUrl": "https://ntfy.sh" }
```
`200` → `{ "id": 3 }`. `topic` is required (`400` otherwise); `baseUrl` is
optional and SSRF-checked. `403` as for webhooks.

`POST …/{id}/test` `200` →
`{ "ok": true, "status": 200, "topic": "my-topic", "server": "https://ntfy.sh" }`.

`DELETE` `200` → `{ "deleted": true }`.

Note: the proxy publishes a **JSON envelope** to the ntfy **server root** (`/`),
not to `/<topic>`; the `topic` field inside the envelope selects the
subscription. The app only needs to store/echo the `topic`.

### 5.3 App integration

- Settings → Notifications: "Notify me about timetable changes". A student
  creates one subscription for their `classId` (from the profile). Webhook is for
  power users (they supply the URL); ntfy is the out-of-the-box mobile push path
  (topic can be suggested = derived from user + class, server default
  `https://ntfy.sh`).
- Store the returned `id` per user so the row can be listed/tested/deleted.
- There is nothing to receive in-app for either: delivery goes to the webhook URL
  or the ntfy transport. Do not build a client-side receiver.

---

## 6. Enrichments injected into existing WebUntis responses

The proxy decorates a few upstream JSON-RPC responses **in place** — same call,
same shape, added fields. A client that ignores the new fields keeps working.

### 6.1 `getHomeWork2017` and `getPeriodData2017`

Each object in `result.homeWorks[]` gains:

```json
"done": true,
"doneAt": "2026-10-01T12:34:56Z"
```

For a non-done assignment it is `"done": false, "doneAt": null` — always present,
so a client can branch on it without a null check on the field's existence.
(`HomeWork.kt` → add `done: Boolean = false`, `doneAt: String? = null`.)

### 6.7 `getStudentAbsences2017`

Each absence entry that belongs to the session user gets, **in addition** to the
upstream fields:

```json
{
  "… upstream fields …": "…",
  "note": "bring workbook",                       // only when a note exists
  "noteUpdatedAt": "2026-10-01T12:34:56Z",
  "derived": {
    "classId": 5000,
    "className": "10b",
    "weekday": "Thursday",
    "date": "2026-10-01",
    "subject": "Mathematik",
    "reason": "Krankheit"
  }
}
```

`derived` **omits any field it cannot know** (e.g. `subject` is left out when the
absence overlaps zero or more than one lesson — it is never guessed; `reason`
needs a known reason code); the object is still present but may be empty (`{}`).
Entries that are not the viewer's own — e.g. a class-register response replayed as
a teacher — are left completely undecorated (no `derived`, no `note`).
Add `note`, `noteUpdatedAt`, `derived` as nullable fields to `StudentAbsence`.

---

## 7. Change detection (poll + SSE)

Already specified in `APP-INTEGRATION.md` §5; restated here with the exact wire
format, including the **PascalCase** row casing.

### 7.1 `GET /api/timetable/changes`

```
GET /api/timetable/changes?classId=<id>&since=<ver>&sinceEvents=<evVer>&school=<name>
Cookie: JSESSIONID=…
```

- `classId` must equal the session user's class, else `403`.
- `since` = last seen class version (default `0`).
- `sinceEvents` = last seen student-event version. **Send it** if you handle
  student events; if you never send it, `eventVersion` is excluded from the
  `304` decision (so you keep getting `304`s).

`304 Not Modified` (empty) when the class version has not moved, there are no
pending rows, and (if `sinceEvents` was sent) the event version is unchanged.

`200`:
```json
{
  "school": "schuldorf",
  "classId": 5000,
  "since": 41,
  "current": 42,
  "eventVersion": 7,
  "sinceEvents": 7,
  "summary": "1 change: Mathe relocated to R12",
  "digest": { "title": "…", "summary": "…", "lines": ["…"],
              "changed": 1, "added": 0, "removed": 0 },
  "changes": [
    { "PeriodID": 9001, "Kind": "ADDED", "Start": "2026-10-01T08:00:00",
      "End": "2026-10-01T08:45:00", "Subject": "Mathematik", "Room": "R12",
      "Teacher": "Müller", "Description": "Raumänderung", "ModVer": 4 }
  ]
}
```

`Kind` ∈ `ADDED` | `CHANGED` | `REMOVED`. **Row fields are PascalCase** (Go
struct, no JSON tags): `PeriodID, Kind, Start, End, Subject, Room, Teacher,
Description, ModVer`. `digest` is camelCase. Persist `current` **and**
`eventVersion` as the next `since` / `sinceEvents`.

### 7.2 `GET /api/timetable/stream` (SSE)

```
GET /api/timetable/stream?school=<name>
Cookie: JSESSIONID=…
Accept: text/event-stream
```

Server-sent events, `Content-Type: text/event-stream`, `Cache-Control:
no-cache`, `Access-Control-Allow-Origin: *`. On connect it sends a `snapshot`,
then pushes `change` / `student-events`, with a `:heartbeat` comment every 30 s:

```
event: snapshot
data: {"event":"snapshot","school":"schuldorf","classId":5000,"current":42,"changes":[…],"eventVersion":7}

event: change
data: {"event":"change","school":"schuldorf","classId":5000,"version":42,"changes":[…]}

event: student-events
data: {"event":"student-events","school":"schuldorf","eventVersion":7,"reason":"create"}

: heartbeat
```

The `snapshot` data carries `current` (the class version), the current `changes[]`
and `eventVersion`; the `change` data carries `version` and just the new
`changes[]` (no digest/summary — unlike the poll body); the `student-events` data
carries only the new event version and a reason — **no payload**. On
`student-events` the app just re-fetches its timetable (the events themselves are
only visible through the JSON-RPC methods the app is already authenticated for).
Requires `ktor-client-sse` (not currently a dependency).

### 7.3 App integration

- Poller: `changes?since=` on a background worker, persist version; use it as the
  cheap fallback when the SSE socket is down.
- SSE: a foreground/long-running worker bound to `currentUser`, reconnecting on
  drop, with `Last-Event-ID`-less reconnect (the `snapshot` re-syncs state).
- Map rows to `api/.../model/untis/timetable/Period.kt`; remember PascalCase.
- On any push, refresh `TimetableViewModel`.

---

## 8. Identity & health (for feature-gating and connectivity)

### 8.1 `GET /me`

```
GET /me
Cookie: JSESSIONID=…
```
`200`:
```json
{ "username": "dee", "level": "boosted",
  "permissions": { "recon": false, "boosted": true, "editor": false } }
```
`level` ∈ `basic` | `recon` | `boosted` | `boosted+editor`. `401`
`{"error":"not logged in"}` without a session → re-login. Use it after login to
gate UI: hide edit affordances unless `permissions.editor`, show a "boosted"
badge when `permissions.boosted`, etc. (detail: `APP-INTEGRATION.md` §9.)

### 8.2 `GET /status` (public)

```
GET /status
```
`200`:
```json
{ "status": "ok", "mode": "prod", "version": "…", "uptime_sec": 12345 }
```
No auth. Cheap reachability probe for the pre-flight in `DailyWorker` /
`TimetableDependantWorker` (detail: `APP-INTEGRATION.md` §3).

### 8.3 `GET /healthz` (public)

```
GET /healthz
```
`200` when healthy, `503` when a school's polling is stale:
```json
{ "status": "ok", "uptime_sec": 12345, "schools": 2, "degraded": 0 }
```
Aggregate only — no school names. Fine as a readiness probe.

---

## 9. Calendar subscriptions

Detail lives in `APP-INTEGRATION.md` §4; the contract is repeated here for one
place of record.

```
POST /api/calendar/token      (Cookie, session)
  {"classId":5000} | {"personal":true} | {"personId":5006}
  | {"teacherId":123} | {"roomId":456} | {"subjectId":789}
  optional: {"timezone":"Europe/Berlin"} {"days":30}
→ { "type","id","name","token","url","timezone","days",
    "horizon","created","lastUsed","createdBy",
    "classId" (class tokens only), "personId" (personal tokens only) }

GET /api/calendar/{token}.ics    (public — token is the credential)
GET /week/{token}                (public — JS-free week page)
```

Idempotent per target. `url` is always `https`. Store it on the `User` and expose
it in Settings → Timetable (copy/share/open). The app does **not** need to parse
the `.ics`.

---

## 10. What is explicitly **out of scope** for the app

These are admin/staff surfaces on the same server. The student app must not call
them; they need `users.admin` and would 401/403 for a normal student:

- `/admin/*` — dashboard + JSON API (users, perms, pool, tokens, schools,
  subscriptions, recon, rooms).
- `/api/admin/events…` — student-event authoring and the event-absence register.
- `/api/admin/events/import` — bulk import.
- `/api/webhooks` / `/api/ntfy` **school-wide** (`classId 0`) or other-class
  targets — privileged only.

The one admin-authored thing a student does see is a **student event** on their
own timetable; it arrives through the normal timetable calls (and via
`/week`/`.ics` for personal feeds), not through `/api/admin/events`.

---

## 11. Implementation checklist (for the `:api` module)

1. **Cookie persistence** (unblocked all of §3–§9): ✅ done. A shared
   `ProxyCookieStorage` is installed on the intern-JSON-RPC and REST clients via
   `HttpClientConfig.installCookieStorage(...)`, so `JSESSIONID` from login rides
   on every later call. No new artifact: on Ktor 3.x `HttpCookies` /
   `AcceptAllCookiesStorage` live in `ktor-client-core` (only Ktor 2.x had a
   separate `ktor-client-http-cookies`). See `APP-INTEGRATION.md` §2.
2. **REST base derivation**: origin of `apiUrl` (§1), not `jsonRpcApiUrl`.
3. New clients + models (suggest `:api` package split):
   - `HomeworkFlagsApi` → `HomeworkFlagsResult`, `HomeworkFlag`
   - `AbsenceNotesApi` → `AbsenceNote` (GET list item, POST echo)
   - `SubscriptionsApi` → webhook + ntfy models
   - `TimetableChangesApi` → `ChangesResult`, `PeriodRow`, `ChangeDigest`
   - `ProxyStatusApi` → `/status`, `/me`, `/healthz`
   - `CalendarApi` → token request/response
4. Register them in `JsonRpcApiClientModule` / `RestApiClientModule`
   (`app/src/main/java/com/deelabs/untisplus/modules/`).
5. Gate every call and every UI affordance on the provider being UntisPlus /
   custom (`user.useUntisPlusApi` / `customApiHost`), never on stock Untis.
6. Map 401 → re-login, 403 → hide the affordance, 304 → no-op.
