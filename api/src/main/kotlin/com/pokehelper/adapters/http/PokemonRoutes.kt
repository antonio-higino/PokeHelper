package com.pokehelper.adapters.http

import com.pokehelper.usecases.PokemonCatalogService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.route
import io.ktor.utils.io.ExperimentalKtorApi

data class CatalogTypeSlot(val slot: Int, val type: Map<String, String>)
data class CatalogPokemonResponse(val id: Int, val name: String, val types: List<CatalogTypeSlot>)
data class CatalogTypeResponse(val name: String, val damage_relations: Map<String, List<Map<String, String>>>)
data class CatalogTypeListResponse(val items: List<CatalogTypeResponse>)

@OptIn(ExperimentalKtorApi::class)
fun Route.pokemonRoutes(catalog: PokemonCatalogService) {
    route("/pokemon") {
        get("/types") {
            call.respond(mapOf("items" to catalog.types()))
        }.describe {
            summary = "List the Pokémon types"
            description = "The 18 types and their damage relations, from the catalog service (PokéAPI cache)."
            responses {
                HttpStatusCode.OK { description = "PokéAPI type documents"; schema = jsonSchema<CatalogTypeListResponse>() }
                catalogFailures()
            }
        }

        get("/types/{name}") {
            call.respond(catalog.type(call.parameters["name"].orEmpty()))
        }.describe {
            summary = "Get a Pokémon type"
            parameters { path("name") { description = "Type name, for example fire"; schema = jsonSchema<String>() } }
            responses {
                HttpStatusCode.OK { description = "PokéAPI type document"; schema = jsonSchema<CatalogTypeResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid type name")
                problem(HttpStatusCode.NotFound, "Type not found")
                catalogFailures()
            }
        }

        get("/{identifier}") {
            call.respond(catalog.pokemon(call.parameters["identifier"].orEmpty()))
        }.describe {
            summary = "Get a Pokémon"
            parameters {
                path("identifier") { description = "Pokémon name or numeric ID"; schema = jsonSchema<String>() }
            }
            responses {
                HttpStatusCode.OK { description = "PokéAPI Pokémon document"; schema = jsonSchema<CatalogPokemonResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid Pokémon identifier")
                problem(HttpStatusCode.NotFound, "Pokémon not found")
                catalogFailures()
            }
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
private fun io.ktor.openapi.Responses.Builder.catalogFailures() {
    problem(HttpStatusCode.ServiceUnavailable, "The catalog service is unavailable or not configured")
    problem(HttpStatusCode.GatewayTimeout, "The catalog service did not answer in time")
    serverError()
}
