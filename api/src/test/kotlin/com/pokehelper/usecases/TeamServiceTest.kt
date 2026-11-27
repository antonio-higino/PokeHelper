package com.pokehelper.usecases

import com.pokehelper.support.InMemoryTeamRepository
import com.pokehelper.support.InMemoryUserRepository
import com.pokehelper.support.pokemon
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TeamServiceTest {
    private val users = InMemoryUserRepository()
    private val teams = InMemoryTeamRepository()
    private val service = TeamService(teams, users)

    @Test
    fun `rejects empty, oversized and repeated teams`() = runBlocking<Unit> {
        val owner = users.create("misty", "secret")
        assertFailsWith<IllegalArgumentException> { service.create("Empty", owner.id, emptyList()) }
        assertFailsWith<IllegalArgumentException> { service.create("Seven", owner.id, (1..7).map { pokemon(it, "p$it") }) }
        assertFailsWith<IllegalArgumentException> {
            service.create("Twice", owner.id, listOf(pokemon(25, "pikachu"), pokemon(25, "pikachu")))
        }
    }
}
