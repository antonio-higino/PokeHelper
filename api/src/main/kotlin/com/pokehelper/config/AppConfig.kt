package com.pokehelper.config

import io.ktor.server.config.ApplicationConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
)

data class RecommendationConfig(
    val target: String?,
    val timeout: Duration
)

data class CatalogConfig(
    val url: String?,
    val timeout: Duration
)

data class AppConfig(
    val database: DatabaseConfig,
    val recommendation: RecommendationConfig,
    val catalog: CatalogConfig
) {
    companion object {
        fun from(config: ApplicationConfig) = AppConfig(
            database = DatabaseConfig(
                url = config.property("database.url").getString(),
                user = config.property("database.user").getString(),
                password = config.property("database.password").getString()
            ),
            recommendation = RecommendationConfig(
                target = config.optional("recommendation.target"),
                timeout = config.millis("recommendation.timeoutMs")
            ),
            catalog = CatalogConfig(
                url = config.optional("catalog.url"),
                timeout = config.millis("catalog.timeoutMs")
            )
        )
    }
}

private fun ApplicationConfig.optional(path: String): String? =
    propertyOrNull(path)?.getString()?.takeIf(String::isNotBlank)

private fun ApplicationConfig.millis(path: String): Duration =
    property(path).getString().toLong().milliseconds
