package com.pokehelper.support

import com.pokehelper.domain.Page
import com.pokehelper.domain.Pokemon
import com.pokehelper.domain.Team
import com.pokehelper.domain.TeamRepository
import com.pokehelper.domain.User
import com.pokehelper.domain.UserRepository
import com.pokehelper.domain.UsernameAlreadyExists

class InMemoryUserRepository : UserRepository {
    private val users = linkedMapOf<Long, User>()
    private var nextId = 1L

    override suspend fun list(page: Int, size: Int, username: String?): Page<User> {
        val matching = users.values.filter { username == null || it.username == username }
        return Page(matching.drop((page - 1) * size).take(size), page, size, matching.size.toLong())
    }

    override suspend fun findById(id: Long) = users[id]
    override suspend fun findByUsername(username: String) = users.values.firstOrNull { it.username == username }

    override suspend fun create(username: String, password: String): User {
        if (findByUsername(username) != null) throw UsernameAlreadyExists()
        return User(nextId++, username, password).also { users[it.id] = it }
    }

    override suspend fun update(user: User): User? {
        if (user.id !in users) return null
        if (users.values.any { it.username == user.username && it.id != user.id }) throw UsernameAlreadyExists()
        users[user.id] = user
        return user
    }

    override suspend fun delete(id: Long) = users.remove(id) != null
}

class InMemoryTeamRepository : TeamRepository {
    private val teams = linkedMapOf<Long, Team>()
    private var nextId = 1L

    override suspend fun list(page: Int, size: Int, name: String?, userId: Long?): Page<Team> {
        val matching = teams.values.filter { (name == null || it.name == name) && (userId == null || it.userId == userId) }
        return Page(matching.drop((page - 1) * size).take(size), page, size, matching.size.toLong())
    }

    override suspend fun findById(id: Long) = teams[id]
    override suspend fun findByUserId(userId: Long) = teams.values.filter { it.userId == userId }

    override suspend fun create(name: String, userId: Long, pokemons: List<Pokemon>): Team =
        Team(nextId++, name, userId, pokemons).also { teams[it.id] = it }

    override suspend fun update(team: Team): Team? {
        if (team.id !in teams) return null
        teams[team.id] = team
        return team
    }

    override suspend fun delete(id: Long) = teams.remove(id) != null
}

fun pokemon(id: Int, name: String, vararg types: String) = Pokemon(
    id = id,
    name = name,
    types = types.toList(),
    document = mapOf(
        "id" to id,
        "name" to name,
        "types" to types.mapIndexed { index, type -> mapOf("slot" to index + 1, "type" to mapOf("name" to type)) }
    )
)
