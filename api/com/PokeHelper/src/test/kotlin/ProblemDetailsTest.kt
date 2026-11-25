package com.PokeHelper

import com.example.adapters.http.configureProblemDetails
import com.example.configureRouting
import com.example.domain.Page
import com.example.domain.Team
import com.example.domain.TeamRepository
import com.example.domain.User
import com.example.domain.UserRepository
import com.example.domain.UsernameAlreadyExists
import com.example.usecases.TeamService
import com.example.usecases.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProblemDetailsTest {
    private val json = ObjectMapper()

    @Test
    fun `invalid parameters and bodies return useful problem details`() = testApplication {
        application { configureTestApi() }

        val invalidPage = client.get("/users?page=wrong")
        assertProblem(invalidPage.status, invalidPage.headers[HttpHeaders.ContentType], invalidPage.bodyAsText(), 400, "page")

        val invalidId = client.get("/teams/0")
        assertProblem(invalidId.status, invalidId.headers[HttpHeaders.ContentType], invalidId.bodyAsText(), 400, "id")

        val missingUser = client.get("/users/1")
        assertProblem(missingUser.status, missingUser.headers[HttpHeaders.ContentType], missingUser.bodyAsText(), 404, "User not found")

        val missingRoute = client.get("/unknown")
        assertProblem(missingRoute.status, missingRoute.headers[HttpHeaders.ContentType], missingRoute.bodyAsText(), 404, "Resource not found")

        val invalidQuery = client.get("/teams?userId=-1")
        assertProblem(invalidQuery.status, invalidQuery.headers[HttpHeaders.ContentType], invalidQuery.bodyAsText(), 400, "userId")

        val missingField = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"password":"secret"}""")
        }
        assertProblem(missingField.status, missingField.headers[HttpHeaders.ContentType], missingField.bodyAsText(), 400, "username")

        val invalidOwner = client.post("/teams") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Kanto","userId":0,"pokemons":[{"id":25,"name":"pikachu"}]}""")
        }
        assertProblem(invalidOwner.status, invalidOwner.headers[HttpHeaders.ContentType], invalidOwner.bodyAsText(), 400, "userId")

        val invalidPokemon = client.post("/teams") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Kanto","userId":1,"pokemons":[{"id":0,"name":"pikachu"}]}""")
        }
        assertProblem(invalidPokemon.status, invalidPokemon.headers[HttpHeaders.ContentType], invalidPokemon.bodyAsText(), 400, "pokemon")

        val malformed = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }
        assertProblem(malformed.status, malformed.headers[HttpHeaders.ContentType], malformed.bodyAsText(), 400, "JSON")
    }

    @Test
    fun `database integrity violations return conflict problem details`() = testApplication {
        application { configureTestApi() }

        val duplicate = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"duplicate","password":"secret"}""")
        }
        assertProblem(duplicate.status, duplicate.headers[HttpHeaders.ContentType], duplicate.bodyAsText(), 409, "Username")

        val response = client.post("/users") {
            contentType(ContentType.Application.Json)
            setBody("""{"username":"conflict","password":"secret"}""")
        }
        assertProblem(response.status, response.headers[HttpHeaders.ContentType], response.bodyAsText(), 409, "constraint")
    }

    private fun assertProblem(status: HttpStatusCode, contentType: String?, body: String, expected: Int, detail: String) {
        assertEquals(expected, status.value)
        assertEquals("application/problem+json", contentType?.substringBefore(';'))
        val problem = json.readTree(body)
        assertEquals("about:blank", problem["type"].asText())
        assertEquals(expected, problem["status"].asInt())
        assertTrue(problem["title"].asText().isNotBlank())
        assertTrue(problem["detail"].asText().contains(detail, ignoreCase = true))
    }
}

internal fun io.ktor.server.application.Application.configureTestApi() {
    val users = object : UserRepository {
        override suspend fun list(page: Int, size: Int, username: String?) = Page(emptyList<User>(), page, size, 0)
        override suspend fun findById(id: Long): User? = null
        override suspend fun findByUsername(username: String): User? = null
        override suspend fun create(username: String, password: String): User =
            if (username == "duplicate") throw UsernameAlreadyExists()
            else throw IllegalStateException("database write failed", SQLException("foreign key violation", "23503"))
        override suspend fun update(user: User): User? = null
        override suspend fun delete(id: Long): Boolean = false
    }
    val teams = object : TeamRepository {
        override suspend fun list(page: Int, size: Int, name: String?, userId: Long?) = Page(emptyList<Team>(), page, size, 0)
        override suspend fun findById(id: Long): Team? = null
        override suspend fun findByUserId(userId: Long): List<Team> = emptyList()
        override suspend fun create(name: String, userId: Long, pokemons: List<com.fasterxml.jackson.databind.JsonNode>): Team =
            error("Unexpected database write")
        override suspend fun update(team: Team): Team? = null
        override suspend fun delete(id: Long): Boolean = false
    }
    install(ContentNegotiation) { jackson() }
    configureProblemDetails()
    configureRouting(UserService(users, teams), TeamService(teams, users))
}
