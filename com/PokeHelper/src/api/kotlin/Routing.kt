package com.example

import com.example.domain.TeamRepository
import com.example.domain.UserRepository
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    userRepository: UserRepository,
    teamRepository: TeamRepository
) {
    routing {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}