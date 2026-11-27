package com.pokehelper.support

import com.pokehelper.adapters.postgres.DatabaseFactory
import com.pokehelper.config.DatabaseConfig
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.ConfigLoader
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.config.mergeWith
import io.ktor.server.testing.ApplicationTestBuilder
import org.jetbrains.exposed.sql.Database
import org.testcontainers.postgresql.PostgreSQLContainer

object PostgresTestDatabase {
    private val container: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    val database: Database by lazy {
        val config = DatabaseConfig(container.jdbcUrl, container.username, container.password)
        DatabaseFactory.migrate(config)
        DatabaseFactory.connect(config)
    }

    val applicationConfig: ApplicationConfig by lazy {
        ConfigLoader.load("application.yaml").mergeWith(
            MapApplicationConfig(
                "database.url" to container.jdbcUrl,
                "database.user" to container.username,
                "database.password" to container.password
            )
        )
    }
}

fun ApplicationTestBuilder.useTestDatabase() {
    environment { config = PostgresTestDatabase.applicationConfig }
}
