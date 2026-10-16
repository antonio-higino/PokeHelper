package com.example.adapters.http

import com.example.domain.Team
import com.example.domain.User
import com.example.domain.UsernameAlreadyExists
import com.example.usecases.UserService
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

data class UserRequest(val username: String, val password: String)
data class UserResponse(val id: Long, val username: String)
data class UserPageResponse(val items: List<UserResponse>, val page: Int, val size: Int, val total: Long)
data class ErrorResponse(val error: String)

private fun User.toResponse() = UserResponse(id, username)

fun Route.userRoutes(service: UserService) {
    route("/users") {
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: if (call.request.queryParameters["page"] == null) 1 else null
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: if (call.request.queryParameters["size"] == null) 20 else null
            if (page == null || size == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("page and size must be integers"))
                return@get
            }
            try {
                val result = service.list(page, size, call.request.queryParameters["username"])
                call.respond(UserPageResponse(result.items.map(User::toResponse), result.page, result.size, result.total))
            } catch (exception: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid query"))
            }
        }

        post {
            val request = try {
                call.receive<UserRequest>()
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user body"))
                return@post
            }
            try {
                val user = service.create(request.username, request.password)
                call.response.headers.append(HttpHeaders.Location, "/users/${user.id}")
                call.respond(HttpStatusCode.Created, user.toResponse())
            } catch (exception: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid user"))
            } catch (_: UsernameAlreadyExists) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("Username already exists"))
            }
        }

        route("/{id}") {
            get {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be a positive integer"))
                    return@get
                }
                val user = service.findById(id)
                if (user == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                else call.respond(user.toResponse())
            }

            put {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be a positive integer"))
                    return@put
                }
                val request = try {
                    call.receive<UserRequest>()
                } catch (_: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid user body"))
                    return@put
                }
                try {
                    val user = service.update(id, request.username, request.password)
                    if (user == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                    else call.respond(user.toResponse())
                } catch (exception: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(exception.message ?: "Invalid user"))
                } catch (_: UsernameAlreadyExists) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse("Username already exists"))
                }
            }

            delete {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be a positive integer"))
                    return@delete
                }
                if (service.delete(id)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
            }

            get("/teams") {
                val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("id must be a positive integer"))
                    return@get
                }
                val teams = service.listTeams(id)
                if (teams == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                else call.respond(teams.map(Team::toResponse))
            }
        }
    }
}
