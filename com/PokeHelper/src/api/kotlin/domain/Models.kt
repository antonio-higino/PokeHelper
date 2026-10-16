package com.example.domain

data class User(
    val id: Long,
    val username: String,
    val password: String
)

data class Team(
    val id: Long,
    val name: String,
    val userId: Long
)