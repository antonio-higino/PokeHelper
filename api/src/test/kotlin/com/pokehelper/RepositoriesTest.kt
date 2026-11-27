package com.pokehelper

import com.pokehelper.adapters.postgres.ExposedTeamRepository
import com.pokehelper.adapters.postgres.ExposedUserRepository
import kotlinx.coroutines.runBlocking
import com.pokehelper.support.PostgresTestDatabase
import com.pokehelper.support.pokemon
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostgresRepositoriesTest {
    @Test
    fun `repositories persist users and teams in PostgreSQL`(): Unit = runBlocking {
        val database = PostgresTestDatabase.database
        val users = ExposedUserRepository(database)
        val teams = ExposedTeamRepository(database)
        val user = users.create("repo-test-${UUID.randomUUID()}", "test-password")

        try {
            assertEquals(user, users.findById(user.id))
            assertEquals(user, users.findByUsername(user.username))

            val updatedUser = user.copy(password = "updated-password")
            assertEquals(updatedUser, users.update(updatedUser))
            assertEquals(updatedUser, users.findById(user.id))

            val team = teams.create("Test team", user.id, listOf(pokemon(6, "charizard", "fire", "flying")))
            try {
                assertEquals(team, teams.findById(team.id))
                assertEquals(listOf(team), teams.findByUserId(user.id))
                assertEquals(listOf("fire", "flying"), teams.findById(team.id)!!.pokemons.single().types)

                val updatedTeam = team.copy(name = "Updated team")
                assertEquals(updatedTeam, teams.update(updatedTeam))
                assertEquals(updatedTeam, teams.findById(team.id))

                assertTrue(teams.delete(team.id))
                assertNull(teams.findById(team.id))
                assertFalse(teams.delete(team.id))
            } finally {
                teams.delete(team.id)
            }

            assertTrue(users.delete(user.id))
            assertNull(users.findById(user.id))
            assertFalse(users.delete(user.id))
        } finally {
            users.delete(user.id)
        }
    }
}