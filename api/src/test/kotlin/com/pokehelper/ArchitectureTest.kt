package com.pokehelper

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import kotlin.test.Test

class ArchitectureTest {
    private val forbidden = listOf(
        "io.ktor",
        "io.grpc",
        "com.google.protobuf",
        "com.pokehelper.grpc",
        "org.jetbrains.exposed",
        "org.flywaydb",
        "java.sql",
        "javax.sql",
        "com.fasterxml",
        "com.pokehelper.adapters",
        "com.pokehelper.config"
    )

    @Test
    fun `domain and use cases do not import frameworks, database, JSON or gRPC`() {
        Konsist.scopeFromProduction().files
            .filter { file -> listOf("com.pokehelper.domain", "com.pokehelper.usecases").any { file.packagee?.name?.startsWith(it) == true } }
            .assertFalse { file -> file.imports.any { import -> forbidden.any { import.name == it || import.name.startsWith("$it.") } } }
    }
}
