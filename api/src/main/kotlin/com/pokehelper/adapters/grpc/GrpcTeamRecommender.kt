package com.pokehelper.adapters.grpc

import com.pokehelper.domain.DependencyRejected
import com.pokehelper.domain.DependencyTimeout
import com.pokehelper.domain.DependencyUnavailable
import com.pokehelper.domain.Pokemon
import com.pokehelper.domain.PokemonCandidate
import com.pokehelper.domain.TeamAnalysis
import com.pokehelper.domain.TeamRecommender
import com.pokehelper.domain.TypeCoverage
import com.pokehelper.domain.TypeScore
import com.pokehelper.grpc.recommendation.v1.AnalyzeTeamResponse
import com.pokehelper.grpc.recommendation.v1.RecommendPokemonResponse
import com.pokehelper.grpc.recommendation.v1.RecommendationServiceGrpcKt.RecommendationServiceCoroutineStub
import com.pokehelper.grpc.recommendation.v1.TeamMember
import com.pokehelper.grpc.recommendation.v1.analyzeTeamRequest
import com.pokehelper.grpc.recommendation.v1.recommendPokemonRequest
import com.pokehelper.grpc.recommendation.v1.teamMember
import io.grpc.Channel
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

class GrpcTeamRecommender(
    channel: Channel,
    private val timeout: Duration
) : TeamRecommender {
    private val stub = RecommendationServiceCoroutineStub(channel)
    private val log = LoggerFactory.getLogger(GrpcTeamRecommender::class.java)

    override suspend fun analyze(pokemons: List<Pokemon>): TeamAnalysis {
        val request = analyzeTeamRequest { members += pokemons.map(::member) }
        return call { analyzeTeam(request) }.toDomain()
    }

    override suspend fun recommendPokemon(pokemons: List<Pokemon>, type: String): List<PokemonCandidate> {
        val request = recommendPokemonRequest {
            members += pokemons.map(::member)
            this.type = type
        }
        return call { recommendPokemon(request) }.toDomain()
    }

    private suspend fun <T> call(rpc: suspend RecommendationServiceCoroutineStub.() -> T): T =
        try {
            stub.withDeadlineAfter(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS).rpc()
        } catch (exception: StatusException) {
            throw translate(exception.status, exception)
        } catch (exception: StatusRuntimeException) {
            throw translate(exception.status, exception)
        }

    private fun translate(status: Status, cause: Throwable): RuntimeException = when (status.code) {
        Status.Code.UNAVAILABLE -> DependencyUnavailable(UNAVAILABLE, cause)
        Status.Code.DEADLINE_EXCEEDED -> DependencyTimeout("The recommendation service did not answer in time", cause)
        Status.Code.INVALID_ARGUMENT ->
            DependencyRejected(status.description ?: "The recommendation service rejected the team", cause)
        else -> {
            log.warn("Recommendation service failed with {}: {}", status.code, status.description)
            DependencyUnavailable("The recommendation service failed (${status.code})", cause)
        }
    }

    private companion object {
        const val UNAVAILABLE = "The recommendation service is unavailable"
    }
}

object UnconfiguredTeamRecommender : TeamRecommender {
    private fun unavailable(): Nothing = throw DependencyUnavailable("The recommendation service is not configured")
    override suspend fun analyze(pokemons: List<Pokemon>) = unavailable()
    override suspend fun recommendPokemon(pokemons: List<Pokemon>, type: String) = unavailable()
}

fun recommendationChannel(target: String): ManagedChannel =
    ManagedChannelBuilder.forTarget(target)
        .usePlaintext()
        .build()

private fun member(pokemon: Pokemon): TeamMember = teamMember {
    id = pokemon.id
    name = pokemon.name
    types += pokemon.types
}

private fun AnalyzeTeamResponse.toDomain() = TeamAnalysis(
    coverage = coverageList.map {
        TypeCoverage(it.attackingType, it.immune, it.veryResistant, it.resistant, it.neutral, it.weak, it.veryWeak)
    },
    recommendedTypes = recommendedTypesList.map { TypeScore(it.type, it.score) }
)

private fun RecommendPokemonResponse.toDomain() = candidatesList.map {
    PokemonCandidate(
        id = it.id,
        name = it.name,
        types = it.typesList.toList(),
        baseStatTotal = it.baseStatTotal,
        combinationRank = it.combinationRank,
        spriteUrl = it.spriteUrl.ifBlank { null }
    )
}
