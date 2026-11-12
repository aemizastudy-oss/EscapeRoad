package com.example.roadescape.game

import com.example.roadescape.model.GamePackage
import com.example.roadescape.model.LockDefinition

object ContentValidator {
    /** Invalid content must be rejected before a session is created. */
    fun validate(content: GamePackage): List<String> = buildList {
        if (content.id.isBlank()) add("패키지 ID가 비어 있습니다.")
        if (content.contentVersion < 1) add("콘텐츠 버전은 양수여야 합니다.")
        if (content.title.isBlank()) add("패키지 제목이 비어 있습니다.")
        if (content.checkpoints.isEmpty()) add("경유지가 없습니다.")
        fun validateIds(label: String, ids: List<String>) {
            if (ids.any(String::isBlank)) add("$label ID가 비어 있습니다.")
            if (ids.toSet().size != ids.size) add("$label ID가 중복됩니다.")
        }
        validateIds("경유지", content.checkpoints.map { it.id })
        validateIds("퍼즐", content.puzzles.map { it.id })
        validateIds("단서", content.clues.map { it.id })
        if (content.checkpoints.map { it.puzzleId }.toSet().size != content.checkpoints.size) {
            add("하나의 퍼즐을 여러 경유지가 공유할 수 없습니다.")
        }
        val puzzleIds = content.puzzles.map { it.id }.toSet()
        val clueIds = content.clues.map { it.id }.toSet()
        content.checkpoints.forEach {
            if (it.puzzleId !in puzzleIds) add("경유지 ${it.id}의 퍼즐 참조가 없습니다.")
            if (it.title.isBlank()) add("경유지 ${it.id}의 제목이 비어 있습니다.")
        }
        content.puzzles.forEach { puzzle ->
            if (puzzle.title.isBlank() || puzzle.prompt.isBlank() || puzzle.solution.isBlank()) {
                add("퍼즐 ${puzzle.id}의 제목·지시문·해설이 필요합니다.")
            }
            if (puzzle.hints.size != 2 || puzzle.hints.any(String::isBlank)) {
                add("퍼즐 ${puzzle.id}에는 비어 있지 않은 힌트 2개가 필요합니다.")
            }
            if (puzzle.rewardClueIds.any { it !in clueIds }) add("퍼즐 ${puzzle.id}의 단서 참조가 없습니다.")
            if (puzzle.rewardClueIds.toSet().size != puzzle.rewardClueIds.size) {
                add("퍼즐 ${puzzle.id}의 단서 보상이 중복됩니다.")
            }
            when (val lock = puzzle.lock) {
                is LockDefinition.Numeric -> if (lock.length !in 1..12 || lock.answer.length != lock.length ||
                    lock.answer.any { it !in '0'..'9' }
                ) add("퍼즐 ${puzzle.id}의 숫자 정답 형식이 잘못됐습니다.")
                is LockDefinition.Sequence -> if (lock.answer.isEmpty() || lock.answer.size > 12) {
                    add("퍼즐 ${puzzle.id}의 순서 정답 길이가 잘못됐습니다.")
                }
                is LockDefinition.StateCombination -> if (lock.min != 0 || lock.max != 5 ||
                    lock.channel !in lock.min..lock.max || lock.level !in lock.min..lock.max
                ) add("퍼즐 ${puzzle.id}의 가상 장치 범위 또는 정답이 잘못됐습니다.")
            }
        }
        content.clues.forEach {
            if (it.title.isBlank() || it.body.isBlank()) add("단서 ${it.id}의 제목과 내용이 필요합니다.")
        }
    }
}
