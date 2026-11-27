package com.pokehelper.adapters

import com.pokehelper.adapters.grpc.GrpcTeamRecommender
import com.pokehelper.domain.DependencyRejected
import com.pokehelper.domain.DependencyTimeout
import com.pokehelper.domain.DependencyUnavailable
import com.pokehelper.grpc.recommendation.v1.analyzeTeamResponse
import com.pokehelper.support.FakeRecommendationServer
import com.pokehelper.support.pokemon
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class GrpcTeamRecommenderTest {
    private val team = listOf(pokemon(6, "charizard", "fire", "flying"))

    @Test
    fun `gives up after the deadline`() = runBlocking {
        FakeRecommendationServer(analyze = {
            delay(10_000)
            analyzeTeamResponse { }
        }).use { server ->
            val elapsed = measureTime {
                assertFailsWith<DependencyTimeout> { GrpcTeamRecommender(server.channel, 200.milliseconds).analyze(team) }
            }
            assertTrue(elapsed < 2.seconds, "took $elapsed")
        }
    }

    @Test
    fun `translates INVALID_ARGUMENT and UNAVAILABLE`() = runBlocking<Unit> {
        FakeRecommendationServer(
            analyze = { throw StatusException(Status.UNAVAILABLE) },
            recommend = { throw StatusException(Status.INVALID_ARGUMENT.withDescription("unknown Pokémon type")) }
        ).use { server ->
            val recommender = GrpcTeamRecommender(server.channel, 2.seconds)
            assertFailsWith<DependencyUnavailable> { recommender.analyze(team) }
            val rejected = assertFailsWith<DependencyRejected> { recommender.recommendPokemon(team, "shadow") }
            assertEquals("unknown Pokémon type", rejected.message)
        }
    }
}
