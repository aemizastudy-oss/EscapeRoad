package com.team.carescape.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.StoryCatalog
import com.team.carescape.game.StoryPhase
import com.team.carescape.game.StoryPuzzleProgress
import com.team.carescape.game.StoryState
import com.team.carescape.game.TourCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 탐색 장면 배치 확인용 캡처. 장소마다 처음 모습과 숨은 곳까지 다 드러난 모습을
 * 조사할 곳 테두리(빨강 = 자물쇠, 청록 = 찾을 것, 흰색 = 살펴보기)와 함께 그려 Pictures/escape-capture에 저장한다.
 */
@RunWith(AndroidJUnit4::class)
class ExploreSceneCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun captureEveryExploreScene() {
        val episode = StoryCatalog.episodeFor(StoryCatalog.EXPLORE_FLOW_VERSION)
        fun stateFor(index: Int, revealed: Boolean): StoryState {
            val scene = episode.chapters[index].scene!!
            val requires = scene.hotspots.mapNotNull { it.requires }.toSet()
            val progress = StoryPuzzleProgress(
                TourCatalog.checkpoints[index].id,
                found = if (revealed) scene.hotspots.filter { it.record }.map { it.id } else emptyList(),
                solvedSteps = if (revealed) episode.chapters[index].puzzleSteps.map { it.id }.filter { it in requires } else emptyList(),
            )
            return StoryState(episode, index, StoryPhase.PUZZLE, flowVersion = StoryCatalog.EXPLORE_FLOW_VERSION,
                puzzleProgress = listOf(progress))
        }
        var current by mutableStateOf(stateFor(0, false))
        rule.setContent { ExploreSceneLayer(current, null, null, ready = false, onTouch = {}, outline = true) }
        episode.chapters.indices.forEach { index ->
            listOf(false, true).forEach { revealed ->
                rule.runOnIdle { current = stateFor(index, revealed) }
                rule.waitForIdle()
                save(rule.onRoot().captureToImage().asAndroidBitmap(), "scene-${index + 1}-${if (revealed) "revealed" else "start"}")
            }
        }
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
