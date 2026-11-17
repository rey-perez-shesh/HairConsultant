package com.hairconsultant.app.ui

import com.hairconsultant.app.ui.auth.passwordRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {
    @Test
    fun `a strong password meets every rule`() {
        assertTrue(passwordRules("Abcdef1!").all { it.met })
    }

    @Test
    fun `each missing ingredient fails only its own rule`() {
        fun unmet(password: String) = passwordRules(password).filterNot { it.met }.map { it.label }
        assertEquals(listOf("8+ characters"), unmet("Abc1!"))
        assertEquals(listOf("Uppercase"), unmet("abcdef1!"))
        assertEquals(listOf("Lowercase"), unmet("ABCDEF1!"))
        assertEquals(listOf("Number"), unmet("Abcdefg!"))
        assertEquals(listOf("Symbol"), unmet("Abcdefg1"))
    }
}
