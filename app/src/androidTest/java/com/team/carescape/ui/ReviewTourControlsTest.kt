package com.team.carescape.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReviewTourControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modeChoiceExplainsActualWaitAndEmitsExplicitChoice() {
        var choice: Boolean? = null
        compose.setContent { MaterialTheme {
            ReviewTourDialogContent(false, true, false, "", { choice = it }, {}, {})
        } }
        compose.onNodeWithText("500m 반경 검사", substring = true).assertExists()
        compose.onNodeWithText("실제 15분", substring = true).assertExists()
        compose.onNodeWithTag("review-location-inside").assertDoesNotExist()
        compose.onNodeWithTag("review-mode-select").performClick()
        assertEquals(true, choice)
    }

    @Test fun reviewPositionsAreExplicitAndUnavailableActionsStayDisabled() {
        val positions = mutableListOf<Boolean>()
        compose.setContent { MaterialTheme {
            ReviewTourDialogContent(true, false, true, "시연 위치 미입력", {}, positions::add, {})
        } }
        compose.onNodeWithTag("live-mode-select").assertIsNotEnabled()
        compose.onNodeWithTag("review-location-inside").performClick()
        compose.onNodeWithTag("review-location-outside").performClick()
        assertEquals(listOf(true, false), positions)
    }
}
