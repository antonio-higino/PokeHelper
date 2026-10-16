package com.example.adapters.postgres

import com.example.domain.Team
import com.example.domain.TeamRepository
import com.example.domain.User
import com.example.domain.UserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

internal object UsersTable : LongIdTable("users") {
    val username = varchar("username", 255)
    val password = varchar("password", 255)
}

internal object TeamsTable : LongIdTable("teams") {
    val name = varchar("name", 255)
    val userId = long("user_id")
}

private fun ResultRow.toUser() = User(
    id = this[UsersTable.id].value,
    username = this[UsersTable.username],
    password = this[UsersTable.password]
)

private fun ResultRow.toTeam() = Team(
    id = this[TeamsTable.id].value,
    name = this[TeamsTable.name],
    userId = this[TeamsTable.userId]
)

class ExposedUserRepository(private val database: Database) : UserRepository {
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

    override suspend fun create(username: String, password: String): User = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val id = UsersTable.insertAndGetId {
            it[UsersTable.username] = username
            it[UsersTable.password] = password
        }.value
        User(id = id, username = username, password = password)
    }

    override suspend fun update(user: User): User? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val updated = UsersTable.update({ UsersTable.id eq user.id }) {
            it[username] = user.username
            it[password] = user.password
        }
        if (updated == 0) null else user
    }

    override suspend fun delete(id: Long): Boolean = newSuspendedTransaction(Dispatchers.IO, db = database) {
        UsersTable.deleteWhere { UsersTable.id eq id } > 0
    }
}

class ExposedTeamRepository(private val database: Database) : TeamRepository {
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

    override suspend fun create(name: String, userId: Long): Team = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val id = TeamsTable.insertAndGetId {
            it[TeamsTable.name] = name
            it[TeamsTable.userId] = userId
        }.value
        Team(id = id, name = name, userId = userId)
    }

    override suspend fun update(team: Team): Team? = newSuspendedTransaction(Dispatchers.IO, db = database) {
        val updated = TeamsTable.update({ TeamsTable.id eq team.id }) {
            it[name] = team.name
            it[userId] = team.userId
        }
        if (updated == 0) null else team
    }

    override suspend fun delete(id: Long): Boolean = newSuspendedTransaction(Dispatchers.IO, db = database) {
        TeamsTable.deleteWhere { TeamsTable.id eq id } > 0
    }
}