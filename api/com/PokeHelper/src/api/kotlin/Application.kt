package com.example

import com.example.adapters.postgres.ExposedTeamRepository
import com.example.adapters.postgres.ExposedUserRepository
import com.example.adapters.services.PokeServicesClient
import com.example.adapters.http.configureProblemDetails
import com.example.domain.TeamRepository
import com.example.domain.UserRepository
import com.example.usecases.TeamService
import com.example.usecases.UserService
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.module() {
    val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/app_db"
    val databaseUser = System.getenv("DATABASE_USER") ?: "admin"
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: "admin"

    Flyway.configure()
        .dataSource(databaseUrl, databaseUser, databasePassword)
        .load()
        .migrate()

    val database = Database.connect(
        url = databaseUrl,
        driver = "org.postgresql.Driver",
        user = databaseUser,
        password = databasePassword
    )
    val userRepository: UserRepository = ExposedUserRepository(database)
    val teamRepository: TeamRepository = ExposedTeamRepository(database)
    val pokeServices = PokeServicesClient(
        pokemonTarget = System.getenv("POKEMON_GRPC_TARGET") ?: "localhost:50051",
        recommendationTarget = System.getenv("RECOMMENDATION_GRPC_TARGET") ?: "localhost:50052",
        timeoutMillis = System.getenv("POKEMON_GRPC_TIMEOUT_MS")?.toLong() ?: 2_000
    )
    monitor.subscribe(ApplicationStopped) { pokeServices.close() }

    install(ContentNegotiation) {
        jackson()
    }
    configureProblemDetails()
    configureRouting(
        userService = UserService(userRepository, teamRepository),
        teamService = TeamService(teamRepository, userRepository),
        pokeServices = pokeServices
    )
}
