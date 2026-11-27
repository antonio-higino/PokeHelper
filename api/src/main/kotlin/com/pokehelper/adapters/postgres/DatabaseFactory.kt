package com.pokehelper.adapters.postgres

import com.pokehelper.config.DatabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object DatabaseFactory {
    fun connect(config: DatabaseConfig): Database =
        Database.connect(url = config.url, driver = "org.postgresql.Driver", user = config.user, password = config.password)

    fun migrate(config: DatabaseConfig): Int =
        Flyway.configure()
            .dataSource(config.url, config.user, config.password)
            .load()
            .migrate()
            .migrationsExecuted
}

suspend fun Database.isReachable(): Boolean = runCatching {
    withTimeout(2_000) { newSuspendedTransaction(Dispatchers.IO, db = this@isReachable) { exec("SELECT 1") } }
}.isSuccess
