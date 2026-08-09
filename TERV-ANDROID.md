# Terv — IT Részvény Monitor natív Android alkalmazás

> Készült: 2026-08-09, Claude Fable 5 modellel. Előzmények: [TERV.md](TERV.md), [TERV-ETF.md](TERV-ETF.md), [TERV-AUTH.md](TERV-AUTH.md), [TERV-PORTFOLIO.md](TERV-PORTFOLIO.md), [TERV-RESZLETEK.md](TERV-RESZLETEK.md), [TERV-EU-ETF.md](TERV-EU-ETF.md), [README.md](README.md).
> Állapot: **6. fázis kész** (2026-08-09) — a fázisok a szokásos módon egyenként indulnak („Mehet a X. fázis").

## Kontextus és cél

A meglévő, tisztán kliensoldali webalkalmazás funkcióit **natív Android** alkalmazásként
kell elérhetővé tenni. Nem webes megoldás (nem WebView-ba csomagolt weboldal, nem PWA),
hanem Kotlin-alapú, Android Studio-ban megnyitható és fordítható projekt, amely:

- **ugyanazt a Supabase adatforrást** használja (projekt: `wotelbmgctbbrnbqlorg`,
  `stocks` séma, PostgREST + Auth + Edge Functionök) — a szerveroldalon **semmi nem változik**;
- **megőrzi a web-app stílusát és színvilágát** (sötét „aurora/indigo" téma,
  design-tokenek, magyar felület, hu-HU formázás);
- lefedi a web-app funkcióit: bejelentkezés, gyertya-/vonalgrafikon volumennel,
  napi/heti/havi felbontás, preset-időablakok, stat-kártyák, megjelenítési deviza
  (USD/EUR/HUF), portfóliók P/L-lel, szimbólum- és ISIN-kereső, ticker felvétel/törlés,
  Részletek (profil, ETF-összetétel).

**Nem cél (v1-ben):** offline működés (Room-perzisztencia), widget, push-értesítés,
több felhasználó, tablet-optimalizált elrendezés, Google Play publikálás. Ezek később
külön tervben bővíthetők.

## Fő döntések

### 1. Technológiai alap

| Terület | Választás | Indoklás |
|---|---|---|
| Nyelv | **Kotlin** | natív Android standard |
| UI | **Jetpack Compose + Material 3** (csak sötét téma) | deklaratív, a design-tokenek 1:1 átvihetők egyéni `ColorScheme`-be |
| Architektúra | egy Activity, **MVVM** (ViewModel + StateFlow + Repository) | a web-app `app.js` állapotgépének természetes megfelelője |
| Supabase-kliens | **supabase-kt** (hivatalos Kotlin SDK: Auth + Postgrest + Functions modulok, OkHttp/Ktor motorral) | a web-app supabase-js + kézi PostgREST-fetch kombinációját egyetlen, sémát (`stocks`) natívan kezelő könyvtár váltja ki |
| Szerializáció | kotlinx.serialization | supabase-kt függősége, DTO-khoz is ez |
| DI | **nincs keretrendszer** — kézi, Application-szintű konténer | a web-app „nincs build, nincs npm" szellemében: kevés mozgó alkatrész; Hilt egy ekkora, egyfejlesztős apphoz felesleges súly |
| Aszinkronitás | Kotlin coroutines + Flow | a gyors tickerváltás versenyhelyzetét (web: kérés-token + AbortController) a coroutine-`Job` cancel + `collectLatest` váltja ki |
| Beállítás-tárolás | DataStore Preferences | a localStorage megfelelője (megjelenítési deviza, utolsó ticker) |
| Min. SDK | **26** (Android 8.0), target 35 | java.time teljes támogatás desugaring nélkül; a készülékek >98%-a lefedett |
| Build | Gradle Kotlin DSL, verziókatalógus (`libs.versions.toml`), JDK 17, minden függőség **pinnelt verzión** | a web-app pinnelt CDN-elvének megfelelője |

### 2. Grafikon-könyvtár — döntést igényel

A webes TradingView Lightweight Charts JS-könyvtár, natívan nem fut. Lehetőségek:

| Opció | Előny | Hátrány |
|---|---|---|
| **MPAndroidChart** *(javasolt)* | teljesen natív; érett, széles körben használt; van CandleStick-, Line- és Bar- (volumen) chart; Apache-2.0 | aktív fejlesztése leállt (stabil, de nem fejlődik); a volumen-panelhez két nézet X-tengelyét kézzel kell szinkronizálni |
| TradingView `lightweight-charts-android` wrapper | pixelre azonos a webbel | **WebView-ban futtatja a JS-könyvtárat** — ütközik a „nem webes" követelménnyel |
| Vico / egyéb Compose-chartok | modern Compose-API | **nincs gyertyagrafikon** — kiesik |
| Saját rajzolás Compose Canvas-szal | teljes kontroll | aránytalanul nagy munka (crosshair, zoom, pan, tengelyek) |

**Javaslat: MPAndroidChart.** A gyertya/vonal + volumen + crosshair-legend mind
megvalósítható vele; a web-app színei (gyertyatest, kanóc, akcent) átadhatók.
A gyertya- és a volumen-chart külön nézet, X-viewportjuk gesztus-listenerrel
szinkronizálva (bevált minta). Compose-ba `AndroidView`-val ágyazódik be.

### 3. Elhelyezés a repóban

Új, **önálló Gradle-projekt** az `android/` mappában. Ez közvetlenül megnyitható
Android Studio-ban (File → Open → `android/`), és mivel önhordó (saját `settings.gradle.kts`,
`gradle/wrapper`), bárhová átmásolható/áthelyezhető — ez teljesíti az „Android Studio-ba
való áthelyezésre alkalmas" követelményt. A web-app érintetlen marad mellette.

### 4. Konfiguráció és titkok

A web-app `config.js`-ének megfelelője: `android/secrets.properties` (**gitignore-olt**),
amelyből a build `BuildConfig` mezőket generál: `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`APP_USER`, `APP_EMAIL`, `START_DATE`, `DEFAULT_TICKER`, `DEFAULT_DISPLAY_CURRENCY`.
Az anon kulcs a lockdown óta önmagában semmire sem jogosít (README, Autentikáció),
de a fegyelem marad: a repóba kulcs nem kerül. Egy `secrets.properties.example`
sablon dokumentálja a szükséges mezőket.

## Amit a terv NEM érint

**A Supabase-oldal változatlan.** Az Android-app pusztán egy új kliens ugyanarra a
backendre: ugyanaz az Auth (signInWithPassword), ugyanazok a táblák és RLS-szabályok,
ugyanazok az Edge Functionök (`sync-prices`, `asset-details`). Nincs új migráció,
nincs function-módosítás, nincs új secret. A web-app is változatlanul működik tovább.

## Architektúra

```
android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradle/ (wrapper + libs.versions.toml)
├── secrets.properties          ← gitignore-olt (sablon: secrets.properties.example)
└── app/
    ├── build.gradle.kts        ← BuildConfig-mezők a secrets.properties-ből
    └── src/
        ├── main/java/hu/jamborz/reszvenymonitor/
        │   ├── MonitorApp.kt           ← Application: kézi DI-konténer (Supabase-kliens, repository-k)
        │   ├── MainActivity.kt         ← egyetlen Activity, Compose-navigáció
        │   ├── data/
        │   │   ├── SupabaseModule      ← kliens-összeállítás (Auth, Postgrest defaultSchema="stocks", Functions)
        │   │   ├── dto/                ← Ticker, StockPrice, FxRate, Portfolio, PortfolioItem, AssetProfile
        │   │   ├── PriceRepository     ← lapozó letöltés, memória-cache (natív devizás sorok!), stale-while-revalidate
        │   │   ├── TickerRepository    ← tickers-lista, felvétel/törlés (sync-prices akciók)
        │   │   ├── FxRepository        ← fx_rates letöltés + cache
        │   │   ├── PortfolioRepository ← portfolios + portfolio_items CRUD (PostgREST, user-JWT)
        │   │   └── DetailsRepository   ← asset-details function hívás
        │   ├── domain/                 ← TISZTA Kotlin, Android-függőség nélkül (unit-tesztelhető)
        │   │   ├── Transform.kt        ← transform.js portja: UTC-dátumok, heti/havi OHLC, presetek, statisztika
        │   │   ├── Fx.kt               ← fx.js portja: USD-bázisú átváltás, forward-fill, GBp→GBP /100
        │   │   ├── PortfolioCalc.kt    ← portfolio.js portja: commonAxis, fillForward, Σ(darab×ár), P/L
        │   │   ├── Format.kt           ← format.js portja: hu-HU ár/%/volumen (E/M/Mrd)/dátum
        │   │   └── Suggest.kt          ← elgépelés-javaslat (Levenshtein ≤1/≤2, utótag nélküli alak)
        │   └── ui/
        │       ├── theme/              ← design-tokenek (lásd Téma szekció), tipográfia, alakzatok
        │       ├── login/              ← LoginScreen + AuthViewModel
        │       ├── monitor/            ← MonitorScreen (fő nézet) + MonitorViewModel
        │       │   ├── ChartPanel      ← MPAndroidChart AndroidView-ban: gyertya/vonal + volumen, legend
        │       │   ├── StatCards, IdentityHeader, Toolbar (presetek/felbontás/típus/deviza/volumen/frissítés)
        │       ├── search/             ← kereső-képernyő: helyi találatok, tőzsdei/ISIN-kereső, felvétel, javaslat
        │       ├── portfolio/          ← portfólió-kezelő képernyő
        │       └── details/            ← Részletek bottom sheet (profil / ETF-összetétel / portfólió-összetétel)
        ├── test/                       ← domain-rétegre JVM unit-tesztek (lásd Tesztelés)
        └── main/res/                   ← adaptív ikon (a favicon három gyertyája #1b2145 alapon), hu erőforrások
```

Rétegszabály: a `domain/` csomag nem importál se Android-, se Supabase-osztályt —
a web-app `transform/fx/portfolio/format` moduljainak tiszta portja, ugyanazokkal
a névvel visszakereshető függvényekkel. A repository-k adnak neki adatot, a
ViewModel-ek komponálják, a Compose-UI csak megjelenít.

## Téma — a webes design-tokenek megfeleltetése

A `styles.css` `:root` tokenjei egy az egyben átkerülnek egy Compose
`MonitorColors` objektumba (a Material 3 `ColorScheme` mögé, egyéni kiterjesztésként):

| CSS-token | Érték | Compose-megfelelő |
|---|---|---|
| `--bg` / `--bg-deep` | `#0B0E1A` / `#070912` | háttér / mély háttér (gradiens alsó rétege) |
| `--surface` / `--surface-strong` | rgba(148,163,204,·05/·09) | kártya-felületek (alpha-val a háttér fölött) |
| `--border` / `--border-strong` | rgba(139,153,204,·14/·28) | kártya- és pill-keretek |
| `--text` / `--text-dim` / `--text-faint` | `#E8EBF8` / `#93A0C4` / `#5D688C` | szövegszintek |
| `--accent` (+soft/ring/glow) | `#7C8CFF` alap, **futásidőben a ticker `color`-ja** | dinamikus akcent: a kiválasztott ticker színe állítja (mint a JS a CSS-változót) |
| `--up` / `--down` / `--warn` | `#34D399` / `#FB7185` / `#FBBF24` | gyertyatest/％-színek, „Elavult lehet" chip |
| `--radius` / `--radius-sm` | 14 / 10 px | 14.dp / 10.dp lekerekítés |
| betűtípus | Segoe UI (weben) | Androidon **Roboto/rendszer-alap** — a webes font-stack maga is erre esik vissza más platformon |

**Aurora-háttér:** a webes három radiális gradiens (indigó 12%/−12%, lila 92%/−4%,
türkiz 50%/118%) Compose `Brush.radialGradient`-ekkel, egy háttér-`Box`-ban.
A lassú drift-animáció mobilon **kimarad** (akkumulátor + a Compose folyamatos
recompose-t okozna); a statikus aurora adja a karaktert.

**Komponens-megfeleltetések:** pill-gombcsoportok → Compose chip-sor (aria-pressed →
selected állapot); stat-kártyák → 2 oszlopos rács (weben 6 egy sorban, mobilon 2×3);
modálok → teljes képernyős képernyő (portfólió-kezelő) ill. bottom sheet (Részletek);
státuszsor → Snackbar; hibasáv → a lista tetején megjelenő hibakártya „Újra" gombbal.
ETF-badge (cián), PORTFÓLIÓ-badge, tőzsde-badge, IPO- és „Elavult lehet" chip
ugyanazokkal a színekkel és feliratokkal.

## Képernyők (mobil adaptáció)

1. **Bejelentkezés** — a web login-overlay megfelelője: felhasználónév + jelszó,
   `APP_USER` → `APP_EMAIL` leképezés kliensen, jelszó-ellenőrzés kizárólag a
   Supabase Auth-on. Sikeres belépésig **semmilyen adatkérés nem indul**. A session-t
   a supabase-kt tárolja és frissíti automatikusan (SharedPreferences-alapú tároló);
   app-újraindításnál nem kell újra belépni. REST 401 bárhol → vissza a login-képernyőre
   (a web-app viselkedése).
2. **Fő nézet (Monitor)** — fentről lefelé: identitás-fejléc (szimbólum, név,
   tőzsde/ETF/PORTFÓLIÓ badge, utolsó adatnap, IPO/elavult chip, Részletek + Törlés),
   eszköztár (időszak-presetek: 1Hét/1M/3M/6M/YTD/1É/MIND; felbontás: Napi/Heti/Havi;
   típus: Gyertya/Vonal; deviza: USD/EUR/HUF; Volumen-kapcsoló; Teljes nézet; Frissítés),
   grafikon legenddel (crosshair-re Ny/Max/Min/Z/Vol + változás), stat-kártyák
   (Utolsó záró, Napi változás, Időszaki változás, Időszak max/min, Átlagvolumen).
   Az eszköztár mobilon vízszintesen görgethető chip-sorokba rendeződik.
   A keresőmező a fejlécben a kereső-képernyőre visz.
3. **Kereső** — teljes képernyős: helyi találatok „Részvények / ETF-ek / Portfóliók"
   csoportokban; 2+ karakterre „«…» keresése a tőzsdéken" opció (sync-prices `search`
   akció: ISIN → OpenFIGI → Yahoo, tőzsde- és deviza-badge-dzsel, felvettek jelölve);
   szimbólumszerű, ismeretlen beírásra „«X» felvétele új tickerként" (sync-prices `add`);
   elgépelésre „Talán erre gondoltál" csoport (Levenshtein-port a webből).
4. **Portfólió-kezelő** — lista + létrehozás; szerkesztő: név, tagok (ticker, darab,
   bekerülési ár, vételi dátum), tag-hozzáadás/-törlés, portfólió törlése, deviza-badge
   (a tagok tényleges devizái). Írás közvetlenül PostgREST-en a user-JWT-vel, owner-only
   RLS alatt — ugyanúgy, mint a weben.
5. **Részletek bottom sheet** — részvény/ETF: profil (szektor, iparág, ország,
   kapitalizáció, béta…); ETF-nél alap-adatok (TER, kategória, AUM), top 10 holding,
   szektormegoszlás (asset-details function, 7 napos szerveroldali cache); portfóliónál
   helyben számolt összetétel (érték + súly, összérték-lábléc), külső hívás nélkül.
6. **Ticker törlése** — a webes kétlépcsős megerősítés Androidon natív
   megerősítő dialógus („`SYM` törlése — biztos?"); portfólió-tagság esetén a
   `in-portfolio` hibakód a portfóliónevek felsorolásával jelenik meg.

## Adatréteg — a webes működés megőrzendő invariánsai

Ezek a web-appban mérésekkel kikényszerített szabályok; az Android-portban
mindegyik **kötelező** és unit-teszttel védendő:

1. **Lapozás:** a PostgREST 1000 soros plafonja miatt `limit=1000&offset=N` ciklus,
   amíg egy oldal rövidebb nem lesz 1000-nél — enélkül ~2027-től némán csonkulna az adatsor.
2. **A cache mindig natív devizás sorokat tárol.** A megjelenítési devizára váltás a
   cache FÖLÖTT, tisztán memóriában történik — devizaváltás nem indít hálózati kérést.
3. **Naponkénti FX-átváltás, nem spot rátával** (`érték × usd_rate(honnan) / usd_rate(hová)`,
   USD nem tárolt, definíció szerint 1); hiányzó napokra forward-fill.
4. **GBp ≠ GBP:** a londoni penny-jegyzés a font századrésze; a /100 pontosan egy
   helyen él (a webes `toUsdFactor` megfelelőjében), és a formázó ` p` / ` £` jelet
   is megkülönbözteti.
5. **Portfóliónál a sorrend kötött:** előbb közös dátumtengely + előre-töltés natív
   devizában, csak utána átváltás — különben a súlyok devizafüggővé válnának.
6. **A portfólió-idősor a legfiatalabb tag első adatnapjától indul**; tag-hiányos
   napon az utolsó ismert záró ad lapos hozzájárulást; volumen = 0, Volumen-gomb tiltva.
7. **P/L devizahatással:** bekerülési ár a vásárlás napi, jelenérték a mai árfolyamon;
   részleges költségadatnál „(N/M elem)" jelzés.
8. **Stale-while-revalidate:** cache-találatnál azonnali kirajzolás + csendes háttér-
   újratöltés; az összehasonlítás a **natív** sorokon fut (átváltott adaton devizaváltás
   után minden nap „változottnak" tűnne).
9. **Dátumfegyelem:** ISO `YYYY-MM-DD` stringek, minden eltolás UTC-ben (`java.time`,
   explicit `ZoneOffset.UTC`); heti kulcs a hét hétfője, havi a `YYYY-MM`; a bar dátuma
   az időszak első kereskedési napja.
10. **Fejlécek:** minden REST-hívásnál `apikey` + `Authorization: Bearer <user-JWT>` +
    `Accept-Profile: stocks` (írásnál `Content-Profile: stocks`) — a supabase-kt
    séma-beállítása ezt adja; a Functions-hívások a bejelentkezett user JWT-jét viszik
    (a `sync-prices` és `asset-details` owner-ellenőrzése miatt kötelező).
11. **FX-kiesés:** ha az `fx_rates` nem elérhető, a devizakapcsoló tiltódik, és minden
    a saját jegyzési devizájában látszik, hibajelzéssel — nem hibás számokkal.
12. **`up-to-date` válasz nem hiba** (Frissítésnél); a Yahoo-throttling kezelése
    (retry) a szerveroldalon van, a klienst nem érinti.

## Fázisok

A megszokott menet: minden fázis külön jóváhagyással indul, a fázis végén
ellenőrzés, és csak utána következik a következő.

### 0. fázis — Tervdokumentum

E terv mentése `TERV-ANDROID.md` néven a projektgyökérbe. *(Kész — ez a fájl.)*

### 1. fázis — Projektváz és téma

Gradle-projekt az `android/` mappában (wrapper, verziókatalógus, pinnelt függőségek:
Compose BOM, supabase-kt, MPAndroidChart, kotlinx.serialization, DataStore);
`secrets.properties` → BuildConfig; téma-modul a fenti token-táblázat szerint
(aurora-háttér, kártya, pill/chip, badge, chip-info/warn komponensek); adaptív
app-ikon a favicon gyertya-motívumából; magyar alap-lokalizáció.
**Ellenőrzés:** üres váz fordul (`gradlew assembleDebug`), emulátoron/készüléken
elindul, az aurora-háttér és egy minta-kártya vizuálisan egyezik a webbel.
*(Kész — 2026-08-09: `android/` váz Gradle 8.14.3 + AGP 8.8.2 + Kotlin 2.1.21 alapon;
assembleDebug zöld; Medium_Phone emulátoron ellenőrizve. A kockázati szekció
füsttesztje is lefutott: a supabase-kt az `Accept-Profile: stocks` fejléccel a
stocks sémába route-ol, anon kéréssel „permission denied for table tickers" —
a séma-kezelés hibátlan, a lockdown él.)*

### 2. fázis — Autentikáció

supabase-kt Auth: login-képernyő (felhasználónév→e-mail leképezés, hibaüzenet:
„Hibás felhasználónév vagy jelszó!"), session-perzisztencia + automatikus token-frissítés,
kijelentkezés a fejlécből, 401-elfogó (bármely REST-hívásnál lejárt session → login-képernyő).
**Ellenőrzés:** belépés a valós Auth-fiókkal; app-újraindítás után nincs újra-belépés;
kijelentkezés után adatkérés nem indul; szándékosan rossz jelszóval magyar hibaüzenet.
*(Kész — 2026-08-09: SupabaseModule + AuthRepository (név→e-mail leképezés,
sessionLost-jelzés, 401-kezelő `onAuthLoss`), LoginScreen a webes overlay
stílusában (rázás-animációval), gyökér auth-kapu: belépésig adatképernyő be sem
komponálódik. Emulátoron igazolva: rossz jelszóra „Hibás felhasználónév vagy
jelszó!". Az 5. fázis során emulátoron a teljes kör lezárva: valós belépés OK;
a session app-újratelepítést is túlél (nincs újra-belépés); Kijelentkezés →
login-képernyő, adatkérés nem indul; visszalépés valós jelszóval OK.)*


### 3. fázis — Domain-port unit-tesztekkel

A `transform.js`, `fx.js`, `portfolio.js`, `format.js` (+ Levenshtein a `ui.js`-ből)
tiszta Kotlin portja, **a webes forrás mellett haladva, függvényről függvényre**.
JVM unit-tesztek a fenti invariánsokra, a README-ben dokumentált mért értékekkel
hitelesítve: GBp /100 (100× hiba elvétésnél); IWDA.AS 2023-01-02 záró HUF-ban
korabeli vs. mai rátával (27 763 vs. 25 388 Ft, 9,4% eltérés); SXR8.DE×EURUSD ≈ CSPX.L
kereszt-próba; heti hétfő-kulcs és havi kulcs határnapokon; portfólió-súlyok
devizafüggetlensége; hu-HU formázás (E/M/Mrd, ` p` vs ` £`).
**Ellenőrzés:** `gradlew test` zöld; a tesztadatok egy része a web-appból exportált
valós sorokból származik, hogy a két implementáció számai bizonyítottan egyezzenek.
*(Kész — 2026-08-09: domain/ portolva (Transform, Fx, PortfolioCalc, Format,
Suggest), 39 unit-teszt zöld. A shiftDate a JS-Date hónapvégi ÁTGÖRDÜLÉSÉT
követi (nem a java.time clampet) — külön teszttel védve. Valós fixture-ök a
Supabase-ből exportálva (user-JWT-vel): az IWDA.AS már nem felvett ticker, ezért
a napi-vs-spot invariánst az SXR8.DE rögzíti — 2023-01-02 záró HUF-ban korabeli
rátával 150 759,98 Ft, 2026-08-07-es rátával 137 664,38 Ft, 8,69% eltérés;
SXR8.DE×EURUSD vs CSPX.L kereszt-próba 5 közös napon ≤0,12% (tolerancia 0,5%);
AIAG.L 2 967 p → 40,0329 $ a GBp/100 útvonalon. Portfólió-súlyok
devizafüggetlensége + fordított sorrend ellenpróbája valós fx-sorokkal.)*

### 4. fázis — Adatréteg

Repository-k: tickers-lista; stock_prices lapozó letöltéssel + memória-cache
(natív devizás sorok) + stale-while-revalidate; fx_rates + forward-fill;
kérés-versenykezelés coroutine-cancellationnel. A sync-prices `POST {"symbols":[…]}`
frissítés-út (cache-kerülő újratöltéssel), `up-to-date` kezelése.
**Ellenőrzés:** naplóból/hálózati figyelésből igazolva: lapozás több oldalnál is teljes
sort ad; devizaváltás 0 hálózati kérés; gyors tickerváltásnál csak az utolsó kérés
eredménye jut a UI-ra; anon-only kéréssel 401 (RLS él).
*(Kész — 2026-08-09: dto/ + ApiGuard (401 → onAuthLoss, magyar üzenetek),
PriceRepository (lapozó ciklus, natív cache, dailyFlow SWR-rel), FxRepository,
TickerRepository (lista + sync-prices frissítés-út). Valós hálózati integrációs
tesztek igazolják (env-hitelesítőkkel, nélkülük kihagyják magukat): fx-lapozás
1000+ sorral több oldalon át; cache-találat azonos példánnyal; flatMapLatest
mellett csak az utolsó szimbólum emissziói érnek célba; anon-only kérés 401 +
sessionLost; up-to-date válasz nem hiba. Mérési tanulság: a PostgREST 401 nem
mindig UnauthorizedRestException — a guard a státuszkódot nézi. A devizaváltás
0 kérése konstrukcióból adódik: a FxConverter.convertRows nem suspend, a cache
fölött tisztán memóriában fut.)*

### 5. fázis — Grafikon

ChartPanel: MPAndroidChart gyertya- és vonal-chart, volumen külön chartban
szinkronizált X-viewporttal; crosshair-highlightra legend-sor (Ny/Max/Min/Z/Vol +
változás, hu-HU formázással); gyertyaszínek `--up`/`--down`, vonal-mód a ticker
akcent-színével; „Teljes nézet" (fit); üres- és skeleton-állapot.
**Ellenőrzés:** ugyanarra a tickerre/presetre a web és az Android chart vizuálisan
egyező alakot ad; pinch-zoom és pan működik; a volumen-kapcsoló a panelt elrejti.
*(Kész — 2026-08-09: ChartPanel a vékony ChartController-absztrakcióval
(MPAndroidChart cserélhető marad): gyertya/area-vonal CombinedChartban +
volumen-BarChart szinkronizált X-viewporttal (fix tengelyszélesség tartja
fedésben); legend-sor a kiemelt/utolsó bar Ny/Max/Min/Z/Vol + változás
értékeivel hu-HU formázással; watermark; skeleton- és üres-állapot; preset-ablak
(a teljes sor betöltve marad, pan-nel elérhető — webes viselkedés); Teljes
nézet. Emulátoron valós NVDA-adattal igazolva: gyertya+volumen, vonal-mód
akcent-területtel, crosshair→legend, volumen-kapcsoló, pan + auto-Y-skála,
fit ~900 baron. A pinch-zoom kézi próbája a felhasználóra vár — adb-vel csak
egyujjas gesztus adható.)*

### 6. fázis — Fő nézet összekötése

MonitorViewModel: állapotgép az `app.js` mintájára (kiválasztott instrumentum,
preset, felbontás, típus, deviza, volumen); stat-kártyák (mindig napi adatból, az
aktuális preset-ablakra); identitás-fejléc chip-ekkel (IPO: `first_trade_date`;
„Elavult lehet": 3+ napos utolsó adatnap); Frissítés-gomb; hibakártya „Újra" gombbal;
deviza-választás DataStore-ba mentve; utolsó ticker megjegyzése.
**Ellenőrzés:** kereszt-ellenőrzés a webbel: azonos ticker/preset/deviza mellett
minden stat-kártya értéke számjegyre egyezik (NVDA USD-ben, ~~IWDA.AS~~ **SXR8.DE**
mindhárom devizában, AIAG.L GBp→HUF útvonalon).
*(Az IWDA.AS nem felvett ticker — az EUR-jegyzésű kereszt-próbát az SXR8.DE adja.)*
*(Kész — 2026-08-09: SettingsRepository (DataStore: deviza + utolsó ticker),
MonitorViewModel (az app.js állapotgépének portja: kiválasztás Job-cancellel,
preset/felbontás/típus/deviza/volumen, Frissítés sync-prices + cache-kerülő
újratöltéssel, hibakártya „Újra"-val), MonitorScreen (identitás badge-ekkel és
IPO/elavult chippel, görgethető eszköztár, 2×3 stat-rács, státuszsor). A minta-
képernyő törölve. A ticker `color`-ja az EGÉSZ felület akcentje, mint a weben
a `--accent` CSS-változó.*
*Az ellenőrzés GÉPI: a `tools/xcheck-web.mjs` a web-app EREDETI js/ moduljait
futtatja Node-ban ugyanazokra a valós sorokra, a `WebCrossCheckTest` pedig a
domain-porttal veti össze — 63 eset (3 ticker × 7 preset × 3 deviza) nyers
számra (1e-12) és formázott szövegre karakterre, plusz az aggregált barok
mindhárom felbontásban. **Ez fedett fel egy valódi eltérést:** a magyar CLDR
`minimumGroupingDigits=2` szabálya szerint az ezres elválasztó csak ötjegyű
egészrésztől jár (a böngésző így ír: `+1464,51%`, `8035,67 HUF`), a JDK
NumberFormat viszont már négyjegyűnél csoportosít — a Format.kt ezt most
kézzel kezeli. Emulátoron is igazolva: NVDA/6M/USD és AIAG.L/6M/USD+HUF minden
kártyája egyezik; deviza-váltás hálózat nélkül; ticker+deviza túléli az
újraindítást; Frissítés: „Frissítve: AIAG.L — 1 új sor (forrás: yahoo)".)*

### 7. fázis — Kereső és ticker-műveletek

Kereső-képernyő: helyi szűrés csoportokkal; tőzsdei/ISIN-kereső (sync-prices `search`);
felvétel (`add`, siker után lista-frissítés + kiválasztás, IPO-dátum jelzése);
törlés (`delete`, megerősítő dialógussal, `in-portfolio` blokk kezelése);
„Talán erre gondoltál" javaslat.
**Ellenőrzés:** `IE00B5BMR087` ISIN-re a jegyzés-lista deviza-badge-ekkel jön;
`SKR8` beírásra a javaslat `SXR8.DE`-t ajánl; ismert szimbólum felvétele és törlése
végigmegy, a webes felületen visszaellenőrizve.

### 8. fázis — Portfóliók

Portfólió-kezelő képernyő (CRUD PostgREST-en, owner-RLS); portfólió-nézet a fő
képernyőn: szintetikus összeg-idősor a domain-porttal, P/L-kártya („N/M elem"),
Volumen tiltva, portfólió-frissítés egyetlen sync-prices hívással; deviza-badge
a tagok tényleges devizáiból.
**Ellenőrzés:** a webes és az androidos portfólió-összérték és P/L számjegyre egyezik
mindhárom megjelenítési devizában; tag-hozzáadás Androidon → weben azonnal látszik
(közös adatbázis).

### 9. fázis — Részletek

Bottom sheet: asset-details hívás (profil / ETF-adatok / top 10 holding /
szektormegoszlás; `null` mezők elegáns kihagyása — „jobb nem mutatni vagyont, mint
rosszat"); portfóliónál helyi összetétel-számítás; forrás-lábléc.
**Ellenőrzés:** VWCE.DE-re az AUM a webes értékkel egyezik; IWDA.AS-nél az AUM-sor
kimarad; portfólió-összetétel offline (repülő módban, cache-elt árakból) is számolódik.

### 10. fázis — Csiszolás és kiadás

Élállapotok végigpróbálása (FX-kiesés → tiltott devizakapcsoló; lejárt session
munka közben; hálózat nélküli indulás); teljesítmény (MIND preset ~900 gyertya,
görgetés-smoke); kisegítő lehetőségek (contentDescription-ök, érintéscélok ≥48dp);
aláírt release APK (keystore a repón kívül), telepítés a célkészülékre USB-n vagy
APK-átvitellel; README-kiegészítés (android/ szekció: megnyitás Android Studio-ban,
secrets.properties kitöltése, build-lépések).
**Ellenőrzés:** release build a célkészüléken; egy teljes munkamenet (belépés →
böngészés → portfólió → részletek → frissítés → kijelentkezés) hibamentesen.

## Kockázatok, nyitott kérdések

- **MPAndroidChart karbantartottság:** stabil, de nem fejlődik. A ChartPanel mögé
  vékony absztrakció kerül, hogy a könyvtár később cserélhető legyen a UI-réteg
  érintése nélkül. *(Döntést igényel: elfogadható-e — a javaslat igen.)*
- **Két chart X-szinkronja** (gyertya + volumen): bevált, de kézi minta; ha túl
  törékenynek bizonyul, B-terv az egy charton belüli volumen (második Y-tengelyen,
  a panel-elválasztás vizuális gyengülésével).
- **supabase-kt sémakezelés:** az 1. fázis első lépése füstteszttel igazolni, hogy a
  `stocks` séma + owner-RLS kombináció a könyvtárral hibátlanul megy (a memóriában
  rögzített tanulság: mérni, nem feltételezni). Ha akadna rése, tartalék a kézi
  OkHttp-hívás a három kötelező fejléccel — a web-app is így él.
- **Betűtípus:** Segoe UI Androidon nincs; a rendszer-Roboto vizuálisan közel áll.
  Ha a karakter mégis hiányzik, opcionális bundled font (pl. Inter) 1 fázisnyi csere.
- **Egyfelhasználós jelleg:** a leképezés (APP_USER→APP_EMAIL) BuildConfig-ban —
  ugyanaz a modell, mint a weben; több felhasználó nem cél.
- **Az aurora-animáció kimarad** (akku); ha hiányozna, később egyszeri, belépéskori
  finom átmenet visszahozható.

## Tesztelés összefoglalva

- **JVM unit-tesztek a domain-portra** (3. fázis) — a README-ben dokumentált mért
  invariánsok mint tesztesetek; web-appból exportált valós sorok referencia-adatként.
- **Kereszt-ellenőrzés a webbel** minden adat-megjelenítő fázis végén: azonos
  bemenetre számjegyre azonos kimenet (közös adatbázis teszi lehetővé).
- **Kézi élállapot-próbák** a 10. fázisban (hálózat, session, FX-kiesés).
- Instrumentált UI-teszt v1-ben nem cél (egyfejlesztős, egyfelhasználós app);
  a domain-tesztek adják a biztonsági hálót.
