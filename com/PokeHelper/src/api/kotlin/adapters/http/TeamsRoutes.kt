package com.example.adapters.http

import com.example.domain.Team
import com.example.usecases.TeamService
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

data class TeamRequest(val name: String, val userId: Long, val pokemons: List<JsonNode>)
data class TeamUpdateRequest(val name: String, val pokemons: List<JsonNode>)
data class TeamResponse(val id: Long, val name: String, val userId: Long, val pokemons: List<JsonNode>)
data class TeamPageResponse(val items: List<TeamResponse>, val page: Int, val size: Int, val total: Long)

internal fun Team.toResponse() = TeamResponse(id, name, userId, pokemons)

fun Route.teamRoutes(service: TeamService) {
    route("/teams") {
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull()
                ?: if (call.request.queryParameters["page"] == null) 1 else null
            val size = call.request.queryParameters["size"]?.toIntOrNull()
                ?: if (call.request.queryParameters["size"] == null) 20 else null
            val userIdParameter = call.request.queryParameters["userId"]
            val userId = userIdParameter?.toLongOrNull()
            if (page == null || size == null || (userIdParameter != null && userId == null)) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("page, size and userId must be integers"))
                return@get
            }
            try {
                val result = service.list(page, size, call.request.queryParameters["name"], userId)
                call.respond(TeamPageResponse(result.items.map(Team::toResponse), result.page, result.size, result.total))
            } catch (exception: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid query"))
            }
        }

        post {
            val request = try {
                call.receive<TeamRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid team body"))
                return@post
            }
            try {
                val team = service.create(request.name, request.userId, request.pokemons)
                if (team == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                } else {
                    call.response.headers.append(HttpHeaders.Location, "/teams/${team.id}")
                    call.respond(HttpStatusCode.Created, team.toResponse())
                }
            } catch (exception: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid team"))
            }
        }

        route("/{id}") {
            get {
                val id = call.teamId() ?: return@get
                val team = service.findById(id)
                if (team == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Team not found"))
                else call.respond(team.toResponse())
            }

            put {
                val id = call.teamId() ?: return@put
                val request = try {
                    call.receive<TeamUpdateRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid team body"))
                    return@put
                }
                try {
                    val team = service.update(id, request.name, request.pokemons)
                    if (team == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("Team not found"))
                    else call.respond(team.toResponse())
                } catch (exception: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid team"))
                }
            }

            delete {
                val id = call.teamId() ?: return@delete
                if (service.delete(id)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, ErrorResponse("Team not found"))
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.teamId(): Long? {
    val id = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) respond(HttpStatusCode.BadRequest, ErrorResponse("id must be a positive integer"))
    return id
}
