package com.team.carescape.game

/** MODE 2 · CAR DETECTIVE - 꼬마 자동차 탐정단 (어린이 교육형, 약 5~10분). 문구는 시연용 초안. */
object CarDetective {
    val episode = Episode(
        id = "car-detective",
        title = "CAR DETECTIVE",
        subtitle = "꼬마 자동차 탐정단",
        tone = ThemeTone.KIDS,
        intro = "미래 모빌리티 마을의 자동차 친구들이 하나둘 멈춰 버렸어요! 정비 로봇이 꼬마 탐정을 찾고 있어요. " +
            "소리 탐정은 로봇 목소리를 잘 듣고, 운전석 탐정은 화면을 보며 차를 조작해요.",
        puzzles = listOf(
            Puzzle(
                id = "hello-signal",
                title = "미션 1 · 자동차 친구에게 인사하기",
                screenClue = "자동차 친구가 우리를 알아보지 못해요. 자동차들은 깜빡이로 인사한대요.",
                audioClue = "삐빅! 자동차 인사법은 왼쪽 한 번, 오른쪽 한 번이야!",
                hints = listOf(
                    "소리 탐정에게 로봇이 알려 준 방향을 물어보세요.",
                    "핸들 옆 방향지시등 레버를 한 방향씩 켜요.",
                    "왼쪽 → 오른쪽 순서로 켜세요.",
                ),
                answer = Answer.TurnSequence(listOf(TurnSide.LEFT, TurnSide.RIGHT)),
                successText = "자동차 친구가 반갑게 깜빡였어요! 방향지시등은 내가 갈 방향을 다른 차에 알려 주는 약속이에요.",
            ),
            Puzzle(
                id = "energy",
                title = "미션 2 · 에너지가 부족한 친구",
                screenClue = "우리 차의 에너지 막대를 보세요. 우리 차 에너지는 반보다 많을까요, 적을까요?",
                audioClue = "에너지 막대가 반을 넘으면 신나게 달릴 수 있어. 반보다 적으면 충전소에 가야 해!",
                hints = listOf(
                    "화면의 에너지 막대가 가운데 선을 넘었는지 보세요.",
                    "가운데 선은 50%예요.",
                    "에너지 숫자가 50 이상이면 첫 번째, 아니면 두 번째예요.",
                ),
                answer = Answer.Choice(
                    options = listOf("반보다 많아요 — 신나게 달릴 수 있어요", "반보다 적어요 — 충전이 필요해요"),
                    rule = ChoiceRule.BATTERY_HALF_OR_MORE,
                ),
                successText = "정답! 전기차는 배터리에 에너지를 담아 달려요. 에너지가 줄어들면 충전기로 채워 줘요.",
                needs = VehicleData.BATTERY_PERCENT,
                // 배터리 값을 못 읽으면 가짜 숫자 대신 상식 퀴즈를 낸다.
                fallback = Puzzle(
                    id = "energy-quiz",
                    title = "미션 2 · 에너지가 부족한 친구",
                    screenClue = "전기차 친구가 배가 고프대요. 전기차는 어디에서 에너지를 얻을까요?",
                    audioClue = "전기차는 기름을 먹지 않아. 콘센트처럼 꽂아서 먹지!",
                    hints = listOf(
                        "소리 탐정이 들은 말을 떠올려 보세요.",
                        "전기를 꽂아서 넣는 곳이에요.",
                        "정답은 충전기예요.",
                    ),
                    answer = Answer.Choice(listOf("주유소", "충전기", "세차장"), correctIndex = 1),
                    successText = "정답! 전기차는 충전기로 배터리에 에너지를 채워요.",
                ),
            ),
            Puzzle(
                id = "distance",
                title = "미션 3 · 학교까지 남은 거리",
                screenClue = "자동차 친구가 마을 학교에 가야 해요. 집에서 학교까지는 8km예요. 남은 거리는 몇 km일까요?",
                audioClue = "나 벌써 5km나 왔어! 조금만 더 가면 돼!",
                hints = listOf(
                    "소리 탐정에게 친구가 얼마나 왔는지 물어보세요.",
                    "전체 거리에서 온 거리를 빼요.",
                    "8 - 5 = 3이에요.",
                ),
                answer = Answer.Code("3"),
                successText = "맞아요! 남은 거리를 알면 에너지가 충분한지도 미리 알 수 있어요.",
            ),
            Puzzle(
                id = "tire",
                title = "미션 4 · 타이어가 아픈 친구",
                screenClue = "타이어에 바람이 빠지면 계기판에 경고등이 켜져요. 타이어 경고등은 어떤 모양일까요?",
                audioClue = "타이어는 말굽처럼 생겼어. 문제가 생기면 가운데에 느낌표가 붙지!",
                hints = listOf(
                    "소리 탐정이 들은 모양을 설명해 달라고 하세요.",
                    "말굽 모양을 찾아보세요.",
                    "첫 번째 보기가 정답이에요.",
                ),
                answer = Answer.Choice(
                    listOf("느낌표가 들어간 말굽 모양", "사람이 벨트를 맨 모양", "네모난 배터리 모양"),
                    correctIndex = 0,
                ),
                successText = "정답! 이 경고등이 켜지면 어른에게 알리고 타이어 바람을 확인해야 해요.",
            ),
            Puzzle(
                id = "ready",
                title = "미션 5 · 출동 준비",
                screenClue = "마지막 문제는 어둠 속에 숨어 있어요. 차의 눈을 밝히면 나타나요!",
                audioClue = "출동하려면 먼저 라이트를 켜! 그리고 오늘 배운 깜빡이 약속을 떠올려 봐.",
                hints = listOf(
                    "차의 '눈'은 전조등이에요. 먼저 켜 보세요.",
                    "미션 1에서 깜빡이는 무엇을 알려 주는 약속이었나요?",
                    "첫 번째 보기가 정답이에요.",
                ),
                answer = Answer.Choice(
                    options = listOf("방향을 바꿀 때", "배가 고플 때", "노래를 부를 때"),
                    correctIndex = 0,
                    revealWhen = RevealCondition.LOW_BEAM_ON,
                ),
                successText = "출동 준비 완료!",
            ),
        ),
        ending = "자동차 친구들이 다시 씽씽 달려요! 마을이 활기를 되찾았어요. 꼬마 자동차 탐정단 인증서를 드립니다.",
    )
}
