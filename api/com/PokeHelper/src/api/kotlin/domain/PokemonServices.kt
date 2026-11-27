package com.example.domain

import com.fasterxml.jackson.databind.JsonNode

/** Application port; transport details belong to its adapters. */
interface PokemonServices {
    suspend fun pokemon(identifier: String): JsonNode
    suspend fun types(): JsonNode
    suspend fun type(name: String): JsonNode
    suspend fun analyze(body: JsonNode): JsonNode
    suspend fun recommendPokemon(body: JsonNode): JsonNode
}

class InvalidPokemonRequest(message: String) : RuntimeException(message)
class PokemonNotFound(message: String) : RuntimeException(message)
class PokemonServiceUnavailable(message: String) : RuntimeException(message)
