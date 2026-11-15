package com.team.carescape.game

/**
 * 배경음악 한 곡. [name]은 res/raw 파일 이름(확장자 없이)이다.
 * 파일이 없으면 [alt]를 찾고, 그것도 없으면 조용히 넘어간다.
 * 반복하지 않는 곡은 끝나면 [then]으로 이어진다.
 */
data class BgmCue(val name: String, val loop: Boolean = true, val then: String? = null, val alt: String? = null)

/** 「시역」 장면별 배경음악. 화면 상태만 보고 고르므로 단위 테스트로 확인한다. */
object BgmCatalog {
    val title = BgmCue("bgm_title")
    val yeoni = BgmCue("bgm_yeoni_theme")

    /** 운전 중(DriveAudio) 곡. 장소 순서대로이고, 범위를 벗어나면 마지막 장소 곡을 쓴다. */
    fun driveTrack(chapterIndex: Int): String = drive[chapterIndex.coerceIn(0, drive.lastIndex)]

    private val drive = listOf("bgm_palace_drive", "bgm_sluice_drive", "bgm_reservoir_drive", "bgm_museum_drive", "bgm_lake_drive")
    private val explore = listOf("bgm_palace_explore", "bgm_sluice_explore", "bgm_reservoir_explore", "bgm_museum_explore", "bgm_lake_explore")

    /** 프롤로그: 지금 줄의 배경에 따라 관제실 → 시간 이동 → 행궁 담장 밖. */
    fun prologue(bg: SceneBg?): BgmCue = when (bg) {
        SceneBg.TIME_RIFT -> BgmCue("bgm_time_rift", loop = false)
        SceneBg.PALACE_NIGHT, SceneBg.PALACE_DUSK -> BgmCue("bgm_palace_wall")
        else -> BgmCue("bgm_agency", alt = "bgm_title")
    }

    /** 엔딩 곡은 한 번만 틀고, 기록 화면에서는 연이의 테마로 넘어간다. */
    fun ending(kind: EndingKind?): BgmCue = when (kind) {
        EndingKind.TRUE -> BgmCue("bgm_ending_true", loop = false, then = yeoni.name)
        EndingKind.NORMAL -> BgmCue("bgm_ending_normal", loop = false, then = yeoni.name)
        EndingKind.BAD -> BgmCue("bgm_ending_bad", loop = false, then = yeoni.name)
        null -> yeoni
    }

    fun story(s: StoryState): BgmCue? {
        if (s.episode.id != Siyeok.episode.id) return null
        val i = s.chapterIndex.coerceIn(0, explore.lastIndex)
        return when (s.phase) {
            StoryPhase.EN_ROUTE -> BgmCue(drive[i])
            StoryPhase.ENDED -> ending(s.endingKind)
            StoryPhase.ARRIVED -> if (s.isFinalChapter) BgmCue("bgm_lake_arrival", loop = false, then = explore[i]) else BgmCue(explore[i])
            StoryPhase.PUZZLE -> if (s.exploreFocus?.startsWith("c5_truth") == true) BgmCue("bgm_truth") else BgmCue(explore[i])
            else -> BgmCue(explore[i])
        }
    }
}
