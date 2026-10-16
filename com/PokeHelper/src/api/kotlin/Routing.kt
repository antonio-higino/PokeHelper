package com.example

import com.example.adapters.http.userRoutes
import com.example.usecases.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(userService: UserService) {
    routing {
        userRoutes(userService)
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}
