package com.pokehelper.adapters.http

import com.pokehelper.domain.PokemonCandidate
import com.pokehelper.domain.TeamAnalysis
import com.pokehelper.domain.TypeCoverage
import com.pokehelper.domain.TypeScore
import com.pokehelper.usecases.TeamRecommendationService
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

data class TypeCoverageResponse(
    val attackingType: String,
    val immune: Int,
    val veryResistant: Int,
    val resistant: Int,
    val neutral: Int,
    val weak: Int,
    val veryWeak: Int
)
data class TypeScoreResponse(val type: String, val score: Int)
data class TeamAnalysisResponse(
    val teamId: Long,
    val coverage: List<TypeCoverageResponse>,
    val recommendedTypes: List<TypeScoreResponse>
)
data class PokemonCandidateResponse(
    val id: Int,
    val name: String,
    val types: List<String>,
    val baseStatTotal: Int,
    val combinationRank: Int,
    val spriteUrl: String?
)
data class PokemonRecommendationResponse(
    val teamId: Long,
    val type: String,
    val candidates: List<PokemonCandidateResponse>
)

private fun TeamAnalysis.toResponse(teamId: Long) = TeamAnalysisResponse(
    teamId = teamId,
    coverage = coverage.map(TypeCoverage::toResponse),
    recommendedTypes = recommendedTypes.map(TypeScore::toResponse)
)

private fun TypeCoverage.toResponse() =
    TypeCoverageResponse(attackingType, immune, veryResistant, resistant, neutral, weak, veryWeak)

private fun TypeScore.toResponse() = TypeScoreResponse(type, score)

private fun PokemonCandidate.toResponse() =
    PokemonCandidateResponse(id, name, types, baseStatTotal, combinationRank, spriteUrl)

@OptIn(ExperimentalKtorApi::class)
fun Route.recommendationRoutes(service: TeamRecommendationService) {
    get("/teams/{id}/analysis") {
        val id = call.pathId()
        val analysis = service.analyze(id) ?: throw ResourceNotFoundException("Team not found")
        call.respond(analysis.toResponse(id))
    }.describe {
        summary = "Analyze the defensive coverage of a team"
        description = "Computed by the recommendation service over gRPC. The rest of the API keeps working " +
            "when that service is down: this route answers 503 or 504."
        parameters { idPath() }
        responses {
            HttpStatusCode.OK {
                description = "Coverage per attacking type and up to five recommended types"
                schema = jsonSchema<TeamAnalysisResponse>()
            }
            dependencyFailures()
        }
    }

    get("/teams/{id}/recommendations") {
        val id = call.pathId()
        val type = call.queryValue("type") ?: throw InvalidRequestException("type is required")
        val candidates = service.recommendPokemon(id, type) ?: throw ResourceNotFoundException("Team not found")
        call.respond(PokemonRecommendationResponse(id, type.trim().lowercase(), candidates.map { it.toResponse() }))
    }.describe {
        summary = "Recommend Pokémon of a type for a team"
        description = "Computed by the recommendation service over gRPC. Legendary, mythical and baby Pokémon, " +
            "special forms and Pokémon already in the team are left out."
        parameters {
            idPath()
            query("type") {
                description = "Type of the suggested Pokémon, for example ground"
                required = true
                schema = jsonSchema<String>()
            }
        }
        responses {
            HttpStatusCode.OK {
                description = "Candidates, from the most to the least suitable"
                schema = jsonSchema<PokemonRecommendationResponse>()
            }
            dependencyFailures()
        }
    }
}

@OptIn(ExperimentalKtorApi::class)
private fun io.ktor.openapi.Responses.Builder.dependencyFailures() {
    problem(HttpStatusCode.BadRequest, "Invalid team ID or query parameter")
    problem(HttpStatusCode.NotFound, "Team not found")
    problem(HttpStatusCode.UnprocessableEntity, "The recommendation service rejected the team")
    problem(HttpStatusCode.ServiceUnavailable, "The recommendation service is unavailable or not configured")
    problem(HttpStatusCode.GatewayTimeout, "The recommendation service did not answer in time")
    serverError()
}
