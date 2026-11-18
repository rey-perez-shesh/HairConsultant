package com.hairconsultant.app.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailCheckTest {
    @Test
    fun `a normal address passes`() {
        assertTrue(isValidEmail("ana.reyes@gmail.com"))
    }

    @Test
    fun `surrounding spaces are ignored`() {
        assertTrue(isValidEmail("  ana@gmail.com "))
    }

    @Test
    fun `blank text fails`() {
        assertFalse(isValidEmail("   "))
    }

    @Test
    fun `an address without at fails`() {
        assertFalse(isValidEmail("ana.gmail.com"))
    }

    @Test
    fun `an address without a domain dot fails`() {
        assertFalse(isValidEmail("ana@gmail"))
    }
}
