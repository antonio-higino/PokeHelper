package com.PokeHelper

import com.example.adapters.postgres.ExposedTeamRepository
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ServerTest {
    private val json = ObjectMapper()

    @Test
    fun `test root endpoint`() = testApplication {
        // loads default configuration
        configure()
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    @Test
    fun `users CRUD respects pagination filtering relationship and HTTP statuses`() = testApplication {
        configure()
        val username = "api-test-${UUID.randomUUID()}"
        val renamedUsername = "renamed-$username"
        val otherUsername = "other-test-${UUID.randomUUID()}"
        val request = json.writeValueAsString(mapOf("username" to username, "password" to "secret"))
        val otherRequest = json.writeValueAsString(mapOf("username" to otherUsername, "password" to "secret"))
        var id: Long? = null
        var otherId: Long? = null

        try {
            val created = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.Created, created.status)
            id = json.readTree(created.bodyAsText())["id"].asLong()
            assertEquals("/users/$id", created.headers["Location"])
            assertFalse(created.bodyAsText().contains("password"))

            val createdOther = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(otherRequest)
            }
            assertEquals(HttpStatusCode.Created, createdOther.status)
            otherId = json.readTree(createdOther.bodyAsText())["id"].asLong()

            val duplicate = client.post("/users") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.Conflict, duplicate.status)

            val firstPage = client.get("/users?page=1&size=1&username=$username")
            assertEquals(HttpStatusCode.OK, firstPage.status)
            val page = json.readTree(firstPage.bodyAsText())
            assertEquals(1, page["items"].size())
            assertEquals(id, page["items"][0]["id"].asLong())
            assertEquals(1L, page["total"].asLong())
            assertFalse(firstPage.bodyAsText().contains("password"))
            val secondPage = json.readTree(client.get("/users?page=2&size=1&username=$username").bodyAsText())
            assertEquals(0, secondPage["items"].size())
            assertEquals(HttpStatusCode.BadRequest, client.get("/users?size=101").status)

            assertEquals(HttpStatusCode.OK, client.get("/users/$id").status)
            assertEquals(HttpStatusCode.BadRequest, client.get("/users/0").status)

            val db = Database.connect(
                System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/app_db",
                driver = "org.postgresql.Driver",
                user = System.getenv("DATABASE_USER") ?: "admin",
                password = System.getenv("DATABASE_PASSWORD") ?: "admin"
            )
            val team = ExposedTeamRepository(db).create("API test team", id)
            val teams = client.get("/users/$id/teams")
            assertEquals(HttpStatusCode.OK, teams.status)
            assertEquals(team.id, json.readTree(teams.bodyAsText())[0]["id"].asLong())

            val conflictingUpdate = client.put("/users/$otherId") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.Conflict, conflictingUpdate.status)

            val updated = client.put("/users/$id") {
                contentType(ContentType.Application.Json)
                setBody(json.writeValueAsString(mapOf("username" to renamedUsername, "password" to "new-secret")))
            }
            assertEquals(HttpStatusCode.OK, updated.status)
            assertFalse(updated.bodyAsText().contains("password"))
            assertEquals(renamedUsername, json.readTree(client.get("/users/$id").bodyAsText())["username"].asText())
            assertEquals(0L, json.readTree(client.get("/users?username=$username").bodyAsText())["total"].asLong())

            assertEquals(HttpStatusCode.NoContent, client.delete("/users/$id").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/users/$id").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/users/$id/teams").status)
            assertEquals(HttpStatusCode.NotFound, client.delete("/users/$id").status)
        } finally {
            if (id != null) client.delete("/users/$id")
            if (otherId != null) client.delete("/users/$otherId")
        }
    }

}
