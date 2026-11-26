package com.example.adapters.services

import com.example.adapters.http.ResourceNotFoundException
import com.example.adapters.http.InvalidRequestException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DownstreamServiceException(message: String) : RuntimeException(message)

class PokeServicesClient(
    pokemonServiceUrl: String,
    recommendationServiceUrl: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
    private val json: ObjectMapper = ObjectMapper()
) {
    private val pokemonBaseUrl = pokemonServiceUrl.trimEnd('/')
    private val recommendationBaseUrl = recommendationServiceUrl.trimEnd('/')

    suspend fun pokemon(identifier: String): JsonNode = get(pokemonBaseUrl, "/pokemon/${identifier.safePathSegment()}")

    suspend fun types(): JsonNode = get(pokemonBaseUrl, "/types")

    suspend fun type(name: String): JsonNode = get(pokemonBaseUrl, "/types/${name.safePathSegment()}")

    suspend fun analyze(body: JsonNode): JsonNode = post(recommendationBaseUrl, "/recommendations", body)

    suspend fun recommendPokemon(body: JsonNode): JsonNode =
        post(recommendationBaseUrl, "/recommendations/pokemon", body)

    private suspend fun get(baseUrl: String, path: String): JsonNode = request(
        HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build()
    )

    private suspend fun post(baseUrl: String, path: String, body: JsonNode): JsonNode = request(
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
            .build()
    )

    private suspend fun request(request: HttpRequest): JsonNode = withContext(Dispatchers.IO) {
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (_: Exception) {
            throw DownstreamServiceException("A required Pokémon service is unavailable")
        }
        when (response.statusCode()) {
            in 200..299 -> try {
                json.readTree(response.body())
            } catch (_: Exception) {
                throw DownstreamServiceException("A Pokémon service returned invalid JSON")
            }
            400 -> throw InvalidRequestException(downstreamMessage(response.body(), "Invalid Pokémon request"))
            404 -> throw ResourceNotFoundException(downstreamMessage(response.body(), "Pokémon resource not found"))
            else -> throw DownstreamServiceException("A required Pokémon service failed")
        }
    }

    private fun downstreamMessage(body: String, fallback: String): String = try {
        json.readTree(body)?.get("error")?.takeIf { it.isTextual }?.textValue() ?: fallback
    } catch (_: Exception) {
        fallback
    }
}

private fun String.safePathSegment(): String {
    val normalized = lowercase()
    if (!normalized.matches(Regex("[a-z0-9-]+"))) {
        throw InvalidRequestException("Pokémon identifier must contain only letters, numbers or hyphens")
    }
    return normalized
}
