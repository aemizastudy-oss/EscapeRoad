package com.example.roadescape.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LobbyLayoutModelsTest {
    @Test
    fun bundledDefaultsAreValidAndExportBothProfilesWithoutLosingOffsets() {
        val defaults = LobbyLayoutJson.parse(File("src/main/assets/lobby_layout.json").readText())
        assertEquals(LobbyLayoutSettings.CURRENT_SCHEMA_VERSION, defaults.schemaVersion)
        val exported = LobbyLayoutJson.encode(defaults)
        assertTrue(exported.contains("\"schemaVersion\""))
        assertTrue(exported.contains("\"wide\""))
        assertTrue(exported.contains("\"compact\""))
        val restored = LobbyLayoutJson.parse(exported)
        assertEquals(defaults, restored)
        LobbyLayoutProfile.entries.forEach { profile ->
            assertEquals(defaults.offsets(profile), restored.offsets(profile))
        }
    }

    @Test
    fun updatingOneProfilePreservesTheOtherAndRoundTripsTheWholeExport() {
        val wide = mapOf(LobbyBlock.LOGO to LobbyOffset(12.5f, -30f), LobbyBlock.ACTIONS to LobbyOffset(-7f, 44f))
        val compact = mapOf(LobbyBlock.WELCOME to LobbyOffset(0f, 18f))
        val original = LobbyLayoutSettings(wide = wide, compact = compact)
        val changed = original.withOffsets(LobbyLayoutProfile.COMPACT, mapOf(LobbyBlock.RESUME to LobbyOffset(4f, 10f)))
        assertEquals(wide, changed.wide)
        assertEquals(compact, original.compact)
        assertEquals(mapOf(LobbyBlock.RESUME to LobbyOffset(4f, 10f)), changed.compact)
        assertEquals(changed, LobbyLayoutJson.parse(LobbyLayoutJson.encode(changed)))
    }

    @Test
    fun emptyProfileResetDoesNotRemoveTheOtherProfile() {
        val current = LobbyLayoutSettings(
            wide = mapOf(LobbyBlock.TITLE to LobbyOffset(10f, 20f)),
            compact = mapOf(LobbyBlock.DESCRIPTION to LobbyOffset(-10f, -20f)),
        )
        val reset = current.withOffsets(LobbyLayoutProfile.WIDE, emptyMap())
        assertTrue(reset.wide.isEmpty())
        assertEquals(current.compact, reset.compact)
        assertFalse(current.wide.isEmpty())
    }

    @Test
    fun finiteBoundaryOffsetsAreAllowedButNonFiniteOrExcessiveValuesAreRejected() {
        val boundary = LobbyLayoutSettings(wide = mapOf(LobbyBlock.LOGO to LobbyOffset(-4096f, 4096f)))
        val restored = LobbyLayoutJson.parse(LobbyLayoutJson.encode(boundary)).wide.getValue(LobbyBlock.LOGO)
        assertEquals(-4096f, restored.xDp, 0f)
        assertEquals(4096f, restored.yDp, 0f)
        for ((x, y) in listOf(
            Float.NaN to 0f, 0f to Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY to 0f,
            4096.5f to 0f, 0f to -4096.5f,
        )) {
            assertTrue("Invalid offset $x,$y", runCatching { LobbyOffset(x, y) }.isFailure)
        }
        assertTrue(runCatching {
            LobbyLayoutJson.parse("""{"schemaVersion":1,"wide":{"LOGO":{"xDp":4097.0,"yDp":0.0}},"compact":{}}""")
        }.isFailure)
    }

    @Test
    fun unknownMissingOrWronglyTypedSchemaAndMalformedPayloadsDoNotBecomeDefaults() {
        for (text in listOf(
            """{"schemaVersion":2,"wide":{},"compact":{}}""",
            """{"wide":{},"compact":{}}""",
            """{"schemaVersion":"1","wide":{},"compact":{}}""",
            "{ interrupted",
            "[]",
            """{"schemaVersion":1,"wide":[],"compact":{}}""",
        )) assertTrue(text, runCatching { LobbyLayoutJson.parse(text) }.isFailure)
    }

    @Test
    fun unknownBlocksAndNonNumericCoordinatesAreRejected() {
        for (text in listOf(
            """{"schemaVersion":1,"wide":{"NEW_BUTTON":{"xDp":0,"yDp":0}},"compact":{}}""",
            """{"schemaVersion":1,"wide":{"LOGO":{"xDp":"left","yDp":0}},"compact":{}}""",
            """{"schemaVersion":1,"wide":{"LOGO":{"xDp":NaN,"yDp":0}},"compact":{}}""",
        )) assertTrue(text, runCatching { LobbyLayoutJson.parse(text) }.isFailure)
    }

    @Test
    fun profileUpdateSnapshotsItsInputInsteadOfRetainingTheMutableDraftMap() {
        val draft = mutableMapOf(LobbyBlock.LOGO to LobbyOffset(5f, 6f))
        val saved = LobbyLayoutSettings().withOffsets(LobbyLayoutProfile.WIDE, draft)
        draft[LobbyBlock.LOGO] = LobbyOffset(50f, 60f)
        draft[LobbyBlock.TITLE] = LobbyOffset(80f, 90f)
        assertEquals(mapOf(LobbyBlock.LOGO to LobbyOffset(5f, 6f)), saved.wide)
        assertTrue(saved.compact.isEmpty())
    }
}
