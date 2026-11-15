package com.team.carescape.travel

import android.content.Context

/** 관광 15분 동안 볼 것 하나. [inGame]이면 이 장소의 수수께끼에 쓰이니 눈여겨보라고 표시한다(정답은 적지 않는다). */
data class LookFor(val title: String, val detail: String, val inGame: Boolean = false)

data class TourSpot(val checkpointId: String, val looks: List<LookFor>, val photo: String, val note: String? = null)

/**
 * 장소별 관광 15분 가이드. 퍼즐 정답은 적지 않고, 내려서 무엇을 봐야 하는지만 알려 준다.
 * 현장 표기·전시 구성은 바뀔 수 있어 [TourSpot.note]로 방문 전 확인을 안내한다.
 */
object TourGuide {
    val spots = listOf(
        TourSpot(
            "suwon_01",
            listOf(
                LookFor("정문 신풍루 지나기", "행궁의 정문이에요. 문루 이름 현판부터 보고 들어가세요."),
                LookFor("봉수당 현판 읽기", "혜경궁의 회갑 잔치가 열린 전각이에요. 현판 세 글자를 기억해 두세요.", inGame = true),
                LookFor("행궁 마당 한 바퀴", "전각과 담장 사이를 천천히 걸어 보세요. 잔치를 준비하던 마당이에요."),
            ),
            photo = "신풍루 앞 광장에서 정문 전체가 들어오게",
        ),
        TourSpot(
            "suwon_02",
            listOf(
                LookFor("수문의 무지개 구멍 세어 보기", "화홍문 아래로 물이 지나는 무지개 모양 구멍(홍예)이 몇 칸인지 세어 보세요.", inGame = true),
                LookFor("물소리 듣기", "수문 아래로 흐르는 물소리에 귀 기울여 보세요. 이야기 속 놈들이 건너지 못하는 물이에요."),
                LookFor("방화수류정 바라보기", "화홍문 가까운 언덕 위 정자예요. 수원천과 성곽이 함께 보여요."),
            ),
            photo = "수원천 산책로에서 화홍문 정면, 물이 함께 나오게",
        ),
        TourSpot(
            "suwon_03",
            listOf(
                LookFor("둑 위 정자 현판 보기", "만석거 둑 위 정자의 현판 한자를 모양까지 눈여겨보세요.", inGame = true),
                LookFor("둑길 걷기", "정조 때 쌓은 저수지의 둑이에요. 물이 어디로 흘러 나가는지 살펴보세요."),
                LookFor("안내판의 축조 연도", "안내판에서 저수지를 쌓은 해를 찾아보세요.", inGame = true),
            ),
            photo = "둑 위에서 정자와 저수지가 함께 들어오게",
            note = "정자 현판 표기는 현장에서 다시 확인해 주세요.",
        ),
        TourSpot(
            "suwon_04",
            listOf(
                LookFor("기증 자료 전시 보기", "이 박물관에 자료를 기증한 수집가의 전시를 찾아 어떤 옛 기록이 있는지 살펴보세요.", inGame = true),
                LookFor("옛 수원 지도", "물길과 저수지가 그려진 옛 지도를 찾아보세요."),
                LookFor("관람 시간 확인", "박물관은 월요일에 쉬는 경우가 많아요. 방문 전에 공식 안내를 확인하세요."),
            ),
            photo = "박물관 입구 앞",
            note = "전시 구성은 바뀔 수 있어요. 방문 전에 공식 안내를 확인해 주세요.",
        ),
        TourSpot(
            "suwon_05",
            listOf(
                LookFor("두 호수 찾기", "광교산 아래 호수 두 개가 이어져 있어요. 어느 쪽이 위이고 아래인지 살펴보세요.", inGame = true),
                LookFor("둑길 산책", "호수 둘레길을 따라 걸어 보세요."),
                LookFor("해 질 녘 물빛", "해가 질 무렵 물빛이 바뀌는 순간을 놓치지 마세요."),
            ),
            photo = "호숫가 둑길에서 물과 하늘이 함께 들어오게",
        ),
    )

    fun spot(checkpointId: String): TourSpot? = spots.firstOrNull { it.checkpointId == checkpointId }
}

/** 관광 가이드 체크 표시. 방탈출 진행 기록과 따로 기기에만 저장한다. */
class TourCheckStore(context: Context) {
    private val prefs = context.getSharedPreferences("travel_guide", Context.MODE_PRIVATE)
    fun load(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty().toSet()
    fun save(checks: Set<String>) { prefs.edit().putStringSet(KEY, checks).apply() }

    companion object {
        private const val KEY = "tour_checks_v1"
        fun key(checkpointId: String, item: Int) = "$checkpointId#$item"
    }
}
