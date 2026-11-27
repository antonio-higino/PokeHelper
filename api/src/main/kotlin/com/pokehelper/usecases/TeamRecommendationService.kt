package com.pokehelper.usecases

import com.pokehelper.domain.PokemonCandidate
import com.pokehelper.domain.TeamAnalysis
import com.pokehelper.domain.TeamRecommender
import com.pokehelper.domain.TeamRepository

class TeamRecommendationService(
    private val teams: TeamRepository,
    private val recommender: TeamRecommender
) {
    suspend fun analyze(teamId: Long): TeamAnalysis? {
        val team = teams.findById(teamId) ?: return null
        return recommender.analyze(team.pokemons)
    }

    suspend fun recommendPokemon(teamId: Long, type: String): List<PokemonCandidate>? {
        val normalized = type.trim().lowercase()
        require(normalized.matches(TYPE_NAME)) { "type must contain only letters" }
        val team = teams.findById(teamId) ?: return null
        return recommender.recommendPokemon(team.pokemons, normalized)
    }

    private companion object {
        val TYPE_NAME = Regex("[a-z]+")
    }
}
