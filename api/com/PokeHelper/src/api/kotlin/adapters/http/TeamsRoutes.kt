package com.example.adapters.http

import com.example.domain.Team
import com.example.usecases.TeamService
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

@JsonSchema.AdditionalPropertiesAllowed
data class PokemonDocument(
    @JsonSchema.Minimum(1.0) val id: Int,
    @JsonSchema.MinLength(1) val name: String
)

data class TeamRequest(
    @JsonSchema.MinLength(1) @JsonSchema.MaxLength(255) val name: String,
    @JsonSchema.Minimum(1.0) val userId: Long,
    @JsonSchema.ItemsRef(PokemonDocument::class)
    @JsonSchema.MinItems(1) @JsonSchema.MaxItems(6)
    val pokemons: List<JsonNode>
)
data class TeamUpdateRequest(
    @JsonSchema.MinLength(1) @JsonSchema.MaxLength(255) val name: String,
    @JsonSchema.ItemsRef(PokemonDocument::class)
    @JsonSchema.MinItems(1) @JsonSchema.MaxItems(6)
    val pokemons: List<JsonNode>
)
data class TeamResponse(
    val id: Long,
    val name: String,
    val userId: Long,
    @JsonSchema.ItemsRef(PokemonDocument::class) val pokemons: List<JsonNode>
)
data class TeamPageResponse(val items: List<TeamResponse>, val page: Int, val size: Int, val total: Long)

internal fun Team.toResponse() = TeamResponse(id, name, userId, pokemons)

@OptIn(ExperimentalKtorApi::class)
fun Route.teamRoutes(service: TeamService) {
    route("/teams") {
        get {
            val result = service.list(
                call.intQuery("page", 1), call.intQuery("size", 20),
                call.queryValue("name"), call.positiveLongQuery("userId")
            )
            call.respond(TeamPageResponse(result.items.map(Team::toResponse), result.page, result.size, result.total))
        }.describe {
            summary = "List teams"
            parameters {
                pagination()
                query("name") {
                    description = "Optional exact team-name filter"
                    schema = jsonSchema<String>()
                }
                query("userId") {
                    description = "Optional positive user ID filter"
                    schema = jsonSchema<Long>().copy(format = "int64", minimum = 1.0)
                }
            }
            responses {
                HttpStatusCode.OK { description = "Team page"; schema = jsonSchema<TeamPageResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid query parameter")
                serverError()
            }
        }

        post {
            val body = call.jsonObject()
            val request = TeamRequest(
                body.requiredString("name"), body.requiredPositiveLong("userId"), body.requiredArray("pokemons")
            )
            val team = service.create(
                request.name, request.userId, request.pokemons
            ) ?: throw ResourceNotFoundException("User not found")
            call.response.headers.append(HttpHeaders.Location, "/teams/${team.id}")
            call.respond(HttpStatusCode.Created, team.toResponse())
        }.describe {
            summary = "Create a team"
            description = "Each team contains 1 to 6 Pokémon objects with a positive integer id and nonempty name. Other Pokémon fields are preserved."
            requestBody { required = true; schema = jsonSchema<TeamRequest>() }
            responses {
                HttpStatusCode.Created { description = "Team created; Location header points to it"; schema = jsonSchema<TeamResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid JSON body or team fields")
                problem(HttpStatusCode.NotFound, "Owner user not found")
                problem(HttpStatusCode.Conflict, "Database constraint conflict")
                serverError()
            }
        }

        route("/{id}") {
            get {
                val team = service.findById(call.pathId())
                    ?: throw ResourceNotFoundException("Team not found")
                call.respond(team.toResponse())
            }.describe {
                summary = "Get a team"
                parameters { idPath() }
                responses {
                    HttpStatusCode.OK { description = "Team"; schema = jsonSchema<TeamResponse>() }
                    problem(HttpStatusCode.BadRequest, "Invalid team ID")
                    problem(HttpStatusCode.NotFound, "Team not found")
                    serverError()
                }
            }

            put {
                val id = call.pathId()
                val body = call.jsonObject()
                val request = TeamUpdateRequest(body.requiredString("name"), body.requiredArray("pokemons"))
                val team = service.update(id, request.name, request.pokemons)
                    ?: throw ResourceNotFoundException("Team not found")
                call.respond(team.toResponse())
            }.describe {
                summary = "Update a team"
                parameters { idPath() }
                requestBody { required = true; schema = jsonSchema<TeamUpdateRequest>() }
                responses {
                    HttpStatusCode.OK { description = "Updated team"; schema = jsonSchema<TeamResponse>() }
                    problem(HttpStatusCode.BadRequest, "Invalid team ID or JSON body")
                    problem(HttpStatusCode.NotFound, "Team not found")
                    problem(HttpStatusCode.Conflict, "Database constraint conflict")
                    serverError()
                }
            }

            delete {
                if (!service.delete(call.pathId())) throw ResourceNotFoundException("Team not found")
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                summary = "Delete a team"
                parameters { idPath() }
                responses {
                    HttpStatusCode.NoContent { description = "Team deleted" }
                    problem(HttpStatusCode.BadRequest, "Invalid team ID")
                    problem(HttpStatusCode.NotFound, "Team not found")
                    problem(HttpStatusCode.Conflict, "Database constraint conflict")
                    serverError()
                }
            }
        }
    }
}
