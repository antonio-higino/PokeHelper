package com.pokehelper.usecases

import com.pokehelper.domain.CatalogDocument
import com.pokehelper.domain.PokemonCatalog

class PokemonCatalogService(private val catalog: PokemonCatalog) {
    suspend fun pokemon(identifier: String): CatalogDocument = catalog.pokemon(normalize(identifier, "Pokémon identifier"))

    suspend fun types(): List<CatalogDocument> = catalog.types()

    suspend fun type(name: String): CatalogDocument = catalog.type(normalize(name, "type name"))

    private fun normalize(value: String, field: String): String {
        val normalized = value.trim().lowercase()
        require(normalized.matches(IDENTIFIER)) { "$field must contain only letters, numbers or hyphens" }
        return normalized
    }

    private companion object {
        val IDENTIFIER = Regex("[a-z0-9-]{1,64}")
    }
}
