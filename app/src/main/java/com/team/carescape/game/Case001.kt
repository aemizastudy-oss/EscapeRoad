package com.team.carescape.game

/**
 * MODE 1 · CASE 001 - 사라진 17분 (성인 추리형). 시연판은 퍼즐 6개 중 앞의 2개만 담는다.
 * 사건·기록은 모두 허구이며 실제 차량의 주행 기록을 읽지 않는다.
 */
object Case001 {
    val episode = Episode(
        id = "case-001",
        title = "CASE 001",
        subtitle = "사라진 17분",
        tone = ThemeTone.NOIR,
        intro = "10년 전 야간 주행 중 한 운전자가 흔적도 없이 사라졌다. 사건은 단순 실종으로 종결됐다. " +
            "최근 그 차량이 발견됐고, 남은 기록에는 17분의 공백이 있다. 당신은 사건 조사관이다.",
        puzzles = listOf(
            Puzzle(
                id = "case-file",
                title = "퍼즐 1 · 사건 파일",
                screenClue = "사건 파일 #001\n접수: 2016년 11월 14일\n실종자 마지막 통화: 자정 19분 전\n" +
                    "기록이 끊긴 시각을 네 자리(시분)로 입력하라.",
                audioClue = "기록 장치는 차가 깨어 있을 때만 열린다. 헤드라이트를 켜라.",
                hints = listOf(
                    "화면이 어둡다면 동료에게 들은 지시를 떠올려라.",
                    "자정은 24:00이다. 19분 전은?",
                    "23시 41분 → 2341",
                ),
                answer = Answer.Code("2341", revealWhen = RevealCondition.LOW_BEAM_ON),
                successText = "23:41. 마지막 통화 직후부터 차량 기록이 흔들리기 시작한다.",
            ),
            Puzzle(
                id = "last-route",
                title = "퍼즐 2 · 마지막 경로",
                screenClue = "그날 밤 내비게이션에 남은 경로 후보 세 개.\n" +
                    "A: 도심 → 터널 → 외곽 순환로\n" +
                    "B: 도심 → 국도 → 24시 주유소 → 저수지 길\n" +
                    "C: 도심 → 고속도로 톨게이트 → 휴게소",
                audioClue = "목격자 진술: 통행료 결제 기록은 없었다. 블랙박스 마지막 장면에는 주유소 간판이 비쳤다.",
                hints = listOf(
                    "동료가 들은 진술 두 가지를 다시 확인하라.",
                    "톨게이트를 지났다면 통행료 기록이 남는다.",
                    "주유소를 지나는 경로는 B뿐이다.",
                ),
                answer = Answer.Choice(listOf("경로 A", "경로 B", "경로 C"), correctIndex = 1),
                successText = "경로 B. 차는 주유소를 지나 저수지 길로 들어섰다. 그리고 17분 동안 기록이 사라졌다.",
            ),
        ),
        ending = "다음 기록은 아직 복구 중이다. (시연판은 여기까지 — 전체 퍼즐 6개는 정식판에 담긴다.)",
    )
}
