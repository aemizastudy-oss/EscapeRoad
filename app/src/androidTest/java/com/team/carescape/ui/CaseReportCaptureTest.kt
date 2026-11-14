package com.team.carescape.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryEngine
import com.team.carescape.game.StoryLockDefinition
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryPuzzleInput
import com.team.carescape.game.TourCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 사건 종결 보고서 배치 확인용 캡처(flow3 진 엔딩 상태로 그린다). Pictures/escape-capture에 저장한다. */
@RunWith(AndroidJUnit4::class)
class CaseReportCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun captureReportSheet() {
        val episode = StoryCatalog.episodeFor(StoryCatalog.CASE_FLOW_VERSION)
        var state = StoryEngine.start(episode, StoryCatalog.CASE_FLOW_VERSION)
        episode.chapters.indices.forEach { index ->
            state = StoryEngine.returnToCar(StoryEngine.leaveCar(StoryEngine.verifiedArrival(state)))
            while (state.phase == StoryPhase.PUZZLE) {
                val input = when (val lock = state.currentPuzzle!!.lock) {
                    is StoryLockDefinition.Numeric -> StoryPuzzleInput.Numeric(lock.answer)
                    is StoryLockDefinition.Choice -> StoryPuzzleInput.Choice(lock.answer)
                    is StoryLockDefinition.Order -> StoryPuzzleInput.Order(lock.answer)
                    is StoryLockDefinition.CarSequence -> StoryPuzzleInput.CarSequence(lock.answer)
                    else -> error("unexpected")
                }
                state = StoryEngine.submitPuzzle(state, input).state
            }
            if (index < episode.finalChapterIndex) {
                state = StoryEngine.next(state)
                if (state.phase == StoryPhase.FINAL_REVEAL) state = StoryEngine.next(state)
            }
        }
        val start = 1_794_000_000_000L
        val starts = TourCatalog.checkpoints.mapIndexed { i, c -> c.id to start + i * 75L * 60_000 }.toMap()
        val ended = state
        rule.setContent { androidx.compose.foundation.layout.Box(Modifier.size(1000.dp, 640.dp)) { ReportSheet(ended, start, start + (6 * 60 + 25) * 60_000L, starts) } }
        rule.waitForIdle()
        rule.onNodeWithTag("report-ending").assertExists()
        save(rule.onRoot().captureToImage().asAndroidBitmap(), "report-true")
    }

    private fun save(bitmap: Bitmap, name: String) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/escape-capture")
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
