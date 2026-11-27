package com.pokehelper

import com.fasterxml.jackson.databind.ObjectMapper
import com.pokehelper.adapters.catalog.UnconfiguredPokemonCatalog
import com.pokehelper.adapters.grpc.GrpcTeamRecommender
import com.pokehelper.grpc.recommendation.v1.analyzeTeamResponse
import com.pokehelper.grpc.recommendation.v1.pokemonCandidate
import com.pokehelper.grpc.recommendation.v1.recommendPokemonResponse
import com.pokehelper.grpc.recommendation.v1.typeRecommendation
import com.pokehelper.support.FakeRecommendationServer
import com.pokehelper.support.InMemoryTeamRepository
import com.pokehelper.support.InMemoryUserRepository
import com.pokehelper.support.pokemon
import com.pokehelper.usecases.PokemonCatalogService
import com.pokehelper.usecases.TeamRecommendationService
import com.pokehelper.usecases.TeamService
import com.pokehelper.usecases.UserService
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class RecommendationFlowTest {
    private val json = ObjectMapper()
    private val users = InMemoryUserRepository()
    private val teams = InMemoryTeamRepository()
    private val teamId = runBlocking {
        val owner = users.create("ash", "secret")
        teams.create("Kanto", owner.id, listOf(pokemon(6, "charizard", "fire", "flying"))).id
    }

    private fun ApplicationTestBuilder.api(
        server: FakeRecommendationServer
    ) = application {
        configureApi(
            ApiServices(
                users = UserService(users, teams),
                teams = TeamService(teams, users),
                recommendations = TeamRecommendationService(teams, GrpcTeamRecommender(server.channel, 2.seconds)),
                catalog = PokemonCatalogService(UnconfiguredPokemonCatalog),
                databaseReady = { true }
            )
        )
    }

    @Test
    fun `returns what the gRPC service computed`() = FakeRecommendationServer(
        analyze = { analyzeTeamResponse { recommendedTypes += typeRecommendation { type = "water"; score = 6 } } },
        recommend = { request -> recommendPokemonResponse { candidates += pokemonCandidate { name = "blastoise"; types += request.type } } }
    ).use { server ->
        testApplication {
            api(server)
            val analysis = client.get("/teams/$teamId/analysis")
            assertEquals(HttpStatusCode.OK, analysis.status)
            assertEquals("water", json.readTree(analysis.bodyAsText())["recommendedTypes"][0]["type"].asText())

            val recommendations = client.get("/teams/$teamId/recommendations?type=water")
            assertEquals(HttpStatusCode.OK, recommendations.status)
            assertEquals("blastoise", json.readTree(recommendations.bodyAsText())["candidates"][0]["name"].asText())
        }
    }

    @Test
    fun `keeps answering when the gRPC service is down`() = FakeRecommendationServer().use { server ->
        server.stop()
        testApplication {
            api(server)
            assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/teams/$teamId/analysis").status)
            assertEquals(HttpStatusCode.OK, client.get("/teams/$teamId").status)
            assertEquals(HttpStatusCode.OK, client.get("/health/ready").status)
        }
    }
}
