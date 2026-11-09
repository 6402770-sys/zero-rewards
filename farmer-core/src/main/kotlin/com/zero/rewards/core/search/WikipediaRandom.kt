package com.zero.rewards.core.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WikipediaRandomSource(
    private val lang: String = "es",
    client: OkHttpClient? = null,
) : TermSource {

    private val http = client ?: OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun next(seen: Set<String>): String? = withContext(Dispatchers.IO) {
        val url = "https://$lang.wikipedia.org/w/api.php" +
            "?action=query&list=random&rnnamespace=0&rnlimit=10&format=json"
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "ZeroRewards/0.1 (+https://github.com/)")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                val root = Json.parseToJsonElement(body).jsonObject
                val arr = root["query"]?.jsonObject?.get("random")?.jsonArray ?: return@use null
                arr.map { it.jsonObject["title"]!!.jsonPrimitive.content }
                    .firstOrNull { it !in seen && !it.startsWith("Anexo:") && !it.startsWith("Categoría:") }
            }
        }.getOrNull()
    }
}
