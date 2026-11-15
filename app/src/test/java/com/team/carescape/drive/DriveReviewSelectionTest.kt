package com.team.carescape.drive

import com.team.carescape.data.StoredTrip
import com.team.carescape.game.StoryPhase
import com.team.carescape.model.TourMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveReviewSelectionTest {
    private fun trip(mode: TourMode) = StoredTrip(mode, mode.name, chapterIndex = 0,
        phase = StoryPhase.EN_ROUTE, startedAtEpochMillis = 1_800_000_000_000)

    @Test fun reviewSelectionNeverUsesAnOlderLiveOrMockTrip() {
        assertNull(DriveService.activeTrip(listOf(trip(TourMode.LIVE), trip(TourMode.MOCK)), reviewMode = true))
        val review = trip(TourMode.REVIEW)
        assertEquals(review, DriveService.activeTrip(listOf(trip(TourMode.LIVE), review, trip(TourMode.MOCK)), reviewMode = true))
    }

    @Test fun normalSelectionNeverFallsBackToAReviewTrip() {
        assertNull(DriveService.activeTrip(listOf(trip(TourMode.REVIEW))))
        val live = trip(TourMode.LIVE)
        assertEquals(live, DriveService.activeTrip(listOf(trip(TourMode.REVIEW), trip(TourMode.MOCK), live)))
    }

    @Test fun completedReviewDoesNotRestartOrFallBackToLive() {
        val review = trip(TourMode.REVIEW).copy(chapterIndex = 4, phase = StoryPhase.ENDED)
        assertNull(DriveService.activeTrip(listOf(review, trip(TourMode.LIVE)), reviewMode = true))
    }

    @Test fun existingMockFallbackRemainsAvailableOutsideReviewMode() {
        val mock = trip(TourMode.MOCK)
        assertEquals(mock, DriveService.activeTrip(listOf(trip(TourMode.REVIEW), mock)))
    }
}
