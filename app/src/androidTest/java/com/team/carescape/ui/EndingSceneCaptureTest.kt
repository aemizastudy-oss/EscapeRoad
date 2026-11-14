package com.team.carescape.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.EndingKind
import com.team.carescape.game.SiyeokScenes
import com.team.carescape.game.StoryCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 엔딩 드라마 확인용 캡처. 엔딩마다 배경이나 화자가 바뀌는 줄만 골라 그려 Pictures/escape-capture에 저장한다.
 */
@RunWith(AndroidJUnit4::class)
class EndingSceneCaptureTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun captureEveryEndingDrama() {
        val episode = StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION)
        var kind by mutableStateOf(EndingKind.TRUE)
        var index by mutableIntStateOf(0)
        rule.setContent {
            ScenePlayer(episode, SiyeokScenes.endingDrama.getValue(kind), index, "Epilogue", onNext = {}, onSkip = {}, onPrev = {})
        }
        EndingKind.entries.forEach { k ->
            val lines = SiyeokScenes.endingDrama.getValue(k)
            // 배경이 바뀌는 줄, 화자가 바뀌는 줄, 마지막 줄
            val picks = lines.indices.filter { i -> i == lines.lastIndex || lines[i].bg != null || (i > 0 && lines[i].speaker != lines[i - 1].speaker && lines[i].speaker != null) }
            picks.forEach { i ->
                rule.runOnIdle { kind = k; index = i }
                rule.waitForIdle()
                save(rule.onRoot().captureToImage().asAndroidBitmap(), "ending-${k.name.lowercase()}-${"%02d".format(i + 1)}")
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
