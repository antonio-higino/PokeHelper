package com.pokehelper.adapters.catalog

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.pokehelper.domain.CatalogDocument
import com.pokehelper.domain.CatalogEntryNotFound
import com.pokehelper.domain.DependencyRejected
import com.pokehelper.domain.DependencyTimeout
import com.pokehelper.domain.DependencyUnavailable
import com.pokehelper.domain.PokemonCatalog
import kotlinx.coroutines.future.await
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class HttpPokemonCatalog(
    baseUrl: String,
    private val timeout: Duration,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(timeout.toJavaDuration()).build()
) : PokemonCatalog {
    private val baseUrl = baseUrl.trimEnd('/')
    private val json = ObjectMapper()
    private val documentType = object : TypeReference<CatalogDocument>() {}

    override suspend fun pokemon(identifier: String): CatalogDocument =
        json.convertValue(get("/pokemon/${segment(identifier)}"), documentType)

    override suspend fun types(): List<CatalogDocument> =
        get("/types").path("items").map { json.convertValue(it, documentType) }

    override suspend fun type(name: String): CatalogDocument =
        json.convertValue(get("/types/${segment(name)}"), documentType)

    private suspend fun get(path: String) = try {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(timeout.toJavaDuration())
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        when (response.statusCode()) {
            in 200..299 -> json.readTree(response.body())
            404 -> throw CatalogEntryNotFound(errorMessage(response.body()) ?: "Pokémon resource not found")
            400 -> throw DependencyRejected(errorMessage(response.body()) ?: "The catalog rejected the request")
            else -> throw DependencyUnavailable("The Pokémon catalog failed (${response.statusCode()})")
        }
    } catch (exception: HttpTimeoutException) {
        throw DependencyTimeout("The Pokémon catalog did not answer in time", exception)
    } catch (exception: IOException) {
        throw DependencyUnavailable(UNAVAILABLE, exception)
    }

    private fun errorMessage(body: String): String? =
        runCatching { json.readTree(body)?.get("error")?.takeIf { it.isTextual }?.textValue() }.getOrNull()

    private fun segment(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val UNAVAILABLE = "The Pokémon catalog is unavailable"
    }
}

object UnconfiguredPokemonCatalog : PokemonCatalog {
    private fun unavailable(): Nothing = throw DependencyUnavailable("The Pokémon catalog is not configured")
    override suspend fun pokemon(identifier: String) = unavailable()
    override suspend fun types() = unavailable()
    override suspend fun type(name: String) = unavailable()
}
