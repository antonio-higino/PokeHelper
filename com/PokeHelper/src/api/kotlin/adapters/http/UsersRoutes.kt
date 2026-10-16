package com.example.adapters.http

import com.example.domain.Team
import com.example.domain.User
import com.example.usecases.UserService
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

data class UserRequest(
    @JsonSchema.MinLength(1) @JsonSchema.MaxLength(255) val username: String,
    @JsonSchema.MinLength(1) @JsonSchema.MaxLength(255) val password: String
)
data class UserResponse(val id: Long, val username: String)
data class UserPageResponse(val items: List<UserResponse>, val page: Int, val size: Int, val total: Long)

private fun User.toResponse() = UserResponse(id, username)

@OptIn(ExperimentalKtorApi::class)
fun Route.userRoutes(service: UserService) {
    route("/users") {
        get {
            val result = service.list(call.intQuery("page", 1), call.intQuery("size", 20), call.queryValue("username"))
            call.respond(UserPageResponse(result.items.map(User::toResponse), result.page, result.size, result.total))
        }.describe {
            summary = "List users"
            parameters {
                pagination()
                query("username") {
                    description = "Optional exact username filter"
                    schema = jsonSchema<String>()
                }
            }
            responses {
                HttpStatusCode.OK { description = "User page"; schema = jsonSchema<UserPageResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid query parameter")
                serverError()
            }
        }

        post {
            val body = call.jsonObject()
            val request = UserRequest(body.requiredString("username"), body.requiredString("password"))
            val user = service.create(request.username, request.password)
            call.response.headers.append(HttpHeaders.Location, "/users/${user.id}")
            call.respond(HttpStatusCode.Created, user.toResponse())
        }.describe {
            summary = "Create a user"
            requestBody { required = true; schema = jsonSchema<UserRequest>() }
            responses {
                HttpStatusCode.Created { description = "User created; Location header points to it"; schema = jsonSchema<UserResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid JSON body or user fields")
                problem(HttpStatusCode.Conflict, "Username already exists or database constraint conflict")
                serverError()
            }
        }

        route("/{id}") {
            get {
                val id = call.pathId()
                val user = service.findById(id)
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(user.toResponse())
            }.describe {
                summary = "Get a user"
                parameters { idPath() }
                responses {
                    HttpStatusCode.OK { description = "User"; schema = jsonSchema<UserResponse>() }
                    problem(HttpStatusCode.BadRequest, "Invalid user ID")
                    problem(HttpStatusCode.NotFound, "User not found")
                    serverError()
                }
            }

            put {
                val id = call.pathId()
                val body = call.jsonObject()
                val request = UserRequest(body.requiredString("username"), body.requiredString("password"))
                val user = service.update(id, request.username, request.password)
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(user.toResponse())
            }.describe {
                summary = "Replace a user"
                parameters { idPath() }
                requestBody { required = true; schema = jsonSchema<UserRequest>() }
                responses {
                    HttpStatusCode.OK { description = "Updated user"; schema = jsonSchema<UserResponse>() }
                    problem(HttpStatusCode.BadRequest, "Invalid user ID or JSON body")
                    problem(HttpStatusCode.NotFound, "User not found")
                    problem(HttpStatusCode.Conflict, "Username already exists or database constraint conflict")
                    serverError()
                }
            }

            delete {
                if (!service.delete(call.pathId())) throw ResourceNotFoundException("User not found")
                call.respond(HttpStatusCode.NoContent)
            }.describe {
                summary = "Delete a user"
                parameters { idPath() }
                responses {
                    HttpStatusCode.NoContent { description = "User deleted" }
                    problem(HttpStatusCode.BadRequest, "Invalid user ID")
                    problem(HttpStatusCode.NotFound, "User not found")
                    problem(HttpStatusCode.Conflict, "Database constraint conflict")
                    serverError()
                }
            }

            get("/teams") {
                val teams = service.listTeams(call.pathId())
                    ?: throw ResourceNotFoundException("User not found")
                call.respond(teams.map(Team::toResponse))
            }.describe {
                summary = "List a user's teams"
                parameters { idPath() }
                responses {
                    HttpStatusCode.OK { description = "Teams owned by the user"; schema = jsonSchema<List<TeamResponse>>() }
                    problem(HttpStatusCode.BadRequest, "Invalid user ID")
                    problem(HttpStatusCode.NotFound, "User not found")
                    serverError()
                }
            }
        }
    }
}
