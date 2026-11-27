package com.pokehelper

import com.pokehelper.adapters.catalog.HttpPokemonCatalog
import com.pokehelper.adapters.catalog.UnconfiguredPokemonCatalog
import com.pokehelper.adapters.grpc.GrpcTeamRecommender
import com.pokehelper.adapters.grpc.UnconfiguredTeamRecommender
import com.pokehelper.adapters.grpc.recommendationChannel
import com.pokehelper.adapters.http.configureProblemDetails
import com.pokehelper.adapters.postgres.DatabaseFactory
import com.pokehelper.adapters.postgres.ExposedTeamRepository
import com.pokehelper.adapters.postgres.ExposedUserRepository
import com.pokehelper.adapters.postgres.isReachable
import com.pokehelper.config.AppConfig
import com.pokehelper.usecases.PokemonCatalogService
import com.pokehelper.usecases.TeamRecommendationService
import com.pokehelper.usecases.TeamService
import com.pokehelper.usecases.UserService
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

class ApiServices(
    val users: UserService,
    val teams: TeamService,
    val recommendations: TeamRecommendationService,
    val catalog: PokemonCatalogService,
    val databaseReady: suspend () -> Boolean
)

fun Application.module() {
    val config = AppConfig.from(environment.config)

    log.info("Applied {} database migration(s)", DatabaseFactory.migrate(config.database))
    val database = DatabaseFactory.connect(config.database)
    val users = ExposedUserRepository(database)
    val teams = ExposedTeamRepository(database)

    val channel = config.recommendation.target?.let(::recommendationChannel)
    val recommender = channel?.let { GrpcTeamRecommender(it, config.recommendation.timeout) } ?: UnconfiguredTeamRecommender
    val catalog = config.catalog.url?.let { HttpPokemonCatalog(it, config.catalog.timeout) } ?: UnconfiguredPokemonCatalog

    monitor.subscribe(ApplicationStopped) { channel?.shutdown() }

    configureApi(
        ApiServices(
            users = UserService(users, teams),
            teams = TeamService(teams, users),
            recommendations = TeamRecommendationService(teams, recommender),
            catalog = PokemonCatalogService(catalog),
            databaseReady = { database.isReachable() }
        )
    )
}

fun Application.configureApi(services: ApiServices) {
    install(ContentNegotiation) {
        jackson()
    }
    configureProblemDetails()
    configureRouting(services)
}
