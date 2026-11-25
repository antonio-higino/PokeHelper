package com.example.usecases

import com.example.domain.Page
import com.example.domain.Team
import com.example.domain.TeamRepository
import com.example.domain.UserRepository
import com.fasterxml.jackson.databind.JsonNode

class TeamService(
    private val teams: TeamRepository,
    private val users: UserRepository
) {
    suspend fun list(page: Int, size: Int, name: String?, userId: Long?): Page<Team> {
        require(page > 0) { "page must be greater than zero" }
        require(size in 1..100) { "size must be between 1 and 100" }
        require(name == null || name.isNotBlank()) { "name cannot be blank" }
        require(userId == null || userId > 0) { "userId must be a positive integer" }
        return teams.list(page, size, name, userId)
    }

    suspend fun findById(id: Long): Team? = teams.findById(id)

    suspend fun create(name: String, userId: Long, pokemons: List<JsonNode>): Team? {
        validate(name, pokemons)
        require(userId > 0) { "userId must be a positive integer" }
        if (users.findById(userId) == null) return null
        return teams.create(name, userId, pokemons)
    }

    suspend fun update(id: Long, name: String, pokemons: List<JsonNode>): Team? {
        validate(name, pokemons)
        val current = teams.findById(id) ?: return null
        return teams.update(current.copy(name = name, pokemons = pokemons))
    }

    suspend fun delete(id: Long): Boolean = teams.delete(id)

    private fun validate(name: String, pokemons: List<JsonNode>) {
        require(name.isNotBlank() && name.length <= 255) { "name must have 1 to 255 characters" }
        require(pokemons.size in 1..6) { "a team must contain between 1 and 6 pokemons" }
        val ids = pokemons.map { pokemon ->
            require(pokemon.isObject) { "each pokemon must be a JSON object" }
            val id = pokemon.get("id")
            require(id != null && id.isIntegralNumber && id.canConvertToInt() && id.intValue() > 0) {
                "each pokemon must have a positive integer id"
            }
            val pokemonName = pokemon.get("name")
            require(pokemonName != null && pokemonName.isTextual && pokemonName.textValue().isNotBlank()) {
                "each pokemon must have a name"
            }
            id.intValue()
        }
        require(ids.distinct().size == ids.size) { "a team cannot contain repeated pokemons" }
    }
}
