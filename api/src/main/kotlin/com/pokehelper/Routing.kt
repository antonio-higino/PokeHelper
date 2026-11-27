package com.pokehelper

import com.pokehelper.adapters.http.healthRoutes
import com.pokehelper.adapters.http.pokemonRoutes
import com.pokehelper.adapters.http.recommendationRoutes
import com.pokehelper.adapters.http.serverError
import com.pokehelper.adapters.http.teamRoutes
import com.pokehelper.adapters.http.userRoutes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.jsonSchema
import io.ktor.openapi.reflect.ReflectionJsonSchemaInference
import io.ktor.server.application.Application
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.OpenApiDocSource
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi

@OptIn(ExperimentalKtorApi::class)
fun Application.configureRouting(services: ApiServices) {
    routing {
        userRoutes(services.users)
        teamRoutes(services.teams)
        recommendationRoutes(services.recommendations)
        pokemonRoutes(services.catalog)
        healthRoutes(services.databaseReady)
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
