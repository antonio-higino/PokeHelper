package com.pokehelper.usecases

import com.pokehelper.support.InMemoryTeamRepository
import com.pokehelper.support.InMemoryUserRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UserServiceTest {
    private val service = UserService(InMemoryUserRepository(), InMemoryTeamRepository())

    @Test
    fun `rejects blank or too long credentials`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> { service.create(" ", "secret") }
        assertFailsWith<IllegalArgumentException> { service.create("a".repeat(256), "secret") }
    }
}
