package com.example.domain

interface UserRepository {
    suspend fun findById(id: Long): User?
    suspend fun findByUsername(username: String): User?
    suspend fun create(username: String, password: String): User
    suspend fun update(user: User): User?
    suspend fun delete(id: Long): Boolean
}

interface TeamRepository {
    suspend fun findById(id: Long): Team?
    suspend fun findByUserId(userId: Long): List<Team>
    suspend fun create(name: String, userId: Long): Team
    suspend fun update(team: Team): Team?
    suspend fun delete(id: Long): Boolean
}