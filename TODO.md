# TODO – Fitness Hub (în ordine)

Lista de lucru pentru proiectul final, făcută după `proiect-final.pdf` (baremul).
Detaliile și motivele sunt în [fitness-hub-plan.md](fitness-hub-plan.md), secțiunea 0.
Bifăm `[x]` pe măsură ce terminăm. Ordinea contează: un pas se începe doar după ce cel dinainte compilează, trece testele și merge pe emulator.

Legendă: **C1–C9** = criteriile din barem.

---

## Gata (înainte de barem)

- [x] Schelet aplicație, Health Connect, dashboard „Today”
- [x] Gym logger: bibliotecă exerciții, zile de antrenament, antrenament activ, timer de pauză
- [x] Progres: grafice 1RM/volum, PR-uri, sugestie de progresie
- [x] Puls/calorii de la ceas pe antrenamente, export în Health Connect
- [x] WorkManager: sincronizare în fundal (C9)

## Faza A – Versiuni la zi + navigație type-safe (14–16 nov)

- [x] A1. Toate dependențele la ultima versiune minoră: Gradle 9, AGP 9, Kotlin 2.4, KSP, Compose BOM, Navigation, Lifecycle, Activity, Core, Room, WorkManager
- [ ] A2. Navigație type-safe: rute `@Serializable` în loc de rute text, argumente cu `toRoute()` (C4)
- [ ] A3. Back stack: tab-uri fără duplicate, `launchSingleTop`, „close” sigur (C4)
- [ ] A4. Editare set (Update) → CRUD complet pe seturi (C5)
- [ ] A5. ViewModel-urile în fișiere separate; evenimente one-shot prin stare, nu callback-uri (C3)

## Faza B – Server Ktor (17–23 nov) – C6

- [ ] B1. Modul `server/`: Ktor (Netty), ContentNegotiation JSON, CallLogging, StatusPages
- [ ] B2. Bază de date pe server: Exposed + H2 (fișier), tabele users/profile/templates/workouts/sets
- [ ] B3. Rute: `GET /health`, `/api/profile` (GET, PUT), `/api/templates` (GET, POST, GET/PUT/DELETE pe id), `/api/workouts` (GET `?since=`, POST, DELETE pe id)
- [ ] B4. Logică business: `GET /api/stats/weekly` (volum pe mușchi, antrenamente/săptămână, PR-uri)
- [ ] B5. Validare request-uri + erori JSON (400/401/404/502)
- [ ] B6. Teste cu `testApplication` pentru fiecare rută
- [ ] B7. Rulare locală documentată: `./gradlew :server:run`, telefon prin `adb reverse tcp:8080 tcp:8080`, emulator prin `10.0.2.2`

## Faza C – Autentificare + client REST (24–30 nov) – C7, C8, C2, C4

- [ ] C1. Proiect Firebase (Spark), Firebase Auth email + parolă, `google-services.json` (ignorat de git)
- [ ] C2. Ecrane Login / Register / Reset parolă, validare input, mesaje de eroare
- [ ] C3. Flux login cu `popUpTo<Login> { inclusive = true }`; logout golește tot back stack-ul
- [ ] C4. Serverul verifică token-ul Firebase (JWT) cu `auth-jwt`; fiecare user vede doar datele lui
- [ ] C5. Ktor Client + kotlinx.serialization, bearer token, `Dispatchers.IO`, erori tipizate
- [ ] C6. URL-ul serverului din `local.properties` → `BuildConfig` (nimic hardcodat)
- [ ] C7. Sincronizare zile de antrenament + antrenamente (WorkManager, constrângere de rețea, retry)
- [ ] C8. Banner „server indisponibil / offline”; aplicația merge complet și fără server

## Faza D – AI coach prin server (1–7 dec) – C6, C8, C9

- [ ] D1. Detector de platou (Kotlin pur, testat)
- [ ] D2. Rezumat anonim + ecran „Ce se trimite” (preview)
- [ ] D3. `POST /api/coach/analyze` pe server: detector + Gemini, cheia din variabilă de mediu
- [ ] D4. Ecran Coach: întrebări rapide, răspuns, fallback offline (doar concluziile detectorului)
- [ ] D5. Opt-in + avertisment „generat de AI, nu e sfat medical”

## Faza E – Finisare + pregătire susținere (8–14 dec) – C1, C2, C3

- [ ] E1. Layout responsive: landscape, navigation rail pe ecrane late (C1)
- [ ] E2. Stări loading / empty / error pe fiecare ecran cu rețea (C1, C2)
- [ ] E3. Test de crash-uri: rotire pe fiecare ecran, mod avion, date invalide (C2)
- [ ] E4. Politica de confidențialitate actualizată (server, Firebase, Gemini)
- [ ] E5. `docs/defense.md`: criteriu → fișiere, unde e starea / apelurile async / persistența, glosar adnotări
- [ ] E6. Parcurgem codul împreună, ecran cu ecran (fără asta punctajul e 0!)
- [ ] E7. Doar dacă rămâne timp: nutriție (Open Food Facts), import export Huawei, biometrie

## Buffer (15–21 dec)

- [ ] F1. Verificare din nou a versiunilor (mvnrepository) și actualizare
- [ ] F2. Arhivă de predare din git (fără `build/`, `data/`, secrete)
- [ ] F3. Repetiție susținere: server + aplicație pornite, demo complet
- [ ] F4. Evaluarea pentru lucrare (scenarii sintetice, condiția A vs B)

## Întrebări deschise

- [ ] Data susținerii? (dacă e înainte de 21 dec, comprimăm fazele)
- [ ] Mai contează cerințele din tema inițială (nutriție, partajare, obiective)?
