# IT Részvény Monitor — Android

A [web-app](../IT%20Reszveny%20Monitor) natív Android-kliense: **ugyanaz a Supabase-backend**,
ugyanazok a táblák, RLS-szabályok és Edge Functionök. A szerveroldalon semmi nem
változott — ez pusztán egy második kliens.

Kotlin + Jetpack Compose (Material 3), egyetlen Activity, MVVM. A fejlesztés
fázisokra bontva zajlott: a lépések, döntések és a hozzájuk tartozó mérések a
[TERV-ANDROID.md](TERV-ANDROID.md)-ben olvashatók.

## Mit tud

- **Árfolyam-grafikon** — gyertya/vonal, napi/heti/havi bontás, presetek
  (1Hét…MIND), volumen-panel, csippentéses nagyítás, lebegő OHLCV-legend.
- **Megjelenítési deviza** (USD/EUR/HUF) — váltáskor **nincs hálózati kérés**:
  a natív devizás sorok élnek a memóriában, azokból számol újra.
- **Kereső** — felvett tickerek, portfóliók, és a tőzsdék (ISIN-t is elfogad);
  új ticker felvétele és törlése.
- **Portfóliók** — létrehozás/átnevezés/törlés, tagok darabszámmal, bekerülési
  árral és vételi dátummal; szintetikus összeg-idősor és P/L-számítás.
- **Részletek** — részvény/ETF profil, ETF-nél alap-adatok, top 10 pozíció és
  szektormegoszlás; portfóliónál helyben számolt összetétel (hálózat nélkül is).

## Építés

### Előfeltételek

| | |
|---|---|
| JDK | 17+ a fordításhoz; **parancssorból JDK 24** ajánlott (a Gradle 8.14.3 ezt támogatja hivatalosan) |
| Android SDK | compileSdk/targetSdk 35, minSdk 26 |
| Android Studio | Ladybug vagy újabb (a saját JBR-jével is jó) |

### 1. `android/secrets.properties`

A web-app `config.js`-ének megfelelője. Másold le a sablont és töltsd ki:

```bash
cp android/secrets.properties.example android/secrets.properties
```

A fájl **gitignore-olt**; jelszó ide sem kerülhet (azt a Supabase Auth
ellenőrzi). Hiányzó fájl vagy kulcs esetén a build magyar hibaüzenettel áll meg.

### 2. Fordítás és futtatás

Android Studióban: `android/` megnyitása, majd Run.

Parancssorból:

```bash
cd android
JAVA_HOME=/c/Program\ Files/Java/jdk-24 ./gradlew assembleDebug
JAVA_HOME=/c/Program\ Files/Java/jdk-24 ./gradlew testDebugUnitTest
```

### 3. Aláírt kiadás

A keystore a **repón kívül** él, a jelszavai a gitignore-olt
`android/keystore.properties`-ben (sablon: `keystore.properties.example`).
Ha ez a fájl hiányzik, a release build aláíratlan APK-t készít — így a projekt
idegen gépen is lefordul.

```bash
cd android
./gradlew assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
```

> **Őrizd meg a keystore-t és a jelszavakat.** Elvesztésük után a telepített app
> többé nem frissíthető, csak eltávolítás + újratelepítés árán.

Telepítés készülékre: `adb install app-release.apk`, vagy az APK átmásolása és
megnyitása a telefonon (ismeretlen forrás engedélyezése szükséges).

## Felépítés

```
android/app/src/main/java/hu/jamborz/reszvenymonitor/
├── data/          Supabase-kliens, repository-k, DTO-k
├── domain/        TISZTA Kotlin: Transform, Fx, PortfolioCalc, Format, Suggest
└── ui/            Compose: login, monitor, search, portfolio, details, theme
```

A `domain/` a web-app `js/` moduljainak portja, Android-függőség nélkül — ezért
tesztelhető sima JVM-en, és ezért lehet a webbel gépileg összevetni.

## Tesztelés

```bash
cd android && ./gradlew testDebugUnitTest    # 72 teszt
```

A hálózatot igénylő `DataLayerIntegrationTest` magát kihagyja, ha nincs
`MONITOR_TEST_EMAIL` / `MONITOR_TEST_PASSWORD` környezeti változó — **jelszó a
repóba nem kerül**.

### Kereszt-ellenőrzés a web-appal

A számoknak és a szövegeknek **karakterre egyezniük kell** a web-appéval. Ezt nem
szemre nézzük: a `tools/` alatti szkriptek a web-app **eredeti** JS-moduljait
futtatják Node-ban, és a várt értékeket fixture-be írják; a Kotlin-tesztek
ugyanazokra a bemenetekre a portot futtatják.

```bash
node tools/xcheck-web.mjs        # transform.js + fx.js + format.js  -> WebCrossCheckTest
node tools/xcheck-portfolio.mjs  # portfolio.js + fx.js              -> PortfolioCrossCheckTest
node tools/xcheck-details.mjs    # ui.js modal-építői (DOM-csonkkal) -> DetailsCrossCheckTest
```

A web-app helyét a `MONITOR_WEB_DIR` környezeti változó írja felül (alapból a
szomszédos `IT Reszveny Monitor` mappa).

Ez a módszer valódi hibát fogott: a magyar ezres elválasztó a JVM-en négyjegyű
számnál is megjelenik, a böngésző ICU-jában viszont csak ötjegyűtől — a
`+1464,51%` így `+1 464,51%`-ra romlott volna.

## Ismert korlátok

- **Egyfelhasználós**: a felhasználónév→e-mail leképezés `BuildConfig`-ból jön,
  ahogy a weben is.
- **Nincs offline-tár**: az árfolyam-cache a memóriában él, az app újraindítása
  után hálózat kell. (Repülő módban a már betöltött adat és a portfólió-
  összetétel viszont továbbra is használható.)
- **Aurora-animáció kimarad** (akkumulátor), a háttér statikus gradiens.
- **Teljesítmény**: a MIND preset ~900 gyertyája emulátoron érezhetően terhel
  (mért medián képkocka-idő grafikon-hurcolásnál ~100 ms). Valós készüléken ez
  lényegesen jobb, de nagy adathalmaznál a grafikon a legdrágább elem.
