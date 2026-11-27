package com.pokehelper.domain

data class TypeCoverage(
    val attackingType: String,
    val immune: Int,
    val veryResistant: Int,
    val resistant: Int,
    val neutral: Int,
    val weak: Int,
    val veryWeak: Int
)

data class TypeScore(val type: String, val score: Int)

data class TeamAnalysis(
    val coverage: List<TypeCoverage>,
    val recommendedTypes: List<TypeScore>
)

data class PokemonCandidate(
    val id: Int,
    val name: String,
    val types: List<String>,
    val baseStatTotal: Int,
    val combinationRank: Int,
    val spriteUrl: String?
)

interface TeamRecommender {
    suspend fun analyze(pokemons: List<Pokemon>): TeamAnalysis
    suspend fun recommendPokemon(pokemons: List<Pokemon>, type: String): List<PokemonCandidate>
}
