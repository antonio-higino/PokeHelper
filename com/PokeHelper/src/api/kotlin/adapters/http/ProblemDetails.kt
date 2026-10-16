package com.example.adapters.http

import com.example.domain.UsernameAlreadyExists
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import java.sql.SQLException
import java.util.concurrent.CancellationException

data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String
)

class InvalidRequestException(message: String) : IllegalArgumentException(message)
class ResourceNotFoundException(message: String) : RuntimeException(message)

private val problemJson = ObjectMapper()
private val problemContentType = ContentType.parse("application/problem+json")

fun Application.configureProblemDetails() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            val (status, detail) = when {
                cause is InvalidRequestException || cause is IllegalArgumentException || cause is BadRequestException ->
                    HttpStatusCode.BadRequest to (cause.message ?: "Invalid request")
                cause is ResourceNotFoundException -> HttpStatusCode.NotFound to (cause.message ?: "Resource not found")
                cause is UsernameAlreadyExists -> HttpStatusCode.Conflict to "Username already exists"
                cause.hasIntegrityViolation() -> HttpStatusCode.Conflict to "The request conflicts with a database constraint"
                else -> {
                    call.application.log.error("Unhandled request error", cause)
                    HttpStatusCode.InternalServerError to "An unexpected error occurred"
                }
            }
            call.respondProblem(status, detail)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondProblem(HttpStatusCode.NotFound, "Resource not found")
        }
    }
}

private fun Throwable.hasIntegrityViolation(): Boolean =
    generateSequence(this) { it.cause }
        .filterIsInstance<SQLException>()
        .any { it.sqlState?.startsWith("23") == true }

private suspend fun ApplicationCall.respondProblem(status: HttpStatusCode, detail: String) {
    val problem = ProblemDetails("about:blank", status.description, status.value, detail)
    respondText(problemJson.writeValueAsString(problem), problemContentType, status)
}
