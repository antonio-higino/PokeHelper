package com.pokehelper.adapters.http

import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

data class HealthResponse(val status: String)

@OptIn(ExperimentalKtorApi::class)
fun Route.healthRoutes(databaseReady: suspend () -> Boolean) {
    get("/health/ready") {
        if (databaseReady()) call.respond(HealthResponse("UP"))
        else call.respond(HttpStatusCode.ServiceUnavailable, HealthResponse("DOWN"))
    }.describe {
        summary = "Readiness check"
        description = "Checks the database. The Go services are not checked: the API works without them."
        responses {
            HttpStatusCode.OK { description = "Ready to serve requests"; schema = jsonSchema<HealthResponse>() }
            HttpStatusCode.ServiceUnavailable { description = "The database is down"; schema = jsonSchema<HealthResponse>() }
        }
    }
}
