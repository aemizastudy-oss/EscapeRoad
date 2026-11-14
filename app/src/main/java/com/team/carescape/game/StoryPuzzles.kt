package com.team.carescape.game

import kotlinx.serialization.Serializable

/** 퍼즐 한 단계의 성격. 화면 라벨과 안전 안내에 쓴다. */
enum class StepKind(val label: String) {
    TESTIMONY("오디오 증언"),
    OBSERVE("현장 관찰"),
    CAR("차량 조작"),
    DEDUCE("앱 안 추리"),
    SCENE("연출"),
    VERDICT("진상 추리"),
    LOCK("자물쇠"),
}

/** 오답에 붙는 대사와 시귀 각성도 증가량(함정만 0보다 크다). */
data class WrongNote(val message: String, val penalty: Int = 0)

data class StoryPuzzleDefinition(
    val id: String,
    val title: String,
    val prompt: String,
    val hints: List<String>,
    val solution: String,
    val lock: StoryLockDefinition,
    val kind: StepKind = StepKind.LOCK,
    /** 정답 입력 키([StoryPuzzles.answerKey])로 고른 오답 대사. null이면 [wrongDefault]. */
    val feedback: (String) -> WrongNote? = { null },
    val wrongDefault: String? = null,
    /** 진상 추리: 틀려도 다음 단계로 넘어가며, 틀린 기록만 남는다(진 엔딩 조건). */
    val verdict: Boolean = false,
    /** 이동 중 들은 증언을 다시 들을 수 있는 단계. */
    val replayNarration: Boolean = false,
    /** 정답 직후 보여 줄 짧은 이야기. */
    val success: String? = null,
    /** 이 단계를 건네는 인물 id. 화면에 이름과 신분을 붙인다. null이면 지문. */
    val speaker: String? = null,
)

data class ChoiceOption(val id: String, val label: String)

/** 차량 조작 퍼즐의 입력. 실제 차량 신호 연동 전에는 같은 이름의 화면 버튼으로 받는다. */
enum class CarControl(val label: String, val short: String) {
    DOOR("운전석 문 열었다 닫기", "문"),
    WIPER("와이퍼 1회", "와이퍼"),
    HAZARD("비상등", "비상등"),
    TURN_LEFT("왼쪽 깜빡이", "← 깜빡이"),
    TURN_RIGHT("오른쪽 깜빡이", "깜빡이 →"),
    HEADLIGHT("전조등 켰다 끄기", "전조등"),
    WINDOW("창문 내리기", "창문"),
}

/** 차량 조작 퍼즐 옆에 그리는 그림. */
sealed interface CarVisual {
    data object None : CarVisual
    /** 화홍문 판문. 입력 하나가 한 칸(와이퍼=내림, 비상등=열어 둠). */
    data class Sluice(val gates: Int) : CarVisual
    /** 만석거 물길. 갈림길마다 왼쪽/오른쪽 높이와 표시. */
    data class WaterMap(val start: Int, val forks: List<Fork>) : CarVisual
    /** 봉화 화구. 전조등 횟수만큼 불이 오른다. */
    data class Beacon(val pits: Int) : CarVisual
}

data class Fork(val left: Int, val leftNote: String, val right: Int, val rightNote: String)

sealed interface StoryLockDefinition {
    data class Numeric(val answer: String) : StoryLockDefinition {
        init { require(answer.isNotEmpty() && answer.all { it in '0'..'9' }) }
    }
    data class Directions(val answer: List<LockDirection>) : StoryLockDefinition {
        init { require(answer.isNotEmpty()) }
    }
    data class Device(val channel: Int, val level: Int, val min: Int = 0, val max: Int = 5) : StoryLockDefinition {
        init { require(min <= max && channel in min..max && level in min..max) }
    }
    data class Choice(val options: List<ChoiceOption>, val answer: String) : StoryLockDefinition {
        init { require(options.map { it.id }.distinct().size == options.size && options.any { it.id == answer }) }
    }
    /** 보기 가운데 [answer]에 든 것만 순서대로 고른다(미끼 보기가 있을 수 있다). */
    data class Order(val items: List<ChoiceOption>, val answer: List<String>) : StoryLockDefinition {
        init {
            require(items.map { it.id }.distinct().size == items.size)
            require(answer.isNotEmpty() && answer.distinct().size == answer.size && answer.all { a -> items.any { it.id == a } })
        }
    }
    /** 글자 바퀴를 돌려 맞추는 자물쇠(예: 십이지 시각). [answer]는 바퀴마다 고른 칸 번호. */
    data class SymbolDial(val wheels: List<List<String>>, val answer: List<Int>) : StoryLockDefinition {
        init { require(wheels.isNotEmpty() && wheels.size == answer.size && answer.indices.all { answer[it] in wheels[it].indices }) }
    }
    /** 소지품 하나를 골라 쓴다. [pool]은 고를 수 있는 모든 소지품 id(입력 검증용). */
    data class UseItem(val answer: String, val pool: List<String>) : StoryLockDefinition {
        init { require(answer in pool && pool.distinct().size == pool.size) }
    }
    /** 앱 안에서 누르는 순서 입력(물길 고르기, 봉화 올리기). 차량 신호는 쓰지 않는다. */
    data class Sequence(
        val tokens: List<ChoiceOption>,
        val answer: List<String>,
        val visual: CarVisual = CarVisual.None,
        val maxLength: Int = answer.size + 2,
    ) : StoryLockDefinition {
        init { require(answer.isNotEmpty() && answer.all { a -> tokens.any { it.id == a } } && maxLength >= answer.size) }
    }
    data class CarSequence(
        val answer: List<CarControl>,
        val controls: List<CarControl>,
        val visual: CarVisual = CarVisual.None,
        val maxLength: Int = answer.size + 3,
    ) : StoryLockDefinition {
        init { require(answer.isNotEmpty() && answer.all { it in controls } && maxLength >= answer.size) }
    }
}

sealed interface StoryPuzzleInput {
    data class Numeric(val value: String) : StoryPuzzleInput
    data class Directions(val value: List<LockDirection>) : StoryPuzzleInput
    data class Device(val channel: Int, val level: Int) : StoryPuzzleInput
    data class Choice(val id: String) : StoryPuzzleInput
    data class Order(val ids: List<String>) : StoryPuzzleInput
    data class CarSequence(val value: List<CarControl>) : StoryPuzzleInput
    data class Dial(val value: List<Int>) : StoryPuzzleInput
    data class UseItem(val id: String) : StoryPuzzleInput
    data class Sequence(val ids: List<String>) : StoryPuzzleInput
}

enum class StoryPuzzleResult { CORRECT, WRONG, INVALID_INPUT, LOCKED }

/**
 * A receipt belongs to one checkpoint of the containing trip, never to a different mode/session.
 * flow3부터 한 장소에 여러 단계가 있다: [stepIndex]는 지금 풀 단계, [hintsShown]은 그 단계에서 연 힌트 수.
 */
@Serializable
data class StoryPuzzleProgress(
    val checkpointId: String,
    val wrongAttempts: Int = 0,
    val hintsShown: Int = 0,
    val solved: Boolean = false,
    val stepIndex: Int = 0,
    /** 이 장소에서 쌓인 시귀 각성도(힌트 2·3단계, 함정). */
    val awakening: Int = 0,
    /** 이 장소에서 연 힌트의 누적 수(단계가 바뀌어도 줄지 않는다). */
    val hintsUsed: Int = 0,
    /** 틀린 채 넘어간 진상 추리 단계 id. */
    val verdictMisses: List<String> = emptyList(),
    /** flow5 탐색 장면: 순서와 상관없이 푼 자물쇠 단계 id. */
    val solvedSteps: List<String> = emptyList(),
    /** flow5 탐색 장면: 살펴보고 기록한 조사할 곳 id. */
    val found: List<String> = emptyList(),
    /** flow5 탐색 장면: 자물쇠마다 연 힌트 수. */
    val stepHints: Map<String, Int> = emptyMap(),
)

data class StorySubmission(val state: StoryState, val result: StoryPuzzleResult, val note: String? = null)

/** Pure native-lock evaluation. No actual vehicle controls or voice recognition are involved. */
object StoryPuzzles {
    fun evaluate(puzzle: StoryPuzzleDefinition, input: StoryPuzzleInput): StoryPuzzleResult {
        val matches = when (val lock = puzzle.lock) {
            is StoryLockDefinition.Numeric -> {
                val value = (input as? StoryPuzzleInput.Numeric)?.value
                    ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.length != lock.answer.length || value.any { it !in '0'..'9' }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value == lock.answer
            }
            is StoryLockDefinition.Directions -> {
                val value = (input as? StoryPuzzleInput.Directions)?.value
                    ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.size != lock.answer.size) return StoryPuzzleResult.INVALID_INPUT
                value == lock.answer
            }
            is StoryLockDefinition.Device -> {
                val value = input as? StoryPuzzleInput.Device ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.channel !in lock.min..lock.max || value.level !in lock.min..lock.max) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value.channel == lock.channel && value.level == lock.level
            }
            is StoryLockDefinition.Choice -> {
                val id = (input as? StoryPuzzleInput.Choice)?.id ?: return StoryPuzzleResult.INVALID_INPUT
                if (lock.options.none { it.id == id }) return StoryPuzzleResult.INVALID_INPUT
                id == lock.answer
            }
            is StoryLockDefinition.Order -> {
                val ids = (input as? StoryPuzzleInput.Order)?.ids ?: return StoryPuzzleResult.INVALID_INPUT
                if (ids.isEmpty() || ids.distinct().size != ids.size || ids.any { id -> lock.items.none { it.id == id } }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                ids == lock.answer
            }
            is StoryLockDefinition.CarSequence -> {
                val value = (input as? StoryPuzzleInput.CarSequence)?.value ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.isEmpty() || value.size > lock.maxLength || value.any { it !in lock.controls }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value == lock.answer
            }
            is StoryLockDefinition.SymbolDial -> {
                val value = (input as? StoryPuzzleInput.Dial)?.value ?: return StoryPuzzleResult.INVALID_INPUT
                if (value.size != lock.wheels.size || value.indices.any { value[it] !in lock.wheels[it].indices }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                value == lock.answer
            }
            is StoryLockDefinition.UseItem -> {
                val id = (input as? StoryPuzzleInput.UseItem)?.id ?: return StoryPuzzleResult.INVALID_INPUT
                if (id !in lock.pool) return StoryPuzzleResult.INVALID_INPUT
                id == lock.answer
            }
            is StoryLockDefinition.Sequence -> {
                val ids = (input as? StoryPuzzleInput.Sequence)?.ids ?: return StoryPuzzleResult.INVALID_INPUT
                if (ids.isEmpty() || ids.size > lock.maxLength || ids.any { id -> lock.tokens.none { it.id == id } }) {
                    return StoryPuzzleResult.INVALID_INPUT
                }
                ids == lock.answer
            }
        }
        return if (matches) StoryPuzzleResult.CORRECT else StoryPuzzleResult.WRONG
    }

    /** 오답 대사를 고르는 안정된 문자열 키. */
    fun answerKey(input: StoryPuzzleInput): String = when (input) {
        is StoryPuzzleInput.Numeric -> input.value
        is StoryPuzzleInput.Directions -> input.value.joinToString(",") { it.name }
        is StoryPuzzleInput.Device -> "${input.channel},${input.level}"
        is StoryPuzzleInput.Choice -> input.id
        is StoryPuzzleInput.Order -> input.ids.joinToString(",")
        is StoryPuzzleInput.CarSequence -> input.value.joinToString(",") { it.name }
        is StoryPuzzleInput.Dial -> input.value.joinToString(",")
        is StoryPuzzleInput.UseItem -> input.id
        is StoryPuzzleInput.Sequence -> input.ids.joinToString(",")
    }

    /** flow3 힌트 비용: 1단계 0, 2단계 +1, 3단계 이상 +2. */
    fun hintCost(hintIndex: Int): Int = when (hintIndex) { 0 -> 0; 1 -> 1; else -> 2 }
}
