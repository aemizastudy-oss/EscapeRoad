package com.team.carescape.ui

import android.media.MediaPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.BgmCatalog
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 장면 배경음악 20곡이 모두 앱에 들어 있고 기기에서 열리는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class BgmFilesTest {
    private val names = listOf(
        "bgm_title", "bgm_yeoni_theme", "bgm_agency", "bgm_time_rift", "bgm_palace_wall",
        "bgm_palace_drive", "bgm_palace_explore", "bgm_sluice_drive", "bgm_sluice_explore",
        "bgm_reservoir_drive", "bgm_reservoir_explore", "bgm_museum_drive", "bgm_museum_explore",
        "bgm_lake_drive", "bgm_lake_arrival", "bgm_lake_explore", "bgm_truth",
        "bgm_ending_true", "bgm_ending_normal", "bgm_ending_bad",
    )

    @Test
    fun everySceneTrackIsBundledAndOpens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        names.forEach { name ->
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            assertNotEquals("$name missing", 0, id)
            val player = MediaPlayer.create(context, id)
            try {
                assertTrue("$name too short", player != null && player.duration > 10_000)
            } finally {
                player?.release()
            }
        }
        (0 until 5).forEach { assertTrue(BgmCatalog.driveTrack(it) in names) }
    }
}
