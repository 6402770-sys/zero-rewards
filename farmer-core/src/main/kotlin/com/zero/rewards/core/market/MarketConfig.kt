package com.zero.rewards.core.market

/**
 * Per-market configuration.
 *
 * - [code]         the `locale-COUNTRY` tag, e.g. `es-AR`, `en-US`
 * - [country]      the 2-letter country code for Bing `cc=` + account storage
 * - [msnPath]      the MSN URL path segment, e.g. `es-ar`, `en-us`
 * - [msnSections]  localized MSN section slugs that still credit read-to-earn
 * - [wikiLang]     Wikipedia language code for the random-article term source
 * - [bakedWordlist] optional static seed terms. null = Wikipedia-only.
 */
data class MarketConfig(
    val code: String,
    val country: String,
    val msnPath: String,
    val msnSections: List<String>,
    val wikiLang: String,
    val bakedWordlist: List<String>?,
) {
    val locale: String get() = code

    companion object {
        /** English section slugs — shared across all English-speaking markets. */
        private val ENGLISH_SECTIONS = listOf(
            "news", "sports", "entertainment", "money", "lifestyle",
            "health", "autos", "tech", "foodanddrink", "travel",
        )

        private val SPANISH_SECTIONS = listOf(
            "noticias", "deportes", "entretenimiento", "dinero", "estilo-de-vida",
            "salud", "motor", "tecnologia", "comida", "viajes",
        )

        private val PORTUGUESE_SECTIONS = listOf(
            "noticias", "esportes", "entretenimento", "dinheiro", "estilo-de-vida",
            "saude", "autos", "tecnologia", "gastronomia", "viagem",
        )

        private val GERMAN_SECTIONS = listOf(
            "nachrichten", "sport", "unterhaltung", "finanzen", "lifestyle",
            "gesundheit", "auto", "digital", "essen-und-trinken", "reise",
        )

        private val FRENCH_SECTIONS = listOf(
            "actualite", "sport", "divertissement", "finance", "lifestyle",
            "sante", "auto", "autres", "cuisine", "voyage",
        )

        private val ITALIAN_SECTIONS = listOf(
            "notizie", "sport", "intrattenimento", "denaro", "lifestyle",
            "salute", "auto", "tecnologia", "cibo", "viaggi",
        )

        val EN_US = MarketConfig(
            "en-US", "US", "en-us", ENGLISH_SECTIONS, "en",
            bakedWordlist = Wordlists.EN_US,
        )
        val EN_GB = MarketConfig(
            "en-GB", "GB", "en-gb", ENGLISH_SECTIONS, "en",
            bakedWordlist = Wordlists.EN_GB,
        )
        val EN_CA = MarketConfig("en-CA", "CA", "en-ca", ENGLISH_SECTIONS, "en", Wordlists.EN_US)
        val EN_AU = MarketConfig("en-AU", "AU", "en-au", ENGLISH_SECTIONS, "en", Wordlists.EN_US)
        val EN_IN = MarketConfig("en-IN", "IN", "en-in", ENGLISH_SECTIONS, "en", Wordlists.EN_US)

        val ES_AR = MarketConfig("es-AR", "AR", "es-ar", SPANISH_SECTIONS, "es", Wordlists.ES_AR)
        val ES_MX = MarketConfig("es-MX", "MX", "es-mx", SPANISH_SECTIONS, "es", Wordlists.ES_GENERIC)
        val ES_ES = MarketConfig("es-ES", "ES", "es-es", SPANISH_SECTIONS, "es", Wordlists.ES_GENERIC)

        val PT_BR = MarketConfig("pt-BR", "BR", "pt-br", PORTUGUESE_SECTIONS, "pt", Wordlists.PT_BR)

        val DE_DE = MarketConfig("de-DE", "DE", "de-de", GERMAN_SECTIONS, "de", null)
        val FR_FR = MarketConfig("fr-FR", "FR", "fr-fr", FRENCH_SECTIONS, "fr", null)
        val IT_IT = MarketConfig("it-IT", "IT", "it-it", ITALIAN_SECTIONS, "it", null)

        val ALL: List<MarketConfig> = listOf(
            EN_US, EN_GB, EN_CA, EN_AU, EN_IN,
            ES_AR, ES_MX, ES_ES, PT_BR,
            DE_DE, FR_FR, IT_IT,
        )

        private val BY_CODE: Map<String, MarketConfig> = ALL.associateBy { it.code.lowercase() }

        fun forCode(code: String?): MarketConfig? =
            code?.let { BY_CODE[it.lowercase()] }

        /** Default fallback when a stored account's market is unknown. */
        val DEFAULT: MarketConfig = EN_US
    }
}
