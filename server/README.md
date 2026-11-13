# Fitness Hub server

The backend of Fitness Hub: a [Ktor](https://ktor.io) 3 server (Netty) with an H2 database.
It runs on your computer, not on the phone. The app works without it; the server adds an
account-based copy of training days and workouts, weekly stats and, later, the AI coach.

## Run it

You need JDK 21. Android Studio's own JDK works:

```bash
# Git Bash (Windows); on macOS/Linux point JAVA_HOME at any JDK 21
export JAVA_HOME="C:/Program Files/Android/openjdk/jdk-21.0.8"
./gradlew :server:run
```

On Windows `cmd` or PowerShell, use `gradlew.bat :server:run`.

The server is ready when it prints `Responding at http://127.0.0.1:8080`. Check it:

```bash
curl http://localhost:8080/health
# {"status":"ok","version":"0.1.0","database":"ok"}
```

Stop it with **Ctrl+C**. If it was started from the IDE and port 8080 stays busy after you stop it,
end the leftover `java` process (PowerShell):

```powershell
Stop-Process -Id (Get-NetTCPConnection -LocalPort 8080 -State Listen).OwningProcess
```

### Settings

| Environment variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | Port to listen on |
| `DB_URL` | `jdbc:h2:file:./db/fitnesshub` | H2 database; the default file is `server/db/fitnesshub.mv.db` |

The database file is ignored by git. Delete the `server/db/` folder to start again with an empty database.
Tables are created automatically on start-up.

## Reach it from the app

The server listens on all network interfaces. How the app reaches it depends on the device:

| Device | Server URL in the app | Extra step |
|---|---|---|
| Android emulator | `http://10.0.2.2:8080` | none: `10.0.2.2` is the emulator's name for your computer |
| Phone over USB | `http://localhost:8080` | `adb reverse tcp:8080 tcp:8080` (again after every reconnect) |
| Phone on the same Wi-Fi | `http://<your PC's IP>:8080` | allow port 8080 in the PC's firewall |

USB with `adb reverse` is the simplest for a demo: it needs no Wi-Fi or firewall change, and the phone
connects to its own `localhost`, which adb forwards to the computer. Undo it with
`adb reverse --remove tcp:8080`.

The server address is not hard-coded in the app: it comes from `local.properties` (step C6).

## API

All bodies are JSON. Every `/api` route belongs to the signed-in user and only ever sees that user's data.

> **Temporary (until step C4):** the user is named in the `X-User-Id` header, without any proof.
> C4 replaces it with a verified Firebase login token (`Authorization: Bearer …`).

| Method | Path | Does | Success |
|---|---|---|---|
| GET | `/health` | Server and database status (no sign-in) | 200, or 503 if the database is down |
| GET | `/api/profile` | Your profile (empty if never saved) | 200 |
| PUT | `/api/profile` | Save the whole profile | 200 |
| GET | `/api/templates` | Your training days with their exercises | 200 |
| POST | `/api/templates` | Create a training day | 201 |
| GET | `/api/templates/{id}` | One training day | 200 |
| PUT | `/api/templates/{id}` | Replace name, days and exercises | 200 |
| DELETE | `/api/templates/{id}` | Delete it | 204 |
| GET | `/api/workouts?since=<epoch ms>` | Your workouts (optionally from a time on), oldest first | 200 |
| POST | `/api/workouts` | Upload a finished workout; the same `clientId` again returns the stored one | 201 new, 200 existing |
| DELETE | `/api/workouts/{id}` | Delete it | 204 |
| GET | `/api/stats/weekly?weeks=4&tz=Europe/Bucharest` | Per week: workouts vs goal, volume per muscle, new records | 200 |

Try it with curl:

```bash
curl -X PUT http://localhost:8080/api/profile \
  -H "X-User-Id: demo" -H "Content-Type: application/json" \
  -d '{"displayName":"Demo","weeklyGoal":3}'

curl "http://localhost:8080/api/stats/weekly?weeks=2&tz=Europe/Bucharest" -H "X-User-Id: demo"
```

### Errors

Every error has the same shape; `error` is a fixed code the app can check:

```json
{"error": "validation_failed", "message": "The request breaks 1 rule(s)",
 "details": ["exercises[0].targetSets must be between 1 and 10"]}
```

| Status | `error` | When |
|---|---|---|
| 400 | `bad_request` | Malformed JSON, missing field, bad parameter |
| 400 | `validation_failed` | Valid JSON that breaks a rule; `details` lists every problem |
| 401 | `unauthorized` | Not signed in |
| 404 | `not_found` | No such route, or no such item **for this user** (other users' ids look missing too) |
| 405 | `method_not_allowed` | Method the route doesn't have |
| 415 | `unsupported_media_type` | Body that isn't JSON |
| 502 | `upstream_failed` | The AI service failed (phase D) |
| 500 | `internal` | A bug; the details only go to the server log |

## Tests

```bash
./gradlew :server:test
```

The tests start the real server module in memory (`testApplication`) with a fresh in-memory database,
so they need no running server and never touch `server/db/`. CI runs them on every push.

## Code

```
server/src/main/kotlin/ro/fitnesshub/server/
  Application.kt      main(): reads PORT/DB_URL, starts Netty; module() installs everything
  Plugins.kt          JSON, request validation, call logging, errors → JSON (StatusPages)
  Routing.kt          /health and the /api routes
  Auth.kt             who is calling (temporary header until C4)
  ApiModels.kt        request/response JSON classes
  Validation.kt       rules for request bodies (pure functions)
  routes/             one file per resource: read request → call repository → respond
  db/                 Exposed tables, AppDatabase (transactions on Dispatchers.IO), repositories (SQL)
  stats/WeeklyStats.kt  weekly numbers and personal records (pure functions)
```
