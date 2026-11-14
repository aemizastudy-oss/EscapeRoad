package com.team.carescape.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.team.carescape.game.SiyeokScenes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 프롤로그 17줄마다 녹음 파일이 들어 있고, 기기에서 실제로 재생이 시작되는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class VoicePlaybackTest {
    @Test
    fun everyPrologueLinePlaysItsRecording() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val player = VoicePlayer(instrumentation.targetContext)
            SiyeokScenes.prologue.forEachIndexed { i, line ->
                assertTrue("prologue_${i + 1} file", player.has(line.text))
                assertTrue("prologue_${i + 1} play", player.play(line.text))
                assertTrue(player.playing)
                player.stop()
                assertFalse(player.playing)
            }
            assertFalse(player.has("대본에 없는 글"))
            assertFalse(player.play("대본에 없는 글"))
        }
    }
}
