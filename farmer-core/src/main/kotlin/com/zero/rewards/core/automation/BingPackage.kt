package com.zero.rewards.core.automation

/**
 * Bing Android app constants. Package name is stable across releases, but
 * resource-ids and text labels drift — so the runners match on multiple
 * signals (text, contentDescription, className) with fallbacks.
 */
object BingPackage {
    const val PACKAGE = "com.microsoft.bing"
    const val LAUNCH_INTENT = "com.microsoft.bing.LAUNCH"

    // Text/contentDescription hints seen across Bing app versions.
    // Localized to es (user's locale). English fallbacks included so a Bing
    // app set to English on the same device still works.
    /**
     * Placeholder strings seen inside the Bing search bar. The current
     * Copilot-branded build shows "Pregunta cualquier cosa" on home and a
     * plain "Buscar" on sub-screens like the Rewards hub — so matching
     * "Buscar" alone wrongly identifies the hub as home. Home-specific
     * matching uses [BING_HOME_HINTS].
     */
    val SEARCH_HINTS = listOf(
        "Pregunta cualquier cosa", "Ask anything", "Ask me anything",
        "Buscar", "Search", "Search the web",
    )

    /** Strings that only appear on the Bing home screen, not sub-screens. */
    val BING_HOME_HINTS = listOf(
        "Pregunta cualquier cosa", "Ask anything", "Ask me anything",
    )

    /**
     * Strings that only appear inside the Rewards hub. Must NOT overlap with
     * anything on the profile page — "Rachas diarias" / "Microsoft Rewards"
     * are shown there and would false-positive us. "Rachas" alone matches
     * "Rachas diarias" via substring, so it's banned here; the full phrase
     * "Intervalos diarios" is the actual hub-exclusive anchor.
     */
    val REWARDS_HUB_HINTS = listOf(
        "Intervalos diarios", "Daily streak",
        "Puntos de hoy", "Today's points",
        "CONJUNTO DIARIO", "Conjunto diario", "Daily set",
        "Actividades diarias", "Daily activities",
    )

    /** Hints for tapping the Rewards entry from home or the apps panel. */
    val REWARDS_HINTS = listOf(
        "Rewards",           // the home-screen card label (English literal on es build)
        "Puntos de hoy",     // apps-panel row label
        "Today's points",
        "Recompensas", "Rachas",
        "Mis puntos", "My Rewards",
    )
    val DAILY_SET_HINTS = listOf("CONJUNTO DIARIO", "Conjunto diario", "Daily set")
    val MORE_ACTIVITIES_HINTS = listOf("Más actividades", "Mas actividades", "More activities", "Puzzles y cuestionarios")
    val READ_TO_EARN_HINTS = listOf("Leer para ganar", "Read to earn", "Leer noticias")
    val NEWS_FEED_HINTS = listOf("Noticias", "News", "Para ti")
    val PROFILE_HINTS = listOf("Cuentas", "Accounts", "Perfil", "Profile", "Cuenta", "Account")
    val ACCOUNT_SWITCHER_HINTS = listOf(
        "Cambiar a otra cuenta", "Cambiar cuenta",
        "Switch to another account", "Switch account",
        "Administrar cuentas", "Manage accounts",
    )
    /**
     * Ad / sponsored content markers. "Ad" alone is too permissive (matches
     * "Advertencia", "Ady..."), so we match it only as an exact word via the
     * runner's isAd. The others are substring-safe.
     */
    val AD_HINTS = listOf(
        "Publicidad", "Patrocinado", "Sponsored", "Anuncio",
        "Contenido promocionado", "Promoted", "Promocionado",
    )

    /**
     * Video / non-text article markers. Read-to-earn only credits text
     * articles — video embeds give zero points on Bing's rewards side.
     * Detected via: thumbnail overlay text ("Video"), duration patterns
     * ("0:30", "1:45"), or player contentDescription ("Reproducir video").
     */
    val VIDEO_HINTS = listOf(
        "Reproducir", "Play video", "Ver video", "Reproducir video",
        "Reproducir vídeo", "Ver vídeo",
        "Vídeo", "Video de", "minutos de vídeo", "minutos de video",
    )

    /**
     * "Intervalos diarios" = Microsoft Rewards daily streak intervals. Keep
     * these specific so they don't match "Rachas diarias" on the profile page
     * (substring match would false-positive on the bare word "Racha").
     */
    val STREAK_SECTION_HINTS = listOf(
        "Intervalos diarios", "Daily streak",
        "Mantén tu racha", "Keep your streak",
    )

    /** All known section headers in the Rewards panel, used to clamp collection to a section. */
    val ALL_REWARDS_SECTION_HINTS = listOf(
        "CONJUNTO DIARIO", "Conjunto diario", "Daily set",
        "Más actividades", "Mas actividades", "More activities", "Puzzles y cuestionarios",
        "Intervalos diarios", "Daily streak", "Racha diaria", "Racha",
        "Punchcards", "Tarjetas perforadas", "Ofertas de puntos", "Punch cards",
    )
}
