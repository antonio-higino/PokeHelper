package com.example.adapters.services

import com.example.domain.InvalidPokemonRequest
import com.example.domain.PokemonNotFound
import com.example.domain.PokemonServiceUnavailable
import com.example.domain.PokemonServices
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.Empty
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import com.pokehelper.contract.v1.Document
import com.pokehelper.contract.v1.LookupRequest
import com.pokehelper.contract.v1.PokemonCatalogGrpc
import com.pokehelper.contract.v1.RecommendationRequest
import com.pokehelper.contract.v1.RecommendationsGrpc
import com.pokehelper.contract.v1.TeamRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Channels connect lazily: the API can start and keep serving without Go. */
class PokeServicesClient(
    private val catalogChannel: ManagedChannel,
    private val recommendationChannel: ManagedChannel,
    private val timeoutMillis: Long = 2_000,
    private val json: ObjectMapper = ObjectMapper()
) : PokemonServices, AutoCloseable {
    constructor(pokemonTarget: String, recommendationTarget: String, timeoutMillis: Long = 2_000) : this(
        ManagedChannelBuilder.forTarget(pokemonTarget).usePlaintext().build(),
        ManagedChannelBuilder.forTarget(recommendationTarget).usePlaintext().build(),
        timeoutMillis
    )

    init { require(timeoutMillis > 0) { "gRPC timeout must be positive" } }
    private fun catalog() = PokemonCatalogGrpc.newBlockingStub(catalogChannel)
        .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
    private fun recommendations() = RecommendationsGrpc.newBlockingStub(recommendationChannel)
        .withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)

    override suspend fun pokemon(identifier: String): JsonNode = request { catalog().getPokemon(lookup(identifier)) }
    override suspend fun types(): JsonNode = request { catalog().listTypes(Empty.getDefaultInstance()) }
    override suspend fun type(name: String): JsonNode = request { catalog().getType(lookup(name)) }
    override suspend fun analyze(body: JsonNode): JsonNode = request {
        recommendations().analyzeTeam(TeamRequest.newBuilder().addAllPokemons(team(body)).build())
    }
    override suspend fun recommendPokemon(body: JsonNode): JsonNode = request {
        recommendations().recommendPokemon(RecommendationRequest.newBuilder()
            .addAllPokemons(team(body)).setType(body.path("type").asText()).build())
    }

    private fun lookup(identifier: String) = LookupRequest.newBuilder().setIdentifier(identifier).build()
    private fun team(body: JsonNode): List<Struct> = body.path("pokemons").map {
        Struct.newBuilder().also { builder -> JsonFormat.parser().merge(json.writeValueAsString(it), builder) }.build()
    }

    private suspend fun request(call: () -> Document): JsonNode = withContext(Dispatchers.IO) {
        val response = try { call() } catch (error: StatusRuntimeException) {
            when (error.status.code) {
                Status.Code.INVALID_ARGUMENT -> throw InvalidPokemonRequest(error.status.description ?: "Invalid Pokémon request")
                Status.Code.NOT_FOUND -> throw PokemonNotFound("Pokémon resource not found")
                // Fail only this operation; the rest of the API continues serving.
                else -> throw PokemonServiceUnavailable("A required Pokémon service is unavailable")
            }
        }
        if (!response.hasData()) throw PokemonServiceUnavailable("A Pokémon service returned an empty document")
        json.readTree(JsonFormat.printer().print(response.data))
    }

    override fun close() {
        catalogChannel.shutdownNow()
        recommendationChannel.shutdownNow()
    }
}
