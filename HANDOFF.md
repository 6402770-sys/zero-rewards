# zero-rewards — handoff notes for C.C.

leeme ANTES de tocar nada en este proyecto. son las lecciones duras que ya pagué.

---

## ⚠️ REGLA CERO — OBLIGATORIA

**ANTES de crear o editar cualquier función que interactúe con el UI de Bing (selectores, bands, coordenadas de tap, section anchors), si NO tenés un dump reciente de la estructura de esa sección, HACÉ EL DUMP PRIMERO.**

no hay excepción. no importa si parece obvio. no importa si "ya sabés cómo es". las versiones de Bing cambian, los wrappers cambian, los sizes cambian. todas las veces que adiviné perdí horas y tappeé cosas que no debía (la search bar en vez de las monedas, daily set tiles en vez de bonus, coins fantasma, etc).

**patrón del dump obligatorio**:

```kotlin
suspend fun run(account: Account): TaskResult {
    if (!panel.openPanel()) return TaskResult.Failed(...)
    primitives.idle(600, 1_000)

    // scroll a la sección
    repeat(6) { primitives.scrollUp(0.75f); primitives.idle(160, 280) }
    val header = scrollToHeader(SECTION_HINTS) ?: return Skipped(...)

    dumpSectionStructure(account, "NOMBRE_SECCION", header.boundsT, header.boundsB)

    primitives.backToBingHome()
    return TaskResult.Skipped("structure dump mode — see NOMBRE_SECCION STRUCT log")
}
```

el dump debe imprimir **mínimo**:
1. **otros anchors** visibles en el viewport (sabés dónde empieza y termina la sección)
2. **todos los nodos por Y row** (bucket de 40px): class, size, bounds, clickable, text, desc
3. **CLICKABLE nodes** sorted por Y
4. **tile-shaped** (candidatos a tile wrapper por size)
5. **TextViews con texto** (títulos, cooldowns, badges)
6. **tree completo depth 8** chunkeado por 1000 chars pa' no saturar logcat

ver `BingDailySetRunner.kt` / `BingMoreActivitiesRunner.kt` (commits viejos de dump mode) o `BingIntervalosRunner.kt` por el patrón concreto. El dump retorna `TaskResult.Skipped("structure dump mode — see ... log")` así el farm sigue.

después del dump el operator corre, mandás log, **con la data real recién** escribís el selector. nunca al revés.

**corolario**: si estás reworkeando algo y tu diff no toca el UI (solo refactor interno, cleanup, fix lógico), no hace falta dump. Si tocás selectores, bands, coords, sizes, class filters, section detection → **dump obligatorio**.

---

## qué es el proyecto

android app que automatiza microsoft rewards farmeando en la app oficial de bing (`com.microsoft.bing`).

- **módulo `app`**: UI (compose), `ZeroAccessibilityService` que implementa gestos.
- **módulo `farmer-core`**: runners (`BingAccountSwitcher`, `BingIntervalosRunner`, `BingDailySetRunner`, `BingMoreActivitiesRunner`, `BingReadToEarnRunner`, `BingSearchRunner`), orquestados por `FarmRun`.
- **entry**: `FarmerForegroundService` → `FarmRun.execute(account)`.

build: `./gradlew :farmer-core:compileDebugKotlin :app:compileDebugKotlin`
install al celu: `./gradlew :app:installDebug` (USB conectado)

---

## orden de fases en FarmRun (no tocar sin pensar)

```
0. Switch account
1. Intervalos diarios      — top del hub, 7 coins
2. Daily set               — CONJUNTO DIARIO (3 tareas)
3. More activities         — MÁS ACTIVIDADES (N tareas)
4. Search N times
5. Read-to-earn (10 articles max)
6. Final sweep             — re-run intervalos/daily_set/more_activities
```

cada runner es idempotente: tiles ya reclamadas se detectan y no se re-tappean.

---

## reglas que me tatué con sangre

### 1. NUNCA uses `host.tap()` para tiles de Bing. Usa `tapCoord(centerX, centerY)`.

`host.tap` dispara `ACTION_CLICK` de accessibility. Bing acepta la acción (devuelve `true`) pero NO navega — los tiles del Rewards hub, las filas del Cuentas screen, los tiles de daily set / more activities tienen onClick en **touch listeners**, no en ACTION_CLICK.

`tapCoord` dispatcha un gesto real (DOWN+UP) que pasa por el touch system y dispara el handler.

**patrón**: `primitives.host.tapCoord(node.centerX.toFloat(), node.centerY.toFloat())`. Nunca `host.tap(node)` para tiles o filas de lista.

### 2. `tapCoord` debe ser "humano": ≥100ms + micro-movimiento 2px

ya está en [`ZeroAccessibilityService.tapCoord`](app/src/main/kotlin/com/zero/rewards/service/ZeroAccessibilityService.kt): 110ms + `lineTo(cx+2, cy+2)`. Un tap estacionario de 50ms es filtrado por `GestureDetector` de RecyclerView.

### 3. `findByText` hace SUBSTRING match — cuidado con falsos positivos

- `findByText("Enviar")` matchea "Reenviar" (botón forward del browser chrome)
- `findByText("Racha")` matchea "Rachas diarias" en la profile page
- `findByText("Inicio")` NO es confiable porque está en múltiples bottom navs

**exact match** cuando importa: `findAllByText(word).firstOrNull { it.text?.trim().equals(word, ignoreCase = true) }`.

### 4. BACK en Bing home CIERRA LA APP

`backToBingHome` NUNCA debe hacer BACK ciego. Siempre tappear "Inicio" del bottom nav, o fallback a `launchApp(BingPackage.PACKAGE)`. Un BACK estando en home = app cerrada.

### 5. `looksLikeBingHome` estricto + rápido

- **RÁPIDO**: NO uses `findByAnyHint` con muchos hints — el `coerceAtLeast(400L)` por hint hace 12s/call. En `waitBingHome(40 rounds)` eso es 8+ minutos.
- **ESTRICTO**: "Inicio" en bottom nav NO basta — sub-screens también lo tienen. Marcadores de sub-screen: `"Atrás"` / `"Reenviar"` contentDesc (nav back/forward del browser), `"Sesión iniciada como"`, `"CONJUNTO DIARIO"`, `"Intervalos diarios"`, `"Puntos de hoy"`, `"Cuentas"`. Positive hint: `"Pregunta cualquier cosa"` (placeholder del search bar de home).

### 6. `ensureBingForeground` necesita retry con `waitForRoot`

`rootInActiveWindow` a veces devuelve null aunque Bing está en foreground. Patrón que anda:
1. Check currentPackage → launchApp si no es Bing
2. waitForRoot(6s). Si falla →
3. launchApp otra vez (fuerza WINDOW_STATE_CHANGED) + waitForRoot(8s)
4. delay 2s + launchApp + waitForRoot(10s)

### 7. Popups / dialogs tapan taps siguientes

Después de reclamar un intervalos coin, Bing muestra popup "+5". Tap al próximo coin inmediatamente va al popup. Patrón: `idle(1.8-2.6s)` + `dismissAwardPopup()` entre taps.

### 8. Coins / tiles NO siempre son el nodo clickable

Los intervalos coins son Views con `click=false` en todos los nodos del band — el touch handler vive en un ancestro. Y el centro visual del coin ≠ centro del label debajo (el label está **left-aligned** con la wrapper, no centrado). Hay que buscar la wrapper View directamente y tappear SU `centerX, centerY`.

### 9. Dedup por texto NO alcanza para cards con múltiples TextView

Un article card tiene title + description + párrafo + contentDesc. Dedup por el texto de UN nodo = la próxima iter encuentra OTRO nodo texto del mismo card y vuelve a tappear. Hay que juntar **todos los text/desc del card (±200px de band)** en visitedKeys.

También: dedup por `@y=T/50` bucket para cuando el texto cambia post-claim ("+10" → "10 ✓").

### 10. SCROLL después de reclamar un article

Después de leer un article, `scrollDown(0.5-0.6f)` para mover la viewport más allá del card. Sin eso, la próxima iter re-selecciona el mismo card.

### 11. **ANALIZÁ antes de scrollear agresivo (nueva)**

El Rewards hub es un WebView con nested scroll. **Un burst de `repeat(10) scrollUp(0.8f)` dispara parallax / bounce / rebote raro que termina moviendo el contenido en la dirección CONTRARIA**. En intervalos esto me costó horas.

**Patrón correcto**:
1. Abrir el panel → `idle(800-1200ms)` para que se renderice
2. Primer check sin scrollear: `findTargets()`. Si están visibles, actuar directo.
3. Si no → **dump de layout** (ver `analyzeHubLayout()` en intervalos): escanear Y de section headers conocidos → saber dónde estamos
4. Scroll calibrado basado en los Y observados, NO scroll burst ciego

### 12. Intervalos: Day labels pueden tener duplicados (histórico)

El Rewards hub a veces muestra "Day N" en 2 lugares: la fila activa (con coin tappeable arriba) y un row histórico/summary (texto sin coin tappeable). `findDayLabels()` debe agrupar por texto y filtrar: **quedarse solo con la label que tiene un coin View arriba** (`findCoinForLabel` returns non-null).

### 13. Entre coin taps, re-scan en vez de confiar en coords viejas

Después de cada coin tap, Bing auto-scrollea (animación de award). Los Day labels capturados inicialmente tienen coords stale. **Patrón**: en cada iteración del loop de taps, re-llamar `findDayLabels()` en el estado actual. Si faltan labels, scroll UP leve (0.4f, no burst).

### 14. More activities NUNCA debe tener fallback que escanee todo el hub

Mi viejo fallback `driveAllRemainingBadges` tappeaba cualquier clickable card del hub cuando `driveSection` retornaba 0 — resultaba tappeando tiles de CONJUNTO DIARIO pensando que eran bonus. **Eliminado**. Si driveSection no encuentra tiles de MA, trust it → Skipped. Mejor no tappear que tappear mal.

### 15. `driveSection` necesita detectar tiles vía CLICKABLE CARDS, no solo "+N" text

En Bing actual, "+5" es drawable, no texto. `pointBadgesInBand` devuelve vacío. `findUnvisitedTile` ahora tiene 2 strategies: (1) +N text badges (legacy), (2) **clickable tile cards scoped al section band** — mismo criterio que `findClickableCard` hub-wide pero bounded a `[sectionTopY..sectionBottomY]` para no bleedar a secciones adyacentes.

### 16. Cuentas screen: SELECT → "Cambiar ahora" (dos pasos)

El flow de switch en current Bing:
1. Tap la fila del email target → aparece `ImageView [seleccionado]` al lado
2. El button bottom-right cambia de "Cerrar sesión" a **"Cambiar ahora"**
3. Tap "Cambiar ahora" → commit

No es un dialog de confirmación — es un button que aparece recién después de seleccionar otra cuenta.

### 17. El drawer tiene un icono **"Reenviar"** que ES el switch-account button

No hay link textual "Cambiar cuenta" en el drawer de current Bing. El switch-account control es un `ImageView [Reenviar]` al lado del email header del drawer. Tappear ese icono abre la pantalla Cuentas. Mi [`findReenviarIcon`](farmer-core/src/main/kotlin/com/zero/rewards/core/runner/BingAccountSwitcher.kt) lo filtra a top 40% screen para no confundirlo con el Reenviar del browser chrome en sub-screens.

### 18. `openPerfilDrawer` debe filtrar Perfil por posición top-left

Puede haber un "Perfil" ImageView dentro de AccountDetails (avatar del user) además del hamburger button real. Filtrá por `y < 15% screen` AND `x < 30% screen` — eso es la zona del hamburger, nunca la zona de avatares interiores.

### 19. Ad detection: sibling-aware

El chip "Anuncio" / "Ad" / "Patrocinado" vive en un sibling del título del card, no en el título mismo. `isAdInCard` escanea nodos en banda ±350px alrededor del candidate, no solo el candidate.

### 20. Video detection: sibling-aware + duración regex

"Reproducir vídeo" contentDesc Y duration pattern `\b\d{1,2}:\d{2}\b` ("15:30") son señales de video card. Ambas en siblings, no en el candidate text.

### 21. `insideArticleIsVideo` solo señales EXPLÍCITAS

**No** usar heurísticas tipo "pocas TextViews = video" — mataba artículos reales cargando lento. Solo: VideoView en el tree, player controls ("Pausar", "Pantalla completa", "Saltar anuncio"), o VIDEO_HINTS text/contentDesc.

### 22. Each phase idempotent — final sweep re-run

Después del first pass de todas las fases, re-run intervalos/daily_set/more_activities. Como cada runner dedup y detecta already-claimed, el sweep es cheap y pesca lazy-loaded tiles / race conditions.

---

## patrón: ANALIZAR antes de ACTUAR

cuando una fase arranca y el target no está visible, NO scrollees a ciegas. Dumpeá primero:

```kotlin
suspend fun analyzeHubLayout() {
    FarmerLog.d("HUB ANALYSIS:")
    val markers = listOf(
        "Rachas", "Intervalos diarios", "CONJUNTO DIARIO", 
        "Puntos de hoy", "Actividades diarias", "MÁS ACTIVIDADES",
        "Canjea tus puntos",
    )
    for (m in markers) {
        val n = primitives.host.findByText(m, 150L) ?: continue
        FarmerLog.d("  '$m' @y=${n.boundsT}..${n.boundsB}")
    }
}
```

Usa los Y observados para decidir scroll direction y magnitud. Un scroll calibrado de 0.3-0.4f es MIL VECES mejor que un burst de 10×0.8f.

---

## bugs específicos cometidos (no repetir)

| bug | lección |
|-----|---------|
| `Path().moveTo(x,y)` sin `lineTo` + 50ms | GestureDetector filtra como ruido. `lineTo(x+2, y+2)` + 110ms. |
| `host.tap()` para tiles | tiles usan touch listeners. `tapCoord` siempre. |
| `findByText("Enviar")` matchea "Reenviar" | substring es peligroso. exact match cuando importa. |
| `backToBingHome` con BACK ciego | BACK en home cierra app. tappear Inicio. |
| `looksLikeBingHome` solo checking Inicio | sub-screens también tienen Inicio. requerir "Pregunta cualquier cosa" o markers específicos. |
| `findByAnyHint` con 30 hints en loop | 400ms × 30 = 12s/call. Mata waitBingHome. |
| REWARDS_HUB_HINTS con "Rachas" solo | matchea "Rachas diarias" en profile page. Frase completa "Intervalos diarios". |
| intervalos tap en `(label.centerX, boundsT - 50)` | label left-aligned → off-center del coin. Buscar wrapper View y tappear SU center. |
| más activities driveTileContent 20s + botones | tiles creditan por ENTRY. 1.5-2s dwell. |
| daily set `return meaningful > 0 \|\| true` | siempre true. completed++ fake. |
| read-to-earn text-density check | mataba artículos lentos. Solo señales explícitas. |
| read-to-earn dedup por text de UN nodo | cards tienen varios TextViews. collectCardKeys del band ±200px. |
| intervalos dwell 0.7-1.3s entre taps | popup "+5" visible. 1.8-2.6s + dismissAwardPopup. |
| ad detection solo en candidate | chip "Ad" en SIBLINGS. scan en banda ±350px. |
| video detection solo en candidate | duración/play-button en siblings arriba. scan banda arriba. |
| openPerfilDrawer agarraba cualquier "Perfil" | hay avatar inside AccountDetails. Filter top-left (y<15%, x<30%). |
| `host.tap(emailRow)` en Cuentas | row usa touch listener. tapCoord + luego "Cambiar ahora" button. |
| asumí que no existía "Cambiar cuenta" button | select → **"Cambiar ahora"** button bottom-right (aparece recién al seleccionar otra cuenta). |
| REWARDS_HINTS sin "Rewards" literal | home card dice **"Rewards"** en inglés incluso en es-AR. |
| waitBingHome 15s | MS servers tardan 5-25s. 30s budget. |
| `repeat(10) scrollUp(0.8f)` en Rewards hub | WebView nested scroll + parallax = rebote, scroll en dirección contraria. Scroll calibrado basado en analyzeHubLayout(). |
| intervalos Day labels duplicados | histórico sin coin. Dedup por text + requerir coin View arriba. |
| coords de labels viejos entre taps | Bing auto-scrollea después de cada claim. Re-scan con `findDayLabels()` cada iteración. |
| more_activities fallback a driveAllRemainingBadges | tappeaba daily set tiles. Eliminado. Solo driveSection. |
| `findUnvisitedTile` solo "+N" text | current Bing usa drawables. Strategy 2 = clickable cards scoped al band. |
| driveSection stale=2 | muy agresivo. Raised a 4. + scroll 0.7f cuando header está en bottom (y > 65%). |
| switcher: `host.tap(emailRow)` sin confirmar con button | select produce `[seleccionado]` marker + button "Cambiar ahora" aparece bottom-right. Dos pasos. |
| switcher: `waitBingHome` polling 25-40s después del commit | MS servers tardan, pero bloquea el pipeline. Solo tap Inicio una vez y dejar que la próxima fase foregrounde Bing de nuevo. |
| intervalos: selector square-ish 60-180px en banda top 65% sin anclar en header | Pescaba 12 "coins" a y=1440 (search bar + pseudo-row de "Buscar 1 vez" daily-set-streak). Anclar en header `"Intervalos diarios"` + band `[headerB+5, headerB+140]` strict. |
| intervalos: findGoldCoins con cluster-Y biggest | El cluster más denso NO es intervalos — son los labels del bottom nav o un pseudo-row. Anclar siempre en header, nunca heurística de densidad. |
| daily set + more activities: `driveSection` genérico con "clickable width>40% screen" | El filtro era muy permisivo. Tiles reales son clickable Views con **desc non-empty** + width > 50% screen + height 100..400. Dedup por `desc.take(50)` para colapsar outer/inner pairs. |
| daily set: band sin cortar en "Actividades diarias" | "Buscar para ganar" y "Leer para ganar" están en Actividades diarias section, NO en Daily Set. `findNextSectionTop` debe incluir `"Actividades diarias"` como boundary. |
| more activities: badge "0/N" interpretado como tile count | Es **puntos ganados / max puntos**, NO cantidad de tiles. Tiles tienen valores variables (algunos 5, otros más). Usar solo para log de puntos; terminar el loop por staleRounds, no por count. |
| more activities: `scrollUntilMaHeader` después de cada tap | Con N tiles donde N > tiles-que-caben-en-viewport, re-anclar el header al top undo el scroll-down → loop infinito en stale. Scroll una vez al principio, después confiar en que Bing restaura la scroll position en back; si el header sale del viewport, fallback a "any clickable wide tile" que es safe porque no hay secciones con tiles clickables abajo de MA. |

---

## convenciones del codebase

- **logs**: `FarmerLog.d/i/w/e`. Prefijos: `[${account.label}]`, `switcher:`, `driveSection:`, `  tile:`, `  video:`, `HUB ANALYSIS:`.
- **async**: todos los runners son `suspend fun run(account: Account): TaskResult`. TaskResult: `Ok(points, detail)` / `Skipped(reason)` / `Failed(reason, cause?)`.
- **diagnostics**: cuando algo falla por detector inexacto, agregar `dumpXxxDiagnostic()` que printe nodes cercanos (class/text/desc/size/click) **antes** de tirar error. El operator me manda el log del próximo run con el dump, afino el selector con data real.
- **idles**: `primitives.idle(min, max)` jitter aleatorio. `delay()` directo solo en casos muy específicos (post-switch Microsoft servers).
- **scrolls**: `scrollDown(fraction)` / `scrollUp(fraction)` — internamente clamp 0.05..0.75. Nunca `host.swipe` directo con coords calculadas (negativas crashean `GestureDescription`).

---

## estructura ideal de un runner

```kotlin
suspend fun run(account: Account): TaskResult {
    if (!primitives.ensureBingForeground()) return TaskResult.Failed("...")
    primitives.idle(SMALL, SMALL)
    
    // 1. VERIFY where we are (dump if target not visible)
    // 2. CALIBRATED scroll to reach section (no bursts)
    // 3. Iterate: tapCoord target → verify navigation → dwell → back → re-anchor
    // 4. Return TaskResult with real points credited (not fake)
}
```

---

## cuando algo falla (protocolo)

1. agregar `dumpXxxDiagnostic()` que printee clickable nodes + text nodes en la zona
2. pedir al operator el log del próximo run con ese dump
3. del dump: identificar el nodo REAL a tappear (clickable, posición, atributos)
4. afinar el selector con criterios específicos (class, size, bounds, contentDesc exacto)
5. **NUNCA adivinar sin data real** — ese es mi peor sesgo histórico. perdí varias iters asumiendo cosas sobre el UI de Bing en vez de dumpear.

## cuando empezás algo nuevo (protocolo)

ver **REGLA CERO** al principio del doc. mismo protocolo que "cuando algo falla", pero **antes** de escribir el selector, no después. si no tenés dump reciente de la sección que vas a tocar → dump mode primero, código después.

---

## cuando algo scrollea raro

**no scrollees más agresivo. ANALIZÁ**. Un dump de los Y de los section headers conocidos te dice EXACTAMENTE dónde estás. Entonces scroll calibrado, no burst.

patrón:

```kotlin
val header = primitives.host.findByText("Intervalos diarios", 500L)
if (header != null) {
    when {
        header.boundsT > screenH * 0.6 -> scrollDown(0.3f)  // header en bottom
        header.boundsT < screenH * 0.1 -> scrollUp(0.4f)    // header en top extremo
        else -> { /* ya se ve */ }
    }
}
```

---

listo. si tenés que tocar algo del flow y no leíste esto, parate y leelo. te va a ahorrar horas.
