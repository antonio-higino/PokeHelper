package com.example

import com.example.adapters.http.userRoutes
import com.example.adapters.http.teamRoutes
import com.example.adapters.http.serverError
import com.example.usecases.TeamService
import com.example.usecases.UserService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.openapi.reflect.ReflectionJsonSchemaInference
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting(userService: UserService, teamService: TeamService) {
    routing {
        userRoutes(userService)
        teamRoutes(teamService)
        get("/") {
            call.respondText("Hello, World!")
        }.describe {
            summary = "API status"
            responses {
                HttpStatusCode.OK {
                    description = "Plain-text greeting"
                    ContentType.Text.Plain { schema = jsonSchema<String>() }
                }
                serverError()
            }
        }
        swaggerUI("/swagger") {
            info = OpenApiInfo("PokeHelper API", "1.0.0")
            remotePath = "openapi.json"
            source = OpenApiDocSource.Routing(
                contentType = ContentType.Application.Json,
                schemaInference = ReflectionJsonSchemaInference.Default
            )
        }
    }
}
