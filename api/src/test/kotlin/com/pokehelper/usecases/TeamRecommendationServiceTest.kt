package com.pokehelper.usecases

import com.pokehelper.domain.Pokemon
import com.pokehelper.domain.PokemonCandidate
import com.pokehelper.domain.TeamAnalysis
import com.pokehelper.domain.TeamRecommender
import com.pokehelper.support.InMemoryTeamRepository
import com.pokehelper.support.pokemon
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamRecommendationServiceTest {
    private val teams = InMemoryTeamRepository()
    private val calls = mutableListOf<String>()
    private val service = TeamRecommendationService(teams, object : TeamRecommender {
        override suspend fun analyze(pokemons: List<Pokemon>): TeamAnalysis {
            calls += "analyze:${pokemons.map { it.id }}"
            return TeamAnalysis(emptyList(), emptyList())
        }

        override suspend fun recommendPokemon(pokemons: List<Pokemon>, type: String): List<PokemonCandidate> {
            calls += "recommend:$type"
            return emptyList()
        }
    })

    @Test
    fun `sends the saved team to the recommender, or returns null when it does not exist`() = runBlocking {
        val team = teams.create("Kanto", 1, listOf(pokemon(25, "pikachu", "electric")))
        service.analyze(team.id)
        service.recommendPokemon(team.id, " Ground ")
        assertNull(service.analyze(42))
        assertEquals(listOf("analyze:[25]", "recommend:ground"), calls)
    }
}
