package com.pokehelper.adapters.json

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.pokehelper.domain.Pokemon

object PokemonJson {
    private val mapper = ObjectMapper()
    private val documentType = object : TypeReference<Map<String, Any?>>() {}

    fun fromNode(node: JsonNode): Pokemon {
        require(node.isObject) { "each pokemon must be a JSON object" }
        val id = node.get("id")
        require(id != null && id.isIntegralNumber && id.canConvertToInt()) {
            "each pokemon must have a positive integer id"
        }
        val name = node.get("name")
        require(name != null && name.isTextual) { "each pokemon must have a name" }
        return Pokemon(
            id = id.intValue(),
            name = name.textValue(),
            types = typesOf(node),
            document = mapper.convertValue(node, documentType)
        )
    }

    fun toNode(pokemon: Pokemon): JsonNode = mapper.valueToTree(pokemon.document)

    fun readList(json: String): List<Pokemon> = mapper.readTree(json).map(::fromNode)

    fun writeList(pokemons: List<Pokemon>): String = mapper.writeValueAsString(pokemons.map(Pokemon::document))

    private fun typesOf(node: JsonNode): List<String> =
        node.path("types").mapNotNull { type ->
            val name = if (type.isTextual) type else type.path("type").path("name")
            name.takeIf(JsonNode::isTextual)?.textValue()?.lowercase()
        }
}
