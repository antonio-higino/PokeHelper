package com.example.adapters.http

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.Parameters
import io.ktor.openapi.Responses
import io.ktor.openapi.jsonSchema

private val problemContentType = ContentType.parse("application/problem+json")

internal fun Parameters.Builder.idPath() {
    path("id") {
        description = "Positive resource ID"
        schema = jsonSchema<Long>().copy(format = "int64", minimum = 1.0)
    }
}

internal fun Parameters.Builder.pagination() {
    query("page") {
        description = "Page number, starting at 1 (default: 1)"
        schema = jsonSchema<Int>().copy(format = "int32", minimum = 1.0)
    }
    query("size") {
        description = "Page size from 1 to 100 (default: 20)"
        schema = jsonSchema<Int>().copy(format = "int32", minimum = 1.0, maximum = 100.0)
    }
}

internal fun Responses.Builder.problem(status: HttpStatusCode, detail: String) {
    status {
        description = detail
        problemContentType { schema = jsonSchema<ProblemDetails>() }
    }
}

internal fun Responses.Builder.serverError() =
    problem(HttpStatusCode.InternalServerError, "Unexpected server error")
