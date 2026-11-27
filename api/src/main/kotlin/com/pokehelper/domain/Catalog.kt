package com.pokehelper.domain

typealias CatalogDocument = Map<String, Any?>

interface PokemonCatalog {
    suspend fun pokemon(identifier: String): CatalogDocument
    suspend fun types(): List<CatalogDocument>
    suspend fun type(name: String): CatalogDocument
}
