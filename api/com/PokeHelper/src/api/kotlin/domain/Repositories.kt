package com.example.domain

import com.fasterxml.jackson.databind.JsonNode

interface UserRepository {
    suspend fun list(page: Int, size: Int, username: String?): Page<User>
    suspend fun findById(id: Long): User?
    suspend fun findByUsername(username: String): User?
    suspend fun create(username: String, password: String): User
    suspend fun update(user: User): User?
    suspend fun delete(id: Long): Boolean
}

interface TeamRepository {
    suspend fun list(page: Int, size: Int, name: String?, userId: Long?): Page<Team>
    suspend fun findById(id: Long): Team?
    suspend fun findByUserId(userId: Long): List<Team>
    suspend fun create(name: String, userId: Long, pokemons: List<JsonNode> = emptyList()): Team
    suspend fun update(team: Team): Team?
    suspend fun delete(id: Long): Boolean
}

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val total: Long
)

class UsernameAlreadyExists : RuntimeException("Username already exists")
