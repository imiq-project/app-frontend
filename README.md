# IMIQ Mobility App

IMIQ is an Android app for Magdeburg, Germany. It helps you choose the best way
to travel: walking, bike, bus/tram or car.

The app learns what **you** care about like saving money, comfort, safety, the environment, being
active and then ranks the travel options for you personally.

## The Pipeline

**1. Sign in.**
You enter an access code. The app sends it to the IMIQ login server and gets a
token back.

**2. Answer a short survey.**
You rate 11 travel needs (comfort, cost, time, safety, environment, ...), pick
your top 3, say how often you use each transport mode and how you feel about
each mode (with emoji sliders).

**3. The server builds your "cognitive passport".**
Your answers are sent to the DYCONET service
(`https://imiq-app.et.uni-magdeburg.de/api/dyconet`). It runs dyconet model and sends back a JSON document: your cognitive passport. It
describes what you value and how you make travel decisions. The passport is
saved **only on your phone**. You can read all of it in the app
(Profile → Cognitive Passport).

**4. AI describes your travel personality.**
The app asks OpenAI (gpt-5.4) to read the passport and write a few 
sentences about you as a traveller. You see this at the end of the survey.

**5. You pick a destination.**
On the home screen (dark map) you type a place name. The app asks our own
geocoding service (`https://imiq-public.et.uni-magdeburg.de/api/geocode`) to
turn the name into map coordinates. Only places in the Magdeburg region are
shown, because that is the area our routing covers.

**6. The routing engine ranks the options.**
The app sends your passport + start point + destination to the ranked-routes
engine (`https://imiq-app.et.uni-magdeburg.de/api/routing/ranked-routes`).
The engine scores every transport mode against your personal values and
returns a ranked list, with reasons.

**7. GraphHopper draws the routes.**
For car, bike and walking the app asks GraphHopper (same server) for the real
route line, distance, time, and turn-by-turn steps and draws it on the map.

**8. Live conditions + the AI pick.**
The app reads urban digital twin data from FIWARE (weather, darkness, air quality,
traffic, free parking spots) and asks gpt-5.4: "Given this person's values,
these options, and the conditions right now, which mode is best NOW and
why?" The answer is the "AI recommendation" card on top of the results.

**9. Start the trip.**
Walk / bike / car: step-by-step directions inside the app.


```
 You ──► Survey ──► DYCONET server ──► Cognitive passport (saved on phone)
                                             │
 You ──► Type destination ──► Geocoder ──────┤
                                             ▼
                Ranked-routes engine (scores each mode with your passport)
                                             │
            GraphHopper (route lines)  +  FIWARE (live city data)
                                             │
                                             ▼
              gpt-5.4 picks the "best mode right now" and explains why
                                             │
                                             ▼
                 Results screen ──► Start ──► directions / Google Maps
```

## Servers used

| Service        | URL                                              | Job                                   |
|----------------|--------------------------------------------------|---------------------------------------|
| Login          | `imiq-public...de/api/dayplanner/login`          | access code → token                    |
| DYCONET        | `imiq-app...de/api/dyconet`                      | survey answers → cognitive passport    |
| Routing engine | `imiq-app...de/api/routing/ranked-routes`        | rank modes by your personal values     |
| GraphHopper    | `imiq-app...de/api/graphhopper/route`            | route lines + turn-by-turn steps       |
| Geocoder       | `imiq-public...de/api/geocode`                   | place name → coordinates               |
| FIWARE Orion   | `imiq-public...de/api/orion`                     | live weather / traffic / parking / air |
| OpenAI         | `api.openai.com`                                 | AI texts (model: gpt-5.4)              |

- MapLibre GL for the map (free Carto "Dark Matter" style, no API key)
- Retrofit / OkHttp + Gson for the APIs, kotlinx-serialization for the survey
- Languages for now: English + German 
