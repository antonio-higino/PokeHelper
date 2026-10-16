package com.example

import com.example.adapters.http.userRoutes
import com.example.adapters.http.teamRoutes
import com.example.usecases.TeamService
import com.example.usecases.UserService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(userService: UserService, teamService: TeamService) {
    routing {
        userRoutes(userService)
        teamRoutes(teamService)
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}
