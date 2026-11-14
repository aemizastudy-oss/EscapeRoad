package com.team.carescape.game

/** 녹음 대본의 한 줄. [id]는 음성 파일 이름(`voice_<id>`)과 같다. */
data class VoiceLine(val id: String, val section: String, val speaker: String, val text: String, val bg: String)

/**
 * 장면 대사마다 고정 번호를 붙인다. 대본(docs/voice/script.csv)과 앱 재생이 같은 번호를 쓴다.
 * 번호는 장면 안 순서라서, 줄을 넣거나 빼면 뒤 번호가 밀린다(녹음 파일 이름도 함께 확인한다).
 */
object VoiceCatalog {
    fun lines(episode: StoryEpisode = StoryCatalog.episodeFor(StoryCatalog.CURRENT_FLOW_VERSION)): List<VoiceLine> {
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

    /** 대사 글 → 음성 번호. 같은 글이 여러 번 나오면 처음 번호를 쓴다(같은 녹음을 다시 쓴다). */
    private val byText: Map<String, String> by lazy {
        lines().reversed().associate { it.text.trim() to it.id }
    }

    /** 이 글에 붙은 음성 번호. 대본에 없는 글이면 null(기기 음성으로 읽는다). */
    fun idFor(text: String): String? = byText[text.trim()]
}
