package com.team.carescape.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 음성 녹음·생성용 대본. 모든 장면 대사에 고정 번호(voice id)를 붙인다.
 * 녹음 파일 이름은 `voice_<id>`(예: voice_prologue_03)로 맞춘다.
 * 환경 변수 EXPORT_VOICE_SCRIPT=<폴더>가 있으면 그 폴더에 script.csv와 script.md를 쓴다.
 */
class VoiceScriptTest {
    data class VoiceLine(val id: String, val section: String, val speaker: String, val text: String, val bg: String)

    private val episode = StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION)

    private fun lines(): List<VoiceLine> {
        val out = mutableListOf<VoiceLine>()
        fun add(prefix: String, section: String, list: List<Line>) {
            var bg = ""
            list.forEachIndexed { i, l ->
                l.bg?.let { bg = it.name }
                val who = episode.speakerFor(l.speaker)?.name ?: "내레이션"
                out += VoiceLine("%s_%02d".format(prefix, i + 1), section, who, l.text, bg)
            }
        }
        add("prologue", "프롤로그", SiyeokScenes.prologue)
        episode.chapters.forEachIndexed { i, c ->
            val n = i + 1
            add("ch${n}_travel", "${n}장 ${c.place} · 이동 중(주행 중 음성)", c.travelScene)
            add("ch${n}_arrival", "${n}장 ${c.place} · 도착", c.arrivalScene)
            add("ch${n}_reveal", "${n}장 ${c.place} · 단서 해금", c.revealScene)
        }
        EndingKind.entries.forEach { k ->
            add("ending_${k.name.lowercase()}", "엔딩 · ${k.label}", SiyeokScenes.endingDrama.getValue(k))
        }
        return out
    }

    @Test fun everyLineHasAUniqueVoiceId() {
        val all = lines()
        assertEquals(all.map { it.id }.distinct().size, all.size)
        assertTrue(all.size > 80)
        assertTrue(all.all { it.id.matches(Regex("[a-z0-9_]+")) })

        val dir = System.getenv("EXPORT_VOICE_SCRIPT") ?: return
        File(dir).mkdirs()
        fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        File(dir, "script.csv").writeText(
            "﻿id,파일 이름,구간,화자,대사,배경\n" + all.joinToString("\n") {
                listOf(it.id, "voice_${it.id}.mp3", it.section, it.speaker, it.text, it.bg).joinToString(",") { v -> csv(v) }
            } + "\n",
        )
        val md = StringBuilder()
        val count = all.groupingBy { it.speaker }.eachCount()
        md.append("# 시역 음성 대본\n\n총 ${all.size}줄 · ").append(count.entries.joinToString(" · ") { "${it.key} ${it.value}" }).append("\n")
        all.groupBy { it.section }.forEach { (section, list) ->
            md.append("\n## ").append(section).append("\n\n| 번호 | 화자 | 대사 |\n| --- | --- | --- |\n")
            list.forEach { md.append("| `").append(it.id).append("` | ").append(it.speaker).append(" | ").append(it.text.replace("|", "\\|")).append(" |\n") }
        }
        File(dir, "script.md").writeText(md.toString())
    }
}
