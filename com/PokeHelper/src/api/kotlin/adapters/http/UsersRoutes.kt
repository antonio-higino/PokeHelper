package com.example.adapters.http

import com.example.domain.Team
import com.example.domain.User
import com.example.usecases.UserService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

data class UserResponse(val id: Long, val username: String)
data class UserPageResponse(val items: List<UserResponse>, val page: Int, val size: Int, val total: Long)

private fun User.toResponse() = UserResponse(id, username)

fun Route.userRoutes(service: UserService) {
    route("/users") {
        get {
            val result = service.list(call.intQuery("page", 1), call.intQuery("size", 20), call.queryValue("username"))
            call.respond(UserPageResponse(result.items.map(User::toResponse), result.page, result.size, result.total))
        }

        post {
            val request = call.jsonObject()
            val user = service.create(request.requiredString("username"), request.requiredString("password"))
            call.response.headers.append(HttpHeaders.Location, "/users/${user.id}")
            call.respond(HttpStatusCode.Created, user.toResponse())
        }

        route("/{id}") {
            get {
                val id = call.pathId()
                val user = service.findById(id)
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(user.toResponse())
            }

            put {
                val id = call.pathId()
                val request = call.jsonObject()
                val user = service.update(id, request.requiredString("username"), request.requiredString("password"))
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(user.toResponse())
            }

            delete {
                if (!service.delete(call.pathId())) throw ResourceNotFoundException("User not found")
                call.respond(HttpStatusCode.NoContent)
            }

            get("/teams") {
                val teams = service.listTeams(call.pathId())
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(teams.map(Team::toResponse))
            }
        }
    }
}
