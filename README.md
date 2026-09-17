# IMIQ Mobility App

IMIQ is an Android app for Magdeburg, Germany. It helps you choose the best way
to travel: walking, bike, bus/tram or car.

The app learns what **you** care about — saving money, comfort, safety, the
environment, being active, and related mobility needs — and uses your cognitive
passport downstream when ranking travel options.

## The Pipeline

**1. Sign in.**
You enter an access code. The app sends it to the IMIQ login server and gets a
token back.

**2. Complete the HOTCO-CT questionnaire.**
The Phase 1 onboarding contains five data steps:

1. rate 11 mobility needs;
2. rank your top 3 needs;
3. explicitly declare structural access to walking, bicycle/e-bike, public
   transport, and car;
4. rate all 44 need-mode belief relations;
5. rate the four action valences.

Availability is asked before the mode-representation questions and is never
inferred from frequency, preference, or observed behavior. Mode-use frequency is
not collected or sent to HOTCO-CT under `hotco_ct_input_2.1`.

Beliefs and valences are still measured for all four modes, including modes the
user marks unavailable. The UI labels those modes as unavailable and explains
that those ratings describe the user's representation of the mode; they do not
activate it. The explicit availability vector gates the corresponding HOTCO
action during the current simulation.

The app does not preselect neutral answers and will not submit an incomplete
model input.

**3. The server builds your "cognitive passport".**
Your answers are sent to the DYCONET service
(`https://imiq-app.et.uni-magdeburg.de/api/dyconet`). It runs HOTCO-CT v4.3
with the strict `hotco_ct_input_2.1` input contract and returns the stable
Cognitive Passport 2.0 downstream contract. No population, synthetic,
behavioral, or neutral value is used to fill a missing model input.

The Passport records availability provenance: Phase 1 uses only explicit
current-user access declarations, uses no external provider data, and performs
no frequency/preference inference. A provider boundary exists in the backend so
routing/API-derived availability can be added in a future version without
changing the HOTCO solver or the separate routing repository.

The passport is saved **only on your phone**. You can read it in the app
(Profile → Cognitive Passport), including deterministic HOTCO-CT XAI
diagnostics.

**4. AI describes your travel profile.**
The app asks OpenAI (gpt-5.4) to verbalize selected, model-grounded Passport/XAI
facts. The GPT layer does not generate the HOTCO trajectory or the Passport.

**5. You pick a destination.**
On the home screen you type a place name. The app asks the IMIQ geocoding
service (`https://imiq-public.et.uni-magdeburg.de/api/geocode`) to turn the name
into map coordinates. Only places in the Magdeburg region are shown because
that is the area currently covered by the routing setup.

**6. The routing engine ranks the options.**
The app sends your adapted passport + start point + destination to the
ranked-routes engine
(`https://imiq-app.et.uni-magdeburg.de/api/routing/ranked-routes`). The engine
scores available transport modes against the supplied profile and returns a
ranked list with reasons.

The app never substitutes a demo passport, fixed confidence, sample route
ranking, or preset origin when a live dependency fails. It shows an explicit
error instead. The routing engine must support the v4.3 adapter's eleven need
axes and four reported availability flags.

**7. GraphHopper draws the routes.**
For car, bike and walking the app asks GraphHopper for the real route line,
distance, time, and turn-by-turn steps and draws it on the map.

**8. Live conditions + contextual AI explanation/recommendation.**
The current route-results layer reads urban digital twin data from FIWARE and
uses gpt-5.4 for the existing contextual recommendation/explanation behavior.
This is separate from the Phase 1 HOTCO availability resolver and remains a
future architectural decision: the availability changes in this branch do not
modify the colleague-owned routing service.

**9. Start the trip.**
Walk / bike / car can use in-app directions; supported modes can also hand off
to Google Maps.

## Local HOTCO-CT build

The project has three explicit build variants:

| Variant | DYCONET endpoint | Intended use |
|---|---|---|
| `localEmulatorDebug` | `http://10.0.2.2:8077` | Android Studio emulator |
| `localUsbDebug` | `http://127.0.0.1:8077` | Physical phone after `adb reverse tcp:8077 tcp:8077` |
| `productionDebug` / `productionRelease` | production server | Deployed environment |

The two local variants bypass the remote access-code screen and open the
questionnaire directly when no passport exists. This bypass is a build-time
development behavior and is not present in the production variant.

With the local DYCONET backend running on port `8077`, build from Windows with:

```powershell
.\gradlew.bat testLocalEmulatorDebugUnitTest lintLocalEmulatorDebug `
  assembleLocalEmulatorDebug assembleLocalUsbDebug
```

Generated APKs are written to:

```text
app/build/outputs/apk/localEmulator/debug/app-localEmulator-debug.apk
app/build/outputs/apk/localUsb/debug/app-localUsb-debug.apk
```

The local variants make the questionnaire → HOTCO-CT → Cognitive Passport path
local. Route ranking, geocoding, maps, FIWARE and AI explanation remain separate
external services and are not silently replaced by demo data.

```text
 You ──► Survey ──► explicit availability resolver ──► HOTCO-CT
                                                      │
                                                      ▼
                                      Cognitive passport (saved on phone)
                                                      │
 You ──► Type destination ──► Geocoder ───────────────┤
                                                      ▼
                Ranked-routes engine (separate service / unchanged)
                                                      │
            GraphHopper (route lines) + FIWARE (live city data)
                                                      │
                                                      ▼
                  Results screen / contextual explanation
                                                      │
                                                      ▼
                         Start trip / directions
```

## Servers used

| Service        | URL                                              | Job                                   |
|----------------|--------------------------------------------------|---------------------------------------|
| Login          | `imiq-public...de/api/dayplanner/login`          | access code → token                    |
| DYCONET        | `imiq-app...de/api/dyconet`                      | survey answers → cognitive passport    |
| Routing engine | `imiq-app...de/api/routing/ranked-routes`        | rank modes by supplied passport        |
| GraphHopper    | `imiq-app...de/api/graphhopper/route`            | route lines + turn-by-turn steps       |
| Geocoder       | `imiq-public...de/api/geocode`                   | place name → coordinates               |
| FIWARE Orion   | `imiq-public...de/api/orion`                     | live weather / traffic / parking / air |
| OpenAI         | `api.openai.com`                                 | explanation/context text (gpt-5.4)     |

- MapLibre GL for the map
- Retrofit / OkHttp + Gson for the APIs, kotlinx-serialization for the survey
- Languages for now: English + German
