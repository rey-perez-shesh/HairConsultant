package com.hairconsultant.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ScanPhotoStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun latest_isNullBeforeAnyScan() {
        assertNull(ScanPhotoStore(folder.root).latest("user-a"))
    }

    @Test
    fun latest_returnsTheNewestScan() {
        val store = ScanPhotoStore(folder.root)
        photo("user-a", 900L)
        val newest = photo("user-a", 1_000L)

        assertEquals(newest, store.latest("user-a"))
    }

    @Test
    fun nextFile_keepsOnlyThePreviousScanBesideTheNewOne() {
        val store = ScanPhotoStore(folder.root)
        photo("user-a", 100L)
        photo("user-a", 200L)
        val previous = photo("user-a", 300L)

        val next = store.nextFile("user-a")
        next.writeText("new")

        assertEquals(setOf(previous, next), File(folder.root, "user-a").listFiles()!!.toSet())
        assertEquals(next, store.latest("user-a"))
    }

    @Test
    fun accounts_neverSeeEachOthersScan() {
        val store = ScanPhotoStore(folder.root)
        val mine = photo("user-a", 100L)

        assertNull(store.latest("user-b"))
        store.nextFile("user-b").writeText("theirs")
        assertEquals(mine, store.latest("user-a"))
        assertTrue(mine.exists())
    }

    private fun photo(uid: String, takenAt: Long): File =
        File(folder.root, "$uid/$takenAt.jpg").apply { parentFile!!.mkdirs(); writeText("jpeg") }
}
