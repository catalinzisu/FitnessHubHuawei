# Fitness Hub server

The backend of Fitness Hub: a [Ktor](https://ktor.io) 3 server (Netty) with an H2 database.
It runs on your computer, not on the phone. The app works without it; the server adds an
account-based copy of training days and workouts, weekly stats and, later, the AI coach.

## Run it

You need JDK 21 (Android Studio's own JDK works) and the id of your Firebase project: the server
only accepts sign-in tokens for that project. Put it once in `local.properties` (not in git), the same
id as `project_id` in `app/google-services.json`:

```properties
firebase.projectId=your-project-id
```

Then:

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
| `FIREBASE_PROJECT_ID` | from `local.properties` (`firebase.projectId`) when started with `run` | Firebase project whose sign-in tokens are accepted; the server won't start without it |
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

### Sign-in

Every `/api` request carries the user's **Firebase ID token**: `Authorization: Bearer <token>`.
The app gets it from Firebase Authentication after signing in. The server never sees a password; it
checks the token itself (`Auth.kt`, Ktor `auth-jwt`) and takes the user id from it:

- signed (RS256) with one of Google's public keys, picked by the token's key id
  ([Google's key set](https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com), cached for a day);
- issuer `https://securetoken.google.com/<project id>` and audience `<project id>`;
- not expired (30 seconds of clock difference allowed; Firebase tokens last one hour);
- has a user id (`sub`).

Anything else (no token, expired, another project, forged, garbage) answers `401 unauthorized`.

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

Try it with curl. A token for a test account comes from Firebase's sign-in REST API; the web API key
is `current_key` in `app/google-services.json` (it identifies the project, it is not a password, but it
still stays out of git):

```bash
TOKEN=$(curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$API_KEY" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"returnSecureToken\":true}" \
  | python -c "import json,sys; print(json.load(sys.stdin)['idToken'])")

curl -X PUT http://localhost:8080/api/profile \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"displayName":"Demo","weeklyGoal":3}'

curl "http://localhost:8080/api/stats/weekly?weeks=2&tz=Europe/Bucharest" -H "Authorization: Bearer $TOKEN"
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
| 401 | `unauthorized` | No valid Firebase ID token |
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
Sign-in is tested with real JWTs signed by a key pair made for the tests (`TestTokens.kt`): the server
trusts that key instead of Google's, so the whole token check runs without network or a Firebase account.

## Code

```
server/src/main/kotlin/ro/fitnesshub/server/
  Application.kt      main(): reads FIREBASE_PROJECT_ID/PORT/DB_URL, starts Netty; module() installs everything
  Plugins.kt          JSON, request validation, call logging, errors → JSON (StatusPages)
  Routing.kt          /health and the /api routes
  Auth.kt             Firebase ID token check (auth-jwt) and the caller's user id
  ApiModels.kt        request/response JSON classes
  Validation.kt       rules for request bodies (pure functions)
  routes/             one file per resource: read request → call repository → respond
  db/                 Exposed tables, AppDatabase (transactions on Dispatchers.IO), repositories (SQL)
  stats/WeeklyStats.kt  weekly numbers and personal records (pure functions)
```
