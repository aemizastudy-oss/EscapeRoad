package com.team.carescape.game

object StoryCatalog {
    const val LEGACY_FLOW_VERSION = 1
    /** v0.4.0 임시 연동 자물쇠(장소당 1개). 기존 저장 기록을 그대로 읽는다. */
    const val INTEGRATED_FLOW_VERSION = 2
    /** 정식 퍼즐판: 장소당 여러 단계, 사건 기록부, 각성도와 엔딩 분기. 새 여행은 이 버전으로 시작한다. */
    const val CASE_FLOW_VERSION = 3
    const val CURRENT_FLOW_VERSION = CASE_FLOW_VERSION

    fun episodeFor(flowVersion: Int): StoryEpisode = when (flowVersion) {
        LEGACY_FLOW_VERSION -> Siyeok.episode
        INTEGRATED_FLOW_VERSION -> IntegratedSiyeok.episode
        CASE_FLOW_VERSION -> SiyeokCase.episode
        else -> throw IllegalArgumentException("지원하지 않는 스토리 진행 버전입니다.")
    }

    /** flow2 이상은 관광 후 native 퍼즐을 풀어야 단서가 열린다. */
    fun usesPuzzles(flowVersion: Int): Boolean = flowVersion >= INTEGRATED_FLOW_VERSION

    /** flow3부터 힌트 비용·함정이 시귀 각성도에 쌓이고 엔딩이 나뉜다. */
    fun usesAwakening(flowVersion: Int): Boolean = flowVersion >= CASE_FLOW_VERSION

    fun completedCount(chapterIndex: Int, phase: StoryPhase, chapterCount: Int): Int = when (phase) {
        StoryPhase.REVEALED, StoryPhase.FINAL_REVEAL -> chapterIndex + 1
        StoryPhase.ENDED -> chapterCount
        else -> chapterIndex
    }
}
