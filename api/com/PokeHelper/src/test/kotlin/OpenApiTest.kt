package com.PokeHelper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenApiTest {
    private val json = ObjectMapper()

    @Test
    fun `generated OpenAPI covers every API route and Swagger UI loads it`() = testApplication {
        application { configureTestApi() }

        val specResponse = client.get("/swagger/openapi.json")
        assertEquals(HttpStatusCode.OK, specResponse.status)
        val spec = json.readTree(specResponse.bodyAsText())
        assertTrue(spec["openapi"].asText().startsWith("3."))
        assertEquals("PokeHelper API", spec["info"]["title"].asText())

        val paths = spec["paths"]
        assertEquals(setOf("/", "/users", "/users/{id}", "/users/{id}/teams", "/teams", "/teams/{id}"),
            paths.fieldNames().asSequence().toSet())
        val methods = mapOf(
            "/" to setOf("get"),
            "/users" to setOf("get", "post"),
            "/users/{id}" to setOf("get", "put", "delete"),
            "/users/{id}/teams" to setOf("get"),
            "/teams" to setOf("get", "post"),
            "/teams/{id}" to setOf("get", "put", "delete")
        )
        for ((path, expected) in methods) {
            assertEquals(expected, paths[path].fieldNames().asSequence().toSet(), path)
            for (method in expected) {
                val operation = paths[path][method]
                assertTrue(operation["summary"].asText().isNotBlank(), "$method $path needs a summary")
                val responses = operation["responses"]
                val success = if (method == "post") "201" else if (method == "delete") "204" else "200"
                assertNotNull(responses[success], "$method $path needs a success response")
                if (method != "delete") assertNotNull(responses[success]["content"], "$method $path needs a response body")
                if (path != "/") assertProblemSchema(spec, responses["400"], "$method $path 400")
                assertProblemSchema(spec, responses["500"], "$method $path 500")
                if ("{id}" in path || (path == "/teams" && method == "post")) {
                    assertProblemSchema(spec, responses["404"], "$method $path 404")
                }
                if (method == "post" || method == "put" || method == "delete") {
                    assertProblemSchema(spec, responses["409"], "$method $path 409")
                }
                if (method == "post" || method == "put") {
                    assertTrue(operation["requestBody"]["required"].asBoolean(), "$method $path needs a required body")
                }
            }
        }

        assertParameter(paths["/users"]["get"], "page", "query", "integer")
        assertParameter(paths["/users"]["get"], "username", "query", "string")
        assertParameter(paths["/teams"]["get"], "userId", "query", "integer")
        assertParameter(paths["/teams/{id}"]["get"], "id", "path", "integer")

        val userBody = paths["/users"]["post"]["requestBody"]
        assertTrue(userBody["required"].asBoolean())
        val requestSchema = resolveSchema(spec, userBody["content"]["application/json"]["schema"])
        assertNotNull(requestSchema["properties"]["username"])
        assertNotNull(requestSchema["properties"]["password"])
        assertEquals(255, requestSchema["properties"]["username"]["maxLength"].asInt())
        assertEquals(255, requestSchema["properties"]["password"]["maxLength"].asInt())

        val teamBody = resolveSchema(spec, paths["/teams"]["post"]["requestBody"]["content"]["application/json"]["schema"])
        assertEquals("integer", teamBody["properties"]["userId"]["type"].asText())
        val pokemonItems = resolveSchema(spec, teamBody["properties"]["pokemons"]["items"])
        assertEquals("object", pokemonItems.path("type").asText(), pokemonItems.toPrettyString())
        assertEquals("integer", pokemonItems.path("properties").path("id").path("type").asText(), pokemonItems.toPrettyString())
        assertEquals("string", pokemonItems.path("properties").path("name").path("type").asText(), pokemonItems.toPrettyString())
        assertEquals(1.0, pokemonItems["properties"]["id"]["minimum"].asDouble())
        assertTrue(pokemonItems["additionalProperties"].asBoolean())
        assertEquals(1, teamBody["properties"]["pokemons"]["minItems"].asInt())
        assertEquals(6, teamBody["properties"]["pokemons"]["maxItems"].asInt())

        val teamResponse = resolveSchema(spec, paths["/teams/{id}"]["get"]["responses"]["200"]["content"]["application/json"]["schema"])
        assertEquals("object", resolveSchema(spec, teamResponse["properties"]["pokemons"]["items"])["type"].asText())
        val teamUpdate = resolveSchema(spec, paths["/teams/{id}"]["put"]["requestBody"]["content"]["application/json"]["schema"])
        assertEquals("object", resolveSchema(spec, teamUpdate["properties"]["pokemons"]["items"])["type"].asText())
        val userTeams = paths["/users/{id}/teams"]["get"]["responses"]["200"]["content"]["application/json"]["schema"]
        assertEquals("array", userTeams["type"].asText())

        for ((path, method) in listOf("/users" to "post", "/teams" to "post")) {
            assertProblemSchema(spec, paths[path][method]["responses"]["400"], "$method $path 400")
        }

        val ui = client.get("/swagger")
        assertEquals(HttpStatusCode.OK, ui.status)
        assertTrue(ui.bodyAsText().contains("/swagger/openapi.json"))
    }

    private fun assertParameter(operation: JsonNode, name: String, location: String, type: String) {
        val parameter = operation["parameters"].firstOrNull { it["name"].asText() == name }
        assertNotNull(parameter)
        assertEquals(location, parameter["in"].asText())
        assertEquals(type, parameter["schema"]["type"].asText())
    }

    private fun resolveSchema(spec: JsonNode, schema: JsonNode): JsonNode {
        val reference = schema.path("\$ref").asText()
        return if (reference.isEmpty()) schema else spec["components"]["schemas"][reference.substringAfterLast('/')]
    }

    private fun assertProblemSchema(spec: JsonNode, response: JsonNode?, label: String) {
        assertNotNull(response, "$label response missing")
        val content = response.path("content").path("application/problem+json")
        assertTrue(!content.isMissingNode, "$label must use application/problem+json")
        val schema = resolveSchema(spec, content["schema"])
        for (field in listOf("type", "title", "status", "detail")) {
            assertNotNull(schema["properties"][field], "$label missing $field")
        }
    }
}
