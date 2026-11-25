package com.example.domain

import com.fasterxml.jackson.databind.JsonNode

data class User(
    val id: Long,
    val username: String,
    val password: String
)

data class Team(
    val id: Long,
    val name: String,
    val userId: Long,
    val pokemons: List<JsonNode> = emptyList()
)
