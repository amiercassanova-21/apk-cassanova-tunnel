package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainImportMenuTest {

    @Test
    fun shareIconOffersOnlyTheShareSheet() {
        assertEquals(listOf(ServerMenuAction.Share), serverMenuActions(includeManagementActions = false))
    }

    @Test
    fun moreMenuIsShareEditDelete() {
        assertEquals(
            listOf(ServerMenuAction.Share, ServerMenuAction.Edit, ServerMenuAction.Delete),
            serverMenuActions(includeManagementActions = true),
        )
    }

    @Test
    fun lockedConfigCanOnlyBeDeleted() {
        assertEquals(
            listOf(ServerMenuAction.Delete),
            serverMenuActions(includeManagementActions = true, isLocked = true),
        )
        assertTrue(serverMenuActions(includeManagementActions = false, isLocked = true).isEmpty())
    }

    @Test
    fun shareSheetStartsLockedOnlyWhenChosenAndPossible() {
        assertTrue(shareStartsLocked(preferLocked = true, lockedAvailable = true))
        assertFalse(shareStartsLocked(preferLocked = true, lockedAvailable = false))
        assertFalse(shareStartsLocked(preferLocked = false, lockedAvailable = true))
        assertFalse(shareStartsLocked(preferLocked = false, lockedAvailable = false))
    }
}
