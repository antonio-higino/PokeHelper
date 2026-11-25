package com.example.usecases

import com.example.domain.Page
import com.example.domain.Team
import com.example.domain.TeamRepository
import com.example.domain.User
import com.example.domain.UserRepository

class UserService(
    private val users: UserRepository,
    private val teams: TeamRepository
) {
    suspend fun list(page: Int, size: Int, username: String?): Page<User> {
        require(page > 0) { "page must be greater than zero" }
        require(size in 1..100) { "size must be between 1 and 100" }
        require(username == null || username.isNotBlank()) { "username cannot be blank" }
        return users.list(page, size, username)
    }

    suspend fun findById(id: Long): User? = users.findById(id)

    suspend fun create(username: String, password: String): User {
        validate(username, password)
        return users.create(username, password)
    }

    suspend fun update(id: Long, username: String, password: String): User? {
        validate(username, password)
        return users.update(User(id, username, password))
    }

    suspend fun delete(id: Long): Boolean = users.delete(id)

    suspend fun listTeams(id: Long): List<Team>? {
        if (users.findById(id) == null) return null
        return teams.findByUserId(id)
    }

    private fun validate(username: String, password: String) {
        require(username.isNotBlank() && username.length <= 255) { "username must have 1 to 255 characters" }
        require(password.isNotBlank() && password.length <= 255) { "password must have 1 to 255 characters" }
    }
}
