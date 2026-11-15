package com.team.carescape.game

/** 방향지시등 방향. */
enum class TurnSide { LEFT, RIGHT }

/**
 * 엔진이 받는 차량 상태 한 장면.
 * null = 아직 모름(미수신·오류). 모르는 값은 정상이나 '켜짐'으로 취급하지 않는다.
 */
data class VehicleState(
    /** P단 + 속도 0이 확인됐는지. null이면 정차로 보지 않는다. */
    val parked: Boolean? = null,
    /** 현재 켜진 방향지시등. 꺼져 있거나 모르면 null. */
    val turnSignal: TurnSide? = null,
    val lowBeamOn: Boolean? = null,
    val wiperOn: Boolean? = null,
    /** 구동 배터리 잔량(%). 기존 앱에서 에뮬레이터 읽기 확인된 값. */
    val batteryPercent: Int? = null,
    /** 주행 가능 거리(km). 여행 도우미 이동 계획 표시 전용. */
    val rangeKm: Int? = null,
    /** 연료 잔량(%). 내연기관 차량. */
    val fuelPercent: Int? = null,
)

/** 퍼즐이 실제 차량에서 읽어야 하는 값. 못 읽으면 [Puzzle.fallback]으로 진행한다. */
enum class VehicleData { BATTERY_PERCENT }

/** 화면 단서를 가리고 있다가 차량 조작으로 드러내는 조건. */
enum class RevealCondition { WIPER_ON, LOW_BEAM_ON }

/** 객관식 정답을 실제 차량 값으로 정하는 규칙. */
enum class ChoiceRule {
    /** 배터리 50% 이상이면 0번, 미만이면 1번 보기가 정답. */
    BATTERY_HALF_OR_MORE,
}

/** 퍼즐 정답을 어떻게 입력하는지. */
sealed interface Answer {
    /** 방향지시등을 정해진 순서로 켠다. 예: 왼→오→왼. */
    data class TurnSequence(val sides: List<TurnSide>) : Answer

    /** 전조등을 켠다 (꺼진 상태에서 켜는 동작). */
    data object LowBeamOn : Answer

    /** 화면에서 코드를 입력한다. [revealWhen]이 있으면 그 조건을 만족해야 단서가 보이고 입력할 수 있다. */
    data class Code(val code: String, val revealWhen: RevealCondition? = null) : Answer

    /** 보기 중 하나를 고른다. 정답은 [correctIndex]로 고정하거나 [rule]로 차량 값에서 정한다. */
    data class Choice(
        val options: List<String>,
        val correctIndex: Int = -1,
        val rule: ChoiceRule? = null,
        val revealWhen: RevealCondition? = null,
    ) : Answer
}

data class Puzzle(
    val id: String,
    val title: String,
    /** 화면 담당이 보는 단서. */
    val screenClue: String,
    /** 소리로만 나오는 단서. 화면에 쓰지 않는다. */
    val audioClue: String,
    /** 방향 → 구체적 힌트 → 정답 순서의 3단계 힌트. */
    val hints: List<String>,
    val answer: Answer,
    val successText: String,
    /** 이 퍼즐이 읽어야 하는 실제 차량 값. */
    val needs: VehicleData? = null,
    /** [needs] 값을 읽지 못할 때 대신 내는 퍼즐. 가짜 값을 만들어 내지 않는다. */
    val fallback: Puzzle? = null,
)

/** 테마 분위기. 화면 색·연출에 쓴다. */
enum class ThemeTone { KIDS, NOIR, ADVENTURE, HISTORY }

data class Episode(
    val id: String,
    val title: String,
    val subtitle: String,
    val tone: ThemeTone,
    val intro: String,
    val puzzles: List<Puzzle>,
    val ending: String,
)
