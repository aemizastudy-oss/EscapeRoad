package com.team.carescape.game

object StoryCatalog {
    const val LEGACY_FLOW_VERSION = 1
    const val CURRENT_FLOW_VERSION = 2

    fun episodeFor(flowVersion: Int): StoryEpisode = when (flowVersion) {
        LEGACY_FLOW_VERSION -> Siyeok.episode
        CURRENT_FLOW_VERSION -> IntegratedSiyeok.episode
        else -> throw IllegalArgumentException("지원하지 않는 스토리 진행 버전입니다.")
    }

    fun completedCount(chapterIndex: Int, phase: StoryPhase, chapterCount: Int): Int = when (phase) {
        StoryPhase.REVEALED, StoryPhase.FINAL_REVEAL -> chapterIndex + 1
        StoryPhase.ENDED -> chapterCount
        else -> chapterIndex
    }
}
