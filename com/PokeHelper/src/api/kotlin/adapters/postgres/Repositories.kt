package com.example.adapters.postgres

import com.example.domain.Team
import com.example.domain.TeamRepository
import com.example.domain.User
import com.example.domain.UserRepository
import com.example.domain.Page
import com.example.domain.UsernameAlreadyExists
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException

internal object UsersTable : LongIdTable("users") {
    val username = varchar("username", 255)
    val password = varchar("password", 255)
}

internal object TeamsTable : LongIdTable("teams") {
    val name = varchar("name", 255)
    val userId = long("user_id")
    val pokemons = text("pokemons")
}

private val json = ObjectMapper()

private fun ResultRow.toUser() = User(
    id = this[UsersTable.id].value,
    username = this[UsersTable.username],
    password = this[UsersTable.password]
)

private fun ResultRow.toTeam() = Team(
    id = this[TeamsTable.id].value,
    name = this[TeamsTable.name],
    userId = this[TeamsTable.userId],
    pokemons = json.readTree(this[TeamsTable.pokemons]).toList()
)

class ExposedUserRepository(private val database: Database) : UserRepository {
    override suspend fun list(page: Int, size: Int, username: String?): Page<User> =
        newSuspendedTransaction(Dispatchers.IO, db = database) {
            val filter = username?.let { UsersTable.username eq it }
            val countQuery = UsersTable.selectAll()
            if (filter != null) countQuery.where { filter }
            val total = countQuery.count()

            val pageQuery = UsersTable.selectAll()
            if (filter != null) pageQuery.where { filter }
            val items = pageQuery
                .orderBy(UsersTable.id to SortOrder.ASC)
                .limit(size)
                .offset((page.toLong() - 1) * size)
                .map(ResultRow::toUser)
            Page(items, page, size, total)
        }

    override suspend fun findById(id: Long): User? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        UsersTable.selectAll()
            .where { UsersTable.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun findByUsername(username: String): User? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        UsersTable.selectAll()
            .where { UsersTable.username eq username }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun create(username: String, password: String): User = translateUniqueViolation {
        newSuspendedTransaction(Dispatchers.IO, db = database) {
            val id = UsersTable.insertAndGetId {
                it[UsersTable.username] = username
                it[UsersTable.password] = password
            }.value
            User(id = id, username = username, password = password)
        }
    }

    override suspend fun update(user: User): User? = translateUniqueViolation {
        newSuspendedTransaction(Dispatchers.IO, db = database) {
            val updated = UsersTable.update({ UsersTable.id eq user.id }) {
                it[username] = user.username
                it[password] = user.password
            }
            if (updated == 0) null else user
        }
    }

    override suspend fun delete(id: Long): Boolean = newSuspendedTransaction(Dispatchers.IO, db = database) {
        UsersTable.deleteWhere { UsersTable.id eq id } > 0
    }
}

class ExposedTeamRepository(private val database: Database) : TeamRepository {
    override suspend fun list(page: Int, size: Int, name: String?, userId: Long?): Page<Team> =
        newSuspendedTransaction(Dispatchers.IO, db = database) {
            fun query() = TeamsTable.selectAll().also { teams ->
                when {
                    name != null && userId != null -> teams.where {
                        (TeamsTable.name eq name) and (TeamsTable.userId eq userId)
                    }
                    name != null -> teams.where { TeamsTable.name eq name }
                    userId != null -> teams.where { TeamsTable.userId eq userId }
                }
            }

            val total = query().count()
            val items = query()
                .orderBy(TeamsTable.id to SortOrder.ASC)
                .limit(size)
                .offset((page.toLong() - 1) * size)
                .map(ResultRow::toTeam)
            Page(items, page, size, total)
        }

    override suspend fun findById(id: Long): Team? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        TeamsTable.selectAll()
            .where { TeamsTable.id eq id }
            .singleOrNull()
            ?.toTeam()
    }

    override suspend fun findByUserId(userId: Long): List<Team> = newSuspendedTransaction(Dispatchers.IO, db = database) {
        TeamsTable.selectAll()
            .where { TeamsTable.userId eq userId }
            .map(ResultRow::toTeam)
    }

    override suspend fun create(name: String, userId: Long, pokemons: List<JsonNode>): Team = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val serializedPokemons = json.writeValueAsString(pokemons)
        val id = TeamsTable.insertAndGetId {
            it[TeamsTable.name] = name
            it[TeamsTable.userId] = userId
            it[TeamsTable.pokemons] = serializedPokemons
        }.value
        Team(id = id, name = name, userId = userId, pokemons = pokemons)
    }

    override suspend fun update(team: Team): Team? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val updated = TeamsTable.update({ TeamsTable.id eq team.id }) {
            it[name] = team.name
            it[userId] = team.userId
            it[pokemons] = json.writeValueAsString(team.pokemons)
        }
        if (updated == 0) null else team
    }

    override suspend fun delete(id: Long): Boolean = newSuspendedTransaction(Dispatchers.IO, db = database) {
        TeamsTable.deleteWhere { TeamsTable.id eq id } > 0
    }
}

private suspend fun <T> translateUniqueViolation(block: suspend () -> T): T = try {
    block()
} catch (exception: SQLException) {
    if (exception.sqlState == "23505") throw UsernameAlreadyExists()
    throw exception
}
