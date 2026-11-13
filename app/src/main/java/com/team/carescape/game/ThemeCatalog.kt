package com.team.carescape.game

/**
 * 메인 화면의 테마 카드.
 * [playable]이 false면 시연용으로 카드만 보여 주고 고를 수 없다. [episode]는 퍼즐형 콘텐츠(테스트용으로 보존), [story]는 스토리형.
 */
data class ThemeCard(
    val mode: String,
    val title: String,
    val subtitle: String,
    val tone: ThemeTone,
    val audience: String,
    val playTime: String,
    val description: String,
    val episode: Episode?,
    val story: StoryEpisode? = null,
    val playable: Boolean = false,
)

object ThemeCatalog {
    /** 앱 이름은 여기서만 바꾼다. 공모전·마켓 규정 확인 전까지 임시값. */
    const val APP_NAME = "Escape Road"

    val cards = listOf(
        ThemeCard(
            mode = "MAIN CASE",
            title = Siyeok.episode.title,
            subtitle = Siyeok.episode.subtitle,
            tone = ThemeTone.HISTORY,
            audience = "성인·청소년",
            playTime = "반나절 · 장소 5곳",
            description = "1795년 화성에 퍼진 역병. 물길을 따라 치료제의 재료와 비방을 모아 백신을 전달한다.",
            episode = null,
            story = IntegratedSiyeok.episode,
            playable = true,
        ),
        // 아래 테마는 시연용으로 카드만 보여 준다(2026-11-12 팀 결정). 콘텐츠와 테스트는 보존한다.
        ThemeCard(
            mode = "MODE 1",
            title = Case001.episode.title,
            subtitle = Case001.episode.subtitle,
            tone = ThemeTone.NOIR,
            audience = "성인·청소년",
            playTime = "15~25분",
            description = "차량에 남은 기록으로 10년 전 실종 사건의 공백 17분을 밝힌다.",
            episode = Case001.episode,
        ),
        ThemeCard(
            mode = "MODE 2",
            title = CarDetective.episode.title,
            subtitle = CarDetective.episode.subtitle,
            tone = ThemeTone.KIDS,
            audience = "유아·초등학생",
            playTime = "5~10분",
            description = "자동차 친구들의 문제를 풀며 우리 차와 친해지는 탐정 놀이.",
            episode = CarDetective.episode,
        ),
        ThemeCard(
            mode = "MODE 3",
            title = "THE LOST ROUTE",
            subtitle = "사라진 여행의 지도",
            tone = ThemeTone.ADVENTURE,
            audience = "가족",
            playTime = "30~60분",
            description = "세 장소를 찾아가 가족이 함께 단서를 모아 사라진 지도를 완성한다.",
            episode = null,
        ),
    )
}
