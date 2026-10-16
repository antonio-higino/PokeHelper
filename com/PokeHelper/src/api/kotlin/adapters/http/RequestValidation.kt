package com.example.adapters.http

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText

private val requestJson = ObjectMapper()

suspend fun ApplicationCall.jsonObject(): JsonNode {
    val body = receiveText()
    if (body.isBlank()) throw InvalidRequestException("JSON body is required")
    val node = try {
        requestJson.readTree(body)
    } catch (_: JsonProcessingException) {
        throw InvalidRequestException("Malformed JSON body")
    }
    if (node == null || !node.isObject) throw InvalidRequestException("JSON body must be an object")
    return node
}

fun JsonNode.requiredString(field: String): String {
    val value = get(field)
    if (value == null || !value.isTextual) throw InvalidRequestException("$field must be a string")
    return value.textValue()
}

fun JsonNode.requiredPositiveLong(field: String): Long {
    val value = get(field)
    if (value == null || !value.isIntegralNumber || !value.canConvertToLong() || value.longValue() <= 0) {
        throw InvalidRequestException("$field must be a positive integer")
    }
    return value.longValue()
}

fun JsonNode.requiredArray(field: String): List<JsonNode> {
    val value = get(field)
    if (value == null || !value.isArray) throw InvalidRequestException("$field must be an array")
    return value.toList()
}

fun ApplicationCall.queryValue(name: String): String? {
    val values = request.queryParameters.getAll(name) ?: return null
    if (values.size != 1) throw InvalidRequestException("$name must be provided only once")
    return values.single()
}

fun ApplicationCall.intQuery(name: String, default: Int): Int {
    val value = queryValue(name) ?: return default
    return value.toIntOrNull() ?: throw InvalidRequestException("$name must be an integer")
}

fun ApplicationCall.positiveLongQuery(name: String): Long? {
    val value = queryValue(name) ?: return null
    return value.toLongOrNull()?.takeIf { it > 0 }
        ?: throw InvalidRequestException("$name must be a positive integer")
}

fun ApplicationCall.pathId(): Long =
    parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
        ?: throw InvalidRequestException("id must be a positive integer")
