package com.pokehelper.domain

data class User(
    val id: Long,
    val username: String,
    val password: String
)

data class Pokemon(
    val id: Int,
    val name: String,
    val types: List<String>,
    val document: Map<String, Any?>
)

data class Team(
    val id: Long,
    val name: String,
    val userId: Long,
    val pokemons: List<Pokemon> = emptyList()
)
