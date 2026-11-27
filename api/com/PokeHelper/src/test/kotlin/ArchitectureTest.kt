package com.PokeHelper

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import kotlin.test.Test

class ArchitectureTest {
    @Test
    fun `domain and use cases do not depend on transport persistence or generated contracts`() {
        val classes = ClassFileImporter().importPackages("com.example")
        noClasses().that().resideInAnyPackage("com.example.domain..", "com.example.usecases..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "io.grpc..", "com.pokehelper.contract..", "com.google.protobuf..",
                "io.ktor..", "org.jetbrains.exposed..", "com.example.adapters.."
            ).check(classes)
    }
}
