package com.example.adapters.http

import com.example.adapters.services.PokeServicesClient
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

data class PokemonLookupResponse(
    @JsonSchema.Minimum(1.0) val id: Int,
    @JsonSchema.MinLength(1) val name: String,
    val types: List<JsonNode>
)
data class TypeListResponse(val items: List<JsonNode>)
data class RecommendationRequest(@JsonSchema.MinItems(1) @JsonSchema.MaxItems(6) val pokemons: List<JsonNode>)
data class PokemonRecommendationRequest(
    @JsonSchema.MinLength(1) val type: String,
    @JsonSchema.MinItems(1) @JsonSchema.MaxItems(6) val pokemons: List<JsonNode>
)

@OptIn(ExperimentalKtorApi::class)
fun Route.pokemonRoutes(services: PokeServicesClient) {
    route("/pokemon") {
        get("/types") {
            call.respond(services.types())
        }.describe {
            summary = "List cached Pokémon types"
            responses {
                HttpStatusCode.OK { description = "PokéAPI type documents"; schema = jsonSchema<TypeListResponse>() }
                downstreamFailure()
                serverError()
            }
        }

        get("/types/{name}") {
            val name = call.parameters["name"] ?: throw InvalidRequestException("type name is required")
            call.respond(services.type(name))
        }.describe {
            summary = "Get a cached Pokémon type"
            parameters { path("name") { description = "Pokémon type name"; schema = jsonSchema<String>() } }
            responses {
                HttpStatusCode.OK { description = "PokéAPI type document"; schema = jsonSchema<JsonNode>() }
                problem(HttpStatusCode.BadRequest, "Invalid type name")
                problem(HttpStatusCode.NotFound, "Type not found")
                downstreamFailure()
                serverError()
            }
        }

        get("/{identifier}") {
            val identifier = call.parameters["identifier"] ?: throw InvalidRequestException("Pokémon identifier is required")
            call.respond(services.pokemon(identifier))
        }.describe {
            summary = "Get a Pokémon through the cached catalog"
            parameters { path("identifier") { description = "Pokémon name or numeric ID"; schema = jsonSchema<String>() } }
            responses {
                HttpStatusCode.OK { description = "PokéAPI Pokémon document"; schema = jsonSchema<PokemonLookupResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid Pokémon identifier")
                problem(HttpStatusCode.NotFound, "Pokémon not found")
                downstreamFailure()
                serverError()
            }
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
fun Route.recommendationRoutes(services: PokeServicesClient) {
    route("/recommendations") {
        post {
            val body = call.jsonObject()
            validateRecommendationTeam(body.requiredArray("pokemons"))
            call.respond(services.analyze(body))
        }.describe {
            summary = "Analyze a team and recommend defensive types"
            requestBody { required = true; schema = jsonSchema<RecommendationRequest>() }
            responses {
                HttpStatusCode.OK { description = "Defensive coverage matrix and up to five type recommendations"; schema = jsonSchema<JsonNode>() }
                problem(HttpStatusCode.BadRequest, "Invalid team")
                downstreamFailure()
                serverError()
            }
        }

        post("/pokemon") {
            val body = call.jsonObject()
            body.requiredString("type")
            validateRecommendationTeam(body.requiredArray("pokemons"))
            call.respond(services.recommendPokemon(body))
        }.describe {
            summary = "Recommend Pokémon for a type and a team"
            requestBody { required = true; schema = jsonSchema<PokemonRecommendationRequest>() }
            responses {
                HttpStatusCode.OK { description = "Ranked Pokémon candidates"; schema = jsonSchema<TypeListResponse>() }
                problem(HttpStatusCode.BadRequest, "Invalid type or team")
                downstreamFailure()
                serverError()
            }
        }
    }
}

private fun validateRecommendationTeam(pokemons: List<JsonNode>) {
    if (pokemons.size !in 1..6) throw InvalidRequestException("a team must contain between 1 and 6 pokemons")
    val ids = pokemons.map { pokemon ->
        val id = pokemon.get("id")
        if (!pokemon.isObject || id == null || !id.isIntegralNumber || !id.canConvertToInt() || id.intValue() <= 0) {
            throw InvalidRequestException("each pokemon must have a positive integer id")
        }
        id.intValue()
    }
    if (ids.distinct().size != ids.size) throw InvalidRequestException("a team cannot contain repeated pokemons")
}

@OptIn(ExperimentalKtorApi::class)
private fun io.ktor.openapi.Responses.Builder.downstreamFailure() {
    problem(HttpStatusCode.BadGateway, "A required Pokémon microservice is unavailable")
}
