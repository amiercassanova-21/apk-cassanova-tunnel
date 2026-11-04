package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainImportMenuTest {

    @Test
    fun regularShareMenuContainsOnlyShareActions() {
        val expected = listOf(
            ServerMenuAction.ShareQRCode,
            ServerMenuAction.ShareClipboard,
            ServerMenuAction.ShareFullContent,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = false, includeManagementActions = false))
    }

    @Test
    fun regularMoreMenuContainsEveryActionInDisplayOrder() {
        assertEquals(
            ServerMenuAction.entries - ServerMenuAction.ShareLocked,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true),
        )
    }

    @Test
    fun complexShareMenuContainsOnlyFullContent() {
        assertEquals(
            listOf(ServerMenuAction.ShareFullContent),
            serverMenuActions(isComplexProfile = true, includeManagementActions = false),
        )
    }

    @Test
    fun complexMoreMenuRetainsManagementActions() {
        val expected = listOf(
            ServerMenuAction.ShareFullContent,
            ServerMenuAction.Edit,
            ServerMenuAction.Delete,
        )
        assertEquals(expected, serverMenuActions(isComplexProfile = true, includeManagementActions = true))
    }

    @Test
    fun lockedCopyOfferedOnlyWhenLockingIsAvailable() {
        assertEquals(
            ServerMenuAction.entries,
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, lockAvailable = true),
        )
        assertEquals(
            false,
            ServerMenuAction.ShareLocked in serverMenuActions(
                isComplexProfile = true, includeManagementActions = true, lockAvailable = true
            ),
        )
    }

    @Test
    fun lockedProfileCanOnlyBeDeleted() {
        assertEquals(
            listOf(ServerMenuAction.Delete),
            serverMenuActions(isComplexProfile = false, includeManagementActions = true, isLocked = true, lockAvailable = true),
        )
        assertEquals(
            emptyList<ServerMenuAction>(),
            serverMenuActions(isComplexProfile = false, includeManagementActions = false, isLocked = true, lockAvailable = true),
        )
    }
}
