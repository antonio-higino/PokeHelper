package com.example.adapters.http

import com.example.domain.Team
import com.example.usecases.TeamService
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

data class TeamResponse(val id: Long, val name: String, val userId: Long, val pokemons: List<JsonNode>)
data class TeamPageResponse(val items: List<TeamResponse>, val page: Int, val size: Int, val total: Long)

internal fun Team.toResponse() = TeamResponse(id, name, userId, pokemons)

fun Route.teamRoutes(service: TeamService) {
    route("/teams") {
        get {
            val result = service.list(
                call.intQuery("page", 1), call.intQuery("size", 20),
                call.queryValue("name"), call.positiveLongQuery("userId")
            )
            call.respond(TeamPageResponse(result.items.map(Team::toResponse), result.page, result.size, result.total))
        }

        post {
            val request = call.jsonObject()
            val team = service.create(
                request.requiredString("name"), request.requiredPositiveLong("userId"),
                request.requiredArray("pokemons")
            ) ?: throw ResourceNotFoundException("User not found")
            call.response.headers.append(HttpHeaders.Location, "/teams/${team.id}")
            call.respond(HttpStatusCode.Created, team.toResponse())
        }

        route("/{id}") {
            get {
                val team = service.findById(call.pathId())
                    ?: throw ResourceNotFoundException("Team not found")
                call.respond(team.toResponse())
            }

            put {
                val id = call.pathId()
                val request = call.jsonObject()
                val team = service.update(id, request.requiredString("name"), request.requiredArray("pokemons"))
                    ?: throw ResourceNotFoundException("Team not found")
                call.respond(team.toResponse())
            }

            delete {
                if (!service.delete(call.pathId())) throw ResourceNotFoundException("Team not found")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
