package com.zero.rewards.core.market

/**
 * Baked seed term lists per language. Terms are deliberately mundane: weather,
 * recipes, news topics, local brands, sports. The runner mixes these with
 * Wikipedia random so queries stay plausibly human across a run.
 *
 * Markets without a baked list in [MarketConfig] rely entirely on
 * Wikipedia random in their [MarketConfig.wikiLang].
 */
internal object Wordlists {

    val EN_US: List<String> = listOf(
        "weather new york", "nfl scores today", "nba standings", "mlb results",
        "stock market today", "s&p 500 futures", "dollar to euro", "mortgage rates",
        "best laptops 2026", "iphone 17 review", "ps6 release date", "steam deck 2",
        "recipes chicken thighs", "sourdough starter guide", "sheet pan dinners",
        "instant pot recipes", "meatloaf recipe", "apple pie from scratch",
        "flights to miami", "hotels in las vegas", "best hiking trails colorado",
        "national parks map", "new york subway map", "chicago restaurants",
        "election results 2026", "congress news today", "supreme court decisions",
        "tax deadline 2026", "irs refund status", "social security 2026",
        "netflix new releases", "hbo max shows", "disney plus movies",
        "spotify playlists", "oscar nominations 2026", "grammy winners",
        "tesla stock", "nvidia stock", "apple news today", "microsoft layoffs",
        "coffee shops near me", "best burgers chicago", "ramen nyc",
        "home depot hours", "target weekly ad", "costco deals",
        "mlb trade rumors", "fantasy football rankings", "march madness bracket",
        "world cup qualifying", "college football rankings", "nba trade deadline",
    )

    val EN_GB: List<String> = listOf(
        "weather london", "premier league table", "championship fixtures", "fa cup draw",
        "pound to euro", "uk interest rates", "ftse 100 today", "house prices london",
        "tesco offers", "sainsburys deals", "aldi weekly", "m&s food",
        "bbc news today", "guardian breaking", "sky news live", "channel 4 schedule",
        "recipes jamie oliver", "nigella sourdough", "mary berry cakes", "sunday roast",
        "nhs appointments", "gp booking", "a&e waiting times", "nhs prescription",
        "london underground map", "oyster card top up", "trains from kings cross",
        "f1 silverstone", "wimbledon 2026", "six nations rugby", "the ashes",
        "coronation street", "eastenders tonight", "line of duty", "doctor who 2026",
        "cost of living crisis", "energy price cap", "council tax bands", "pip claim",
        "best uk broadband", "virgin media deals", "sky tv packages", "ee 5g coverage",
        "brexit news", "parliament today", "prime minister questions",
    )

    val ES_AR: List<String> = listOf(
        "clima buenos aires", "resultado boca river", "dolar blue hoy", "noticias argentina",
        "recetas milanesa", "como hacer alfajores", "receta empanadas", "futbol argentino hoy",
        "liga profesional tabla", "selección argentina", "messi noticias", "formula 1 colapinto",
        "tiempo cordoba", "tiempo rosario", "tipo de cambio", "indec inflacion",
        "subte linea b", "colectivos buenos aires", "mendoza turismo", "bariloche vuelos",
        "iguazu cataratas", "mar del plata playas", "salta turismo", "ushuaia que hacer",
        "feriados 2026 argentina", "calendario escolar", "trámite dni", "anses mi anses",
        "cuit consulta", "monotributo categorias", "prepaga precios", "hospital guardias",
        "universidad uba inscripcion", "técnicos ort", "cursos gratis coursera", "python aprender",
        "recetas pastas caseras", "dulce de leche casero", "mate cebado", "yerba mate marcas",
        "wine malbec argentino", "asado parrilla tiempos", "chimichurri receta", "locro receta",
        "guiso lentejas", "pizza argentina estilo", "milanesa napolitana", "flan casero",
        "spotify argentina", "netflix estrenos", "disney plus", "amazon prime video",
        "mercado libre ofertas", "mercadopago comisiones", "ualá tarjeta", "brubank cuenta",
        "banco nacion plazo fijo", "plazo fijo rendimiento", "cedear cotizacion", "acciones ypf",
        "clima la plata", "temperatura hoy caba", "lluvia pronóstico", "radar meteorológico",
        "trafico ruta 2", "peajes autopista", "subte horarios", "tren sarmiento",
        "inflacion argentina 2026", "riesgo pais hoy", "bonos cotización", "merval",
    )

    val ES_GENERIC: List<String> = listOf(
        "clima hoy", "noticias de ultima hora", "resultados futbol", "liga española tabla",
        "real madrid noticias", "fc barcelona noticias", "atletico de madrid", "copa del rey",
        "selección española", "liga mx tabla", "recetas tortilla española", "paella receta",
        "empanadas receta", "tacos al pastor receta", "guacamole casero", "mole poblano",
        "renfe horarios", "iberia vuelos", "aeropuerto barajas", "metro madrid mapa",
        "temperaturas mañana", "pronostico lluvia", "huracan trayectoria",
        "bolsa de madrid ibex", "euro dolar cotizacion", "tipo de cambio peso mexicano",
        "renta 2026", "declaracion impuestos", "seguridad social tramites",
        "amazon españa ofertas", "mercadona precios", "carrefour catalogo",
        "walmart mexico", "oxxo ubicaciones", "liverpool mexico",
        "netflix catalogo", "hbo max estrenos", "disney plus series",
        "spotify tendencias", "tiktok virales español", "whatsapp estados",
        "universidades online", "cursos de ingles gratis", "aprender python",
    )

    val PT_BR: List<String> = listOf(
        "tempo sao paulo", "resultado flamengo", "brasileirao tabela", "copa do brasil",
        "selecao brasileira", "neymar noticias", "formula 1 brasil", "nba resultados",
        "dolar hoje", "bolsa b3 cotacao", "petrobras acoes", "vale acoes",
        "receita feijoada", "pao de queijo receita", "brigadeiro caseiro", "coxinha de frango",
        "feijao tropeiro", "moqueca baiana", "acaraje", "tapioca recheada",
        "clima rio de janeiro", "tempo curitiba", "chuva previsao", "furacao caminho",
        "cpf consulta", "cpf situacao receita", "imposto de renda 2026", "serasa score",
        "metro sp linha azul", "passagem rodoviaria", "aeroporto guarulhos",
        "noticias globo", "uol ultimas", "g1 noticias", "folha sp",
        "mercadolivre ofertas", "shopee promocoes", "amazon prime br", "magalu",
        "nubank cartao", "itau pix", "picpay saldo", "bradesco app",
        "netflix brasil", "globoplay", "disney plus series", "prime video filmes",
        "spotify playlists", "tiktok viral brasil", "kwai videos",
        "cursos gratis online", "python para iniciantes", "excel avancado",
    )
}
