# Mini Games TV

Kicsi Android TV alkalmazás, amely teljes képernyőn, kurzor nélkül megnyitja a
[Mini Games](https://nomadbeni.github.io/minigames/) oldalt, és a Bluetooth-kontroller
gombjait átadja a játékoknak. A játékok kódja a `minigames` tárolóban van, ez az app nem módosítja.

> **MI-jelölés.** Ebben a tárolóban mindent – kódot, teszteket, leírást, a bannert – teljes
> egészében mesterséges intelligencia (Claude, Anthropic) készített; az ellenőrzést egy másik MI végezte.
>
> **AI disclosure.** Everything in this repository – code, tests, documentation and the banner – was
> created entirely by artificial intelligence (Claude, Anthropic); the review was done by another AI.

## Miért kell app?

A TV böngészője a kontrollert nem adja át a weboldalnak (a Gamepad API üres), a D-padból pedig
egérkurzort csinál. Az app a kontroller eseményeit natívan elkapja, és JavaScripten át adja tovább:

1. **billentyű-emuláció:** szintetikus `keydown`/`keyup` helyes `key`, `code` és `keyCode` mezővel;
2. **virtuális gamepad:** a `navigator.getGamepads()` egy „standard” leképezésű gamepadot ad vissza.

Ha egy játék maga olvassa a gamepadot (a minigames közös `gamepad` modulja), a billentyű-emuláció
automatikusan hallgat, így nincs dupla esemény.

## Adatvédelem

- Egyetlen engedély: `INTERNET`. Nincs analitika, összeomlás-jelentés, Google Play Services, reklám
  vagy hirdetésazonosító.
- WebView: Safe Browsing ki, WebView-mérőszám ki, harmadik féltől származó süti tiltva, fájl- és
  tartalomelérés ki, nincs felhőmentés.
- Csak a `https://nomadbeni.github.io/minigames/` alá eső címek nyílhatnak meg; minden más link
  csendben elnyelődik, külső böngésző sem nyílik.
- A játékok mentése (`localStorage`) csak a TV-n marad.

## Gombkiosztás

Az alap kiosztás az [`app/src/main/assets/keymap.json`](app/src/main/assets/keymap.json)-ban van.
A billentyűk a `minigames` játékainak billentyűkezelői alapján lettek kiválasztva.

| Kontroller | Csak billentyűs játékban | Gamepad API-s játékban (a játék saját kiosztása) | Startlapon |
|---|---|---|---|
| D-pad / bal kar | nyilak | nyilak | lépés a kártyák között |
| A | Enter (a kijelölt gombot meg is nyomja) | szóköz | kártya megnyitása |
| B | Escape | „l” | vissza / kilépés |
| X | szóköz | „j” | – |
| Y | „x” (jelölés: Gyémántbánya, Kincsvadász) | „k” | – |
| LB | „z” (visszavonás) | – | – |
| RB, LT, RT | – (csak a virtuális gamepadon) | RB: „l” | – |
| Start | Escape (szünet) | Escape (szünet) | – |
| Select | **az app „vissza” gombja** | **az app „vissza” gombja** | vissza / kilépés |
| távirányító Vissza | az app „vissza” gombja | az app „vissza” gombja | vissza / kilépés |

**Vissza:** ha van előzmény, egy oldalt vissza; a startlapon a második megnyomás (2 mp-en belül) kilép
az appból („Nyomd meg újra a kilépéshez”). A B gomb játékban a játéké (Escape), mert a játékok
akciógombként vagy szünetként használják.

A Trust GXT 735 Mylox a TV-n így jelentkezik (a debug átfedéssel kiolvasva): a gombok
`KEYCODE_BUTTON_*` kódként jönnek, a **D-pad `AXIS_HAT_X/Y` tengelyként**, a ravaszok analóg
tengelyként (`AXIS_BRAKE`/`AXIS_GAS`, 0–1), a jobb kar `AXIS_Z/RZ`.

## Játékonkénti állapot

A `minigames` kódjának elemzése alapján (2026-11-13). „Igen” = a játék a meglévő
irányítással kontrollerrel végigjátszható; a többi a `minigames` külön tervéhez tartozik.

| Játék | Mappa | Kontrollerrel | Megjegyzés |
|---|---|---|---|
| Arena | `betu-csata` | igen | Gamepad API (közös modul) |
| Downhill | `bringa-futam` | igen | Gamepad API |
| Bunyó | `bunyo` | igen | Gamepad API |
| Csapda | `csapda` | igen | Gamepad API |
| Dobozmester | `dobozmester` | igen | Gamepad API |
| E-Útvesztő | `e-utveszto` | igen | Gamepad API |
| Falbontó | `falbonto` | igen | Gamepad API |
| Horda | `horda` | igen | Gamepad API |
| Dungeon | `kalandor` | igen | Gamepad API |
| Kőkori Kaland | `kokori-kaland` | igen | Gamepad API |
| Páncélos őrség | `tankorseg` | igen | Gamepad API |
| Téglatörő Swipe | `teglatoro` | igen | Gamepad API |
| Minecraft-tervek (Cölöptorony, Feketevár lovagerőd, Hegyormi vár, Kőkastély árokkal, Rejtekház, Sasfészek sziklavár, Tündérvár) | | igen | a nyilak lapoznak a lépések között |
| Bike Shop | `bringa-muhely` | részben | csak a javítás fázisban reagál (szóköz = X); a többi érintéses |
| Invázió | `kertvedo` | nem | csak a szünet (Escape) billentyűs, a játék érintéses |
| Gyémántbánya | `gyemantbanya` | nem | a tábla billentyűzettel kezelhető, de csak ha egy cella fókuszban van – kontrollerrel ez nem érhető el |
| Kincsvadász | `kincsvadasz` | nem | mint a Gyémántbánya |
| Pasziánsz | `pasziansz` | nem | érintéses; csak Escape és „z” (visszavonás) billentyűs |
| Betűbánya | `betubanya` | nem | érintéses |
| Farm Empire | `kocka-kert` | nem | érintéses |
| Cornhole Scorekeeper | `cornholescore` | nem | érintéses pontozó |

## Telepítés a TV-re

1. A TV-n: Beállítások → Eszközbeállítások → Névjegy → a „Build” hétszeri megnyomása, majd
   Fejlesztői beállítások → **USB-hibakeresés** és **Hálózati hibakeresés** be.
2. Gépről (érdemes a TV-nek fix IP-t adni a routerben DHCP-foglalással):
   ```bash
   adb connect <TV-IP>:5555
   ```
   Az első alkalommal a TV-n jóvá kell hagyni („Mindig engedélyezed erről a gépről”).
3. Az APK a GitHub Actions „Build” futásának `mini-games-tv-apk` artifactja (vagy `v*` címkénél a
   Release). Telepítés és indítás:
   ```bash
   adb install -r mini-games-tv-*.apk
   adb shell am start -n io.github.nomadbeni.gamestv/.MainActivity
   ```
   Az app ezután a TV kezdőképernyőjén is megjelenik („Mini Games”).

## Fejlesztés

- JDK 17, Android SDK (compileSdk 37), Gradle wrapper. `minSdk 24`.
- `./gradlew test` – Kotlin unit tesztek (gombkiosztás, holtsáv-logika).
- `./gradlew assembleDebug` – debug build (`io.github.nomadbeni.gamestv.debug`, a release mellé települ).
- `npm ci && npx playwright install chromium && npx playwright test` – a beinjektált JavaScript
  (`app/src/main/assets/bridge.js`) tesztje asztali Chromiumban, az élő oldalon.

**Debug build a TV-n:**
- **L3** (bal kar lenyomása): átfedés a beérkező billentyűkódokkal és tengelyekkel; a `GamesTvInput`
  logcat-címkén is megjelenik (`adb logcat -s GamesTvInput`).
- **R3**: kontroller-próbaoldal (gombok, ravaszok, karok, beérkező billentyűk).
  Indítás közvetlenül: `adb shell am start -n io.github.nomadbeni.gamestv.debug/io.github.nomadbeni.gamestv.MainActivity --es page gamepad-test --ez overlay true`.
- Távoli hibakeresés: `chrome://inspect` a gépen (`adb forward` után).

## Aláírás és CI

A release buildet állandó kulccsal kell aláírni, különben a TV nem engedi felülírni a régit.
A kulcs és a jelszavai **soha nem kerülnek a tárolóba**.

- **Helyben** a `~/.gradle/gradle.properties` adja (a tárolón kívül):
  `gamestv.storeFile`, `gamestv.storePassword`, `gamestv.keyAlias`, `gamestv.keyPassword`.
- **A CI-ben** a GitHub Secrets (Settings → Secrets and variables → Actions → New repository secret):

  | Secret | Érték |
  |---|---|
  | `GAMESTV_KEYSTORE_BASE64` | a keystore base64-ben: `base64 -w0 <keystore-fájl>` |
  | `GAMESTV_STORE_PASSWORD` | a `gamestv.storePassword` értéke |
  | `GAMESTV_KEY_ALIAS` | `gamestv` |
  | `GAMESTV_KEY_PASSWORD` | a `gamestv.keyPassword` értéke |

A `.github/workflows/build.yml` minden pushnál lefuttatja a unit és a Playwright-teszteket, aláírt
release APK-t épít, és artifactként feltölti; `v*` címkénél GitHub Release is készül. A `versionCode`
a commitok száma, így minden új build felülírja a régit.

## Ellenőrizve

Philips (TPV) `PH0M_EA_T32`, Android 11, WebView 157, Trust GXT 735 Mylox (Bluetooth):

- [x] az app települ, a kezdőképernyőről elindul, az élő startlapot nyitja meg (aláírt release build)
- [x] a Mylox minden gombja, a D-pad, a ravaszok és mindkét kar megérkezik az appba (debug átfedés)
- [x] a szimulált kontrollergombok helyes `keydown`/`keyup` eseményként érkeznek egy billentyűs játékba
- [x] a kontroller lekapcsolódása és visszakapcsolódása után a bemenet újra működik

## Licenc

[MIT](LICENSE)
