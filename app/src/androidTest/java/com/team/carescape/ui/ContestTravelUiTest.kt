package com.team.carescape.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.travel.GuideStops
import com.team.carescape.travel.Interest
import com.team.carescape.travel.SavedPlace
import com.team.carescape.travel.TravelParty
import com.team.carescape.travel.TripLength
import com.team.carescape.travel.TripPlan
import com.team.carescape.travel.TripProfile
import com.team.carescape.travel.TripProfileStore
import java.util.UUID
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plain course UI and an isolated preferences file; no app ViewModel, SDK or user trip is opened. */
@RunWith(AndroidJUnit4::class)
class ContestTravelUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unrevealedFinalStopHasOnlyFourPublicCardsAndALockedNotice() {
        val publicStops = GuideStops.all.dropLast(1)
        rule.setContent {
            CourseStopCards(publicStops, selectedStop = 0, dayStops = listOf(0, 1),
                finalLocked = true, onSelectStop = {}, modifier = Modifier.fillMaxSize())
        }

        rule.onNodeWithTag("travel-course").assertIsDisplayed()
        rule.onAllNodes(hasClickAction()).assertCountEquals(4)
        publicStops.forEach { stop ->
            rule.onNodeWithTag("travel-course-stop-${stop.index}").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText(stop.name).assertIsDisplayed()
        }
        rule.onNodeWithTag("travel-course-stop-${GuideStops.all.last().index}").assertDoesNotExist()
        rule.onNodeWithText(GuideStops.all.last().name, substring = true, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag("travel-course-final-locked").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("마지막 장소는 공개 후 표시됩니다.").assertIsDisplayed()
    }

    @Test fun touchingACourseCardCallsBackWithItsStopIndex() {
        val selections = mutableListOf<Int>()
        val publicStops = GuideStops.all.dropLast(1)
        val target = publicStops[2]
        rule.setContent {
            CourseStopCards(publicStops, selectedStop = 0, dayStops = listOf(0, 1, 2),
                finalLocked = true, onSelectStop = { selections += it }, modifier = Modifier.fillMaxSize())
        }

        rule.onNodeWithTag("travel-course-stop-${target.index}")
            .performScrollTo().assertIsDisplayed().performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(listOf(target.index), selections) }
    }

    @Test fun curatedPlanPreservesProfileIgnoresLegacyPlanAndReloadsNavigationProvenance() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val testFile = "contest_travel_test_${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(testFile, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                assertEquals("The store must keep using its existing logical preferences file", "travel_guide", name)
                return base.getSharedPreferences(testFile, mode)
            }
        }
        val json = Json
        val profile = TripProfile(length = TripLength.ONE_NIGHT, startDate = "2026-11-15",
            companion = TravelParty.FAMILY, interests = setOf(Interest.HISTORY))
        val legacy = SavedPlace(id = "old-search-result", name = "이전 검색 결과", category = "음식점",
            address = "이전 주소", source = "이전 API 검색", stopIndex = 0, tab = "FOOD")
        val profileJson = json.encodeToString(TripProfile.serializer(), profile)
        val legacyJson = json.encodeToString(TripPlan.serializer(), TripPlan(saved = listOf(legacy)))

        try {
            assertTrue(prefs.edit().putString("profile", profileJson).putString("plan", legacyJson).commit())
            val store = TripProfileStore(isolated)
            assertEquals(profile, store.load())
            assertEquals("Legacy search results must not become curated cards", TripPlan(), store.loadPlan())
            assertTrue(prefs.edit().putString("curated_plan_v1", "invalid-json").commit())
            assertEquals("A broken curated plan must not fall back to legacy search results", TripPlan(), store.loadPlan())

            val place = SavedPlace(id = "curated-test-parking", name = "테스트 주차장", category = "주차",
                address = "공식 안내 주소", lat = null, lng = null, source = "공식 안내",
                stopIndex = 0, tab = "PARKING", sourceUrl = "https://www.suwon.go.kr/",
                note = "주차장 입구 좌표는 확인되지 않았습니다.", navigationName = "화성행궁",
                navigationLat = 37.281791, navigationLng = 127.013251,
                navigationLabel = "주변 관광지로 안내")
            val plan = TripPlan(saved = listOf(place))
            store.savePlan(plan)

            val reloaded = TripProfileStore(isolated)
            assertEquals(profile, reloaded.load())
            assertEquals(plan, reloaded.loadPlan())
            val saved = reloaded.loadPlan().saved.single()
            assertNull(saved.lat)
            assertNull(saved.lng)
            assertEquals(place.sourceUrl, saved.sourceUrl)
            assertEquals(place.navigationName, saved.navigationName)
            assertEquals(place.navigationLat, saved.navigationLat)
            assertEquals(place.navigationLng, saved.navigationLng)
            assertEquals("주변 관광지로 안내", saved.navigationLabel)
            assertTrue(prefs.contains("curated_plan_v1"))
            assertEquals("Saving curated cards must not overwrite old search data", legacyJson, prefs.getString("plan", null))
            assertEquals("Saving curated cards must not rewrite the profile", profileJson, prefs.getString("profile", null))
        } finally {
            // This UUID-named file belongs only to this test; the real travel_guide file is never opened.
            prefs.edit().clear().commit()
            base.deleteSharedPreferences(testFile)
        }
    }
}
