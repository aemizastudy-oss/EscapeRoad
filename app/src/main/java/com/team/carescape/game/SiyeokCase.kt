package com.team.carescape.game

/**
 * flow3 「시역(屍疫)」 정식 퍼즐판 (팀 기획 2026-11-14).
 * 장소마다 오디오 증언 → 현장 관찰 → 차량 조작 → 앱 안 추리 단계를 차례로 푼다.
 * 인물·일지·「고을 소식」은 모두 허구이며, 차량 조작은 신호 연동 전까지 화면 버튼으로 받는다.
 * 사실 근거: 1795년(을묘년) 윤2월 13일 봉수당 진찬, 화홍문 홍예 7칸·남수문 9칸, 만석거 1795년(정조 19년),
 * 축만제 1799년(정조 23년), 원천저수지=아랫방죽·신대저수지=웃방죽 별칭, 조선 봉수 5거 신호.
 */
object SiyeokCase {
    private fun opts(vararg pairs: Pair<String, String>) = pairs.map { ChoiceOption(it.first, it.second) }
    private fun notes(vararg pairs: Pair<String, String>): (String) -> WrongNote? {
        val map = pairs.toMap()
        return { key -> map[key]?.let { WrongNote(it) } }
    }

    private val W = CarControl.WIPER
    private val H = CarControl.HAZARD
    private val L = CarControl.TURN_LEFT
    private val R = CarControl.TURN_RIGHT

    // ───────── CH.1 화성행궁 ─────────
    private val ch1Steps = listOf(
        StoryPuzzleDefinition(
            id = "c1_testimony", kind = StepKind.TESTIMONY, replayNarration = true,
            title = "이동 중 들은 말",
            prompt = "오는 길에 의원의 목소리를 들었습니다. 의원은 전하의 글을 어디에 숨겼다고 했나요?",
            hints = listOf("‘다시 듣기’로 이동 중 이야기를 다시 들을 수 있어요.", "곧 열릴 잔치와 관련 있어요.", "잔치가 열릴 전각입니다."),
            solution = "의원: “전하의 글은 잔치가 열릴 전각에 숨겼소.”",
            lock = StoryLockDefinition.Choice(opts("hall" to "잔치가 열릴 전각", "gate" to "정문 신풍루", "kitchen" to "수라간", "clinic" to "내의원"), "hall"),
            wrongDefault = "의원의 말을 다시 떠올려 보세요. 사흘 뒤 무엇이 열리나요?",
        ),
        StoryPuzzleDefinition(
            id = "c1_plaque", kind = StepKind.OBSERVE,
            title = "잔치의 집",
            prompt = "밀서: “그것들은 흐르는 길을 건너지 못한다. 왕이 어머니의 장수를 빈 집을 찾아, 그 이름 가운데 글자를 읽으라.”\n\n회갑연이 열린 전각 현판의 가운데 글자는?",
            hints = listOf("혜경궁의 회갑연이 열린 전각을 찾아보세요.", "봉수당(奉壽堂)입니다. 세 글자 가운데를 보세요.", "壽 — 목숨 수입니다."),
            solution = "봉수당(奉壽堂)의 가운데 글자는 壽입니다.",
            lock = StoryLockDefinition.Choice(opts("feng" to "奉", "shou" to "壽", "tang" to "堂", "fu" to "福", "gong" to "宮", "an" to "安"), "shou"),
            feedback = notes("feng" to "그건 ‘받든다’는 첫 글자다. 가운데를 보라.", "tang" to "그건 ‘집’을 뜻하는 마지막 글자다."),
            wrongDefault = "현판의 세 글자를 다시 확인해 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c1_water", kind = StepKind.DEDUCE,
            title = "소리는 같으나",
            prompt = "壽를 짚자 밀서의 글씨가 번집니다. “소리는 맞으나 뜻이 다르다. 밀서가 말한 ‘흐르는 길’의 ‘수’를 고르라.”",
            hints = listOf("보기는 모두 ‘수’로 읽혀요.", "밀서의 첫 문장, 그것들이 건너지 못하는 것을 떠올려 보세요.", "흐르는 것은 물, 水입니다."),
            solution = "흐르는 길의 ‘수’는 물 水입니다.",
            lock = StoryLockDefinition.Choice(opts("shou" to "壽 (목숨)", "shui" to "水 (물)", "shou2" to "守 (지키다)", "shou3" to "首 (머리)"), "shui"),
            feedback = notes("shou2" to "지킬 수 없고, 머리도 아니다. 무엇이 흐르는가?", "shou3" to "지킬 수 없고, 머리도 아니다. 무엇이 흐르는가?", "shou" to "소리는 맞으나 뜻이 다르다."),
            success = "밀서 끝에 한 글자가 떠오릅니다. 水 — 물을 따라가라.",
        ),
        StoryPuzzleDefinition(
            id = "c1_bolt", kind = StepKind.SCENE,
            title = "행궁의 빗장",
            prompt = "“놈들이 들어오기 전에 문을 걸어라!”\n주변 차량과 보행자를 확인한 뒤 운전석 문을 열었다 닫고 버튼을 누르세요.",
            hints = emptyList(),
            solution = "문을 닫으면 빗장이 걸립니다.",
            lock = StoryLockDefinition.CarSequence(listOf(CarControl.DOOR), listOf(CarControl.DOOR), maxLength = 1),
            success = "쿵 — 빗장이 걸렸습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c1_diary_lock", kind = StepKind.DEDUCE,
            title = "연이의 수첩",
            prompt = "사라진 궁녀 연이의 수첩이 잠겨 있습니다. 표지: “내가 모시는 분의 잔치 날을 잊지 마라.”\n사건 기록부의 의원 일지 1장을 보고 날짜 네 자리(월·일)를 맞추세요.",
            hints = listOf("사건 기록부 → 문서 → 의원 일지 1장을 열어 보세요.", "혜경궁 마마의 회갑연 날짜예요.", "윤2월 13일 → 0213"),
            solution = "의원 일지 1장: 을묘년 윤2월 13일, 혜경궁 마마의 회갑연. 비밀번호는 0213입니다.",
            lock = StoryLockDefinition.Numeric("0213"),
            feedback = notes("0210" to "그건 의원이 일지를 쓴 날이다. 잔치는 사흘 뒤다."),
            wrongDefault = "수첩이 열리지 않습니다. 잔치 날을 다시 확인하세요.",
            success = "수첩이 열렸습니다. 사건 기록부에 ‘연이의 수첩’이 추가되었습니다.",
        ),
    )

    // ───────── CH.2 화홍문 ─────────
    private val ch2Steps = listOf(
        StoryPuzzleDefinition(
            id = "c2_testimony", kind = StepKind.TESTIMONY, replayNarration = true,
            title = "이동 중 들은 말",
            prompt = "장용영 병사가 말했습니다. 놈들이 다리 앞에서 멈춘 시각은?",
            hints = listOf("‘다시 듣기’로 병사의 말을 다시 들어 보세요.", "밤 9시에서 11시 사이를 부르는 이름이에요.", "해시입니다."),
            solution = "병사: “해시에 놈들이 다리 앞에서 멈췄소. 물소리가 컸지.”",
            lock = StoryLockDefinition.Choice(opts("you" to "유시", "xu" to "술시", "hai" to "해시", "zi" to "자시"), "hai"),
            wrongDefault = "병사의 말을 다시 들어 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c2_arches", kind = StepKind.OBSERVE,
            title = "수문의 수",
            prompt = "물이 성을 지나는 무지개 구멍(홍예)은 몇 칸인가요?",
            hints = listOf("화홍문 다리 아래 아치를 세어 보세요.", "남쪽 수문(남수문)과는 칸 수가 달라요.", "7칸입니다."),
            solution = "화홍문의 홍예는 일곱 칸입니다(남수문은 아홉 칸).",
            lock = StoryLockDefinition.Numeric("7"),
            feedback = notes("9" to "그건 남쪽 수문 이야기다. 지금 네 앞의 문을 세라."),
            wrongDefault = "다리 아래 무지개 구멍을 다시 세어 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c2_compare", kind = StepKind.DEDUCE,
            title = "두 기록 대조",
            prompt = "사건 기록부의 ‘장용영 순라 보고서’와 ‘수문 일지’를 나란히 읽으세요.\n놈들은 무엇 때문에 다리를 건너지 못했을까요?",
            hints = listOf("두 문서의 시각(해시·자시)을 맞춰 보세요.", "판문을 내려 물이 약해진 바로 그때 한 놈이 건넜어요.", "흐르는 물입니다."),
            solution = "물이 세게 흐르던 해시에는 멈췄고, 판문을 내려 물이 줄어든 자시 말에 건넜습니다. 놈들은 흐르는 물을 건너지 못합니다.",
            lock = StoryLockDefinition.Choice(opts("torch" to "횃불", "water" to "흐르는 물", "soldier" to "병사의 수", "dark" to "밤의 어둠"), "water"),
            wrongDefault = "자시 말에 무엇이 달라졌는지 두 기록을 맞춰 보세요.",
            success = "놈들은 흐르는 물을 건너지 못한다 — 규칙을 확인했습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c2_index", kind = StepKind.DEDUCE,
            title = "서고 색인함",
            prompt = "의원 일지 2장에 ‘그 아이에게 처음 쓴 약’을 싸 둔 보자기가 나옵니다. 서고 색인함에서 같은 이름의 카드를 고르세요.",
            hints = listOf("사건 기록부 → 의원 일지 2장을 읽어 보세요.", "보자기의 색과 천 이름이 정확히 같아야 해요.", "청포 보자기 → 乙-04"),
            solution = "의원 일지 2장: 청포 보자기. 색인 乙-04에서 연이의 진료 기록이 나옵니다.",
            lock = StoryLockDefinition.Choice(opts("e04" to "청포 보자기 · 乙-04", "e14" to "청색 보자기 · 乙-14", "a02" to "홍포 보자기 · 甲-02", "e40" to "청포 주머니 · 乙-40"), "e04"),
            feedback = notes("e14" to "빈 서랍이다.", "a02" to "잔치 옷감 목록이 나왔다. 찾던 것이 아니다.", "e40" to "동전 몇 닢뿐이다."),
            success = "乙-04 서랍에서 연이의 진료 기록을 찾았습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c2_gates", kind = StepKind.CAR,
            title = "일곱 판문",
            prompt = "병사: “판문을 내려 물을 한 칸으로 모아야 약에 쓸 센 물을 뜰 수 있소. 하지만 일곱을 다 막으면 물이 멎고, 놈들이 건너오오.”\n수문 일지를 참고해 1번 칸부터 차례로 정하세요. 와이퍼 = 판문 내리기, 비상등 = 열어 두기.",
            hints = listOf("열어 둘 칸은 딱 하나예요.", "수문 일지: 물이 가장 깊은 곳은 한가운데 칸. 일곱 칸의 한가운데는?", "와이퍼 3번 → 비상등 → 와이퍼 3번"),
            solution = "가운데 4번 칸만 열어 둡니다: 와이퍼, 와이퍼, 와이퍼, 비상등, 와이퍼, 와이퍼, 와이퍼.",
            lock = StoryLockDefinition.CarSequence(listOf(W, W, W, H, W, W, W), listOf(W, H), CarVisual.Sluice(7), maxLength = 7),
            feedback = { key ->
                val parts = key.split(",")
                when {
                    parts.size == 7 && parts.all { it == "WIPER" } -> WrongNote("물이 멎었다… 판문 너머로 그림자가 넘어온다. (시귀 각성도 +2)", 2)
                    parts.size < 7 -> WrongNote("일곱 칸을 모두 정해야 합니다.")
                    parts.count { it == "HAZARD" } > 1 -> WrongNote("물이 흩어진다. 한 칸으로 모아라.")
                    else -> WrongNote("물이 얕아 길어 올릴 수 없다. 가장 깊은 곳은 어디였나?")
                }
            },
            success = "가운데 칸으로 물이 세차게 몰립니다. 백신 재료 ‘멈추지 않고 흐르는 물’을 떴습니다.",
        ),
    )

    // ───────── CH.3 만석거 ─────────
    private val waterMap = CarVisual.WaterMap(30, listOf(
        Fork(25, "", 32, ""),
        Fork(27, "", 20, ""),
        Fork(18, "고인 우물", 15, "곡식 논"),
    ))
    private val ch3Steps = listOf(
        StoryPuzzleDefinition(
            id = "c3_testimony", kind = StepKind.TESTIMONY, replayNarration = true,
            title = "이동 중 들은 말",
            prompt = "둔전 노인이 말했습니다. 임금님이 이 저수지에 담은 바람은?",
            hints = listOf("‘다시 듣기’로 노인의 말을 다시 들어 보세요.", "‘만 석’이 무엇을 세는 말인지 생각해 보세요.", "만 석의 수확입니다."),
            solution = "노인: “만 석이 나야 고을이 산다고 임금님이 둑을 쌓으셨지.”",
            lock = StoryLockDefinition.Choice(opts("harvest" to "만 석의 수확", "stone" to "만 개의 돌", "people" to "만 명의 백성"), "harvest"),
            wrongDefault = "노인의 말을 다시 들어 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c3_pavilion", kind = StepKind.OBSERVE,
            title = "둑 위의 정자",
            prompt = "만석거 둑 위에 있는 정자의 이름은?",
            hints = listOf("둑 위 정자와 안내판을 찾아보세요.", "화홍문 옆 정자(방화수류정)와는 달라요.", "영화정입니다."),
            solution = "만석거 둑 위의 정자는 영화정입니다.",
            lock = StoryLockDefinition.Choice(opts("yeonghwa" to "영화정", "banghwa" to "방화수류정", "yeonmu" to "연무대"), "yeonghwa"),
            feedback = notes("banghwa" to "그건 화홍문 곁의 정자다.", "yeonmu" to "그건 성 안의 활터다."),
        ),
        StoryPuzzleDefinition(
            id = "c3_channel", kind = StepKind.CAR,
            title = "물길 돌리기",
            prompt = "농서 조각: “물은 높은 데서 낮은 데로만 간다. 곡식은 고인 물이 닿지 않은 것이어야 한다.”\n물은 화면 위에서 아래로 흐릅니다. 갈림길마다 깜빡이로 방향을 고르세요(왼쪽·오른쪽은 화면 기준).",
            hints = listOf("갈래의 숫자가 지금 높이보다 작아야 물이 갑니다.", "마지막 갈림길은 두 쪽 다 내리막이에요. 농서의 두 번째 문장을 보세요.", "왼 → 오른 → 오른"),
            solution = "30 → 25(왼) → 20(오른) → 15 곡식 논(오른). 18 쪽은 고인 우물이라 곡식을 버립니다.",
            lock = StoryLockDefinition.CarSequence(listOf(L, R, R), listOf(L, R), waterMap, maxLength = 3),
            feedback = { key ->
                val p = key.split(",")
                when {
                    p.size < 3 -> WrongNote("갈림길 세 곳을 모두 정해야 합니다.")
                    p[0] == "TURN_RIGHT" || p[1] == "TURN_LEFT" -> WrongNote("물이 거꾸로 오르지 못한다.")
                    else -> WrongNote("곡식이 고인 물에 젖었다. 약으로 쓸 수 없다.")
                }
            },
            success = "물길이 마을을 감싸고 곡식 논에 닿았습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c3_year", kind = StepKind.DEDUCE,
            title = "왕의 저수지",
            prompt = "“만석거는 정조 19년, 1795년에 쌓았다. 축만제는 정조 23년에 쌓았다.” 축만제를 쌓은 해는?",
            hints = listOf("같은 왕의 몇 년째인지가 두 번 나와요.", "23 − 19 = 4", "1795 + 4 = 1799"),
            solution = "1795 + (23 − 19) = 1799. 축만제는 1799년에 쌓았습니다.",
            lock = StoryLockDefinition.Numeric("1799"),
            feedback = notes("1798" to "한 해가 모자라다. 해의 차이를 세어 보라.", "1800" to "한 해가 넘친다."),
            wrongDefault = "정조 즉위 몇 년째인지의 차이로 계산해 보세요.",
            success = "마을 사람들이 곡식 한 자루를 내어 줍니다. 백신 재료 ‘만 석의 들판에서 거둔 곡식’을 얻었습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c3_hidden", kind = StepKind.DEDUCE,
            title = "붉은 먹점",
            prompt = "의원 일지 3장 몇 글자에만 붉은 먹점이 찍혀 있습니다. 먹점 글자만 이어 읽으면?",
            hints = listOf("사건 기록부 → 의원 일지 3장에서 붉게 표시된 글자를 찾으세요.", "여섯 글자입니다. 첫 글자는 ‘경’.", "경계를 넘었다"),
            solution = "경·계·를·넘·었·다 — “경계를 넘었다”.",
            lock = StoryLockDefinition.Choice(opts("crossed" to "경계를 넘었다", "water" to "물을 건너라", "night" to "밤을 넘겼다", "guard" to "경계를 서라"), "crossed"),
            wrongDefault = "붉은 먹점 글자만 순서대로 다시 읽어 보세요.",
            success = "일지의 숨은 문장: 경계를 넘었다.",
        ),
    )

    // ───────── CH.4 수원광교박물관 ─────────
    private val ch4Steps = listOf(
        StoryPuzzleDefinition(
            id = "c4_testimony", kind = StepKind.TESTIMONY, replayNarration = true,
            title = "이동 중 들은 말",
            prompt = "학예사가 찢어진 소식지 이야기를 했습니다. 소식지 뒷면에 무엇이 있다고 했나요?",
            hints = listOf("‘다시 듣기’로 학예사의 말을 다시 들어 보세요.", "조각이 맞아야 이어진다고 했어요.", "숫자 획입니다."),
            solution = "학예사: “뒷면에 숫자 획이 있네요.”",
            lock = StoryLockDefinition.Choice(opts("digits" to "숫자 획", "seal" to "붉은 도장", "map" to "물길 지도", "name" to "사람 이름"), "digits"),
            wrongDefault = "학예사의 말을 다시 들어 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c4_uigwe", kind = StepKind.OBSERVE,
            title = "기록을 되살린 사람",
            prompt = "이 박물관에 이름을 남긴 수집가가 다시 세상에 내놓은, 화성을 쌓은 과정의 기록은?",
            hints = listOf("기증자의 이름이 붙은 전시를 찾아보세요.", "‘의궤’로 끝나요.", "화성성역의궤"),
            solution = "화성 축성 과정을 기록한 『화성성역의궤』입니다. (현장 전시 구성은 방문 전 확인 필요)",
            lock = StoryLockDefinition.Choice(opts("seongyeok" to "화성성역의궤", "wonhaeng" to "원행을묘정리의궤", "joseon" to "조선왕조실록", "jeongjo" to "홍재전서"), "seongyeok"),
            feedback = notes("wonhaeng" to "그건 1795년 행차의 기록이다. 성을 쌓은 기록을 찾으라."),
            wrongDefault = "성을 ‘쌓은’ 기록을 찾으세요.",
        ),
        StoryPuzzleDefinition(
            id = "c4_paper", kind = StepKind.DEDUCE,
            title = "찢어진 소식지",
            prompt = "게임 속 기록 「고을 소식」이 여섯 조각으로 찢겨 있습니다. 찢긴 모양이 맞물리도록 처음부터 차례로 고르세요. 종이 가장자리(┃)는 처음과 끝에만 있습니다.",
            hints = listOf("조각 오른쪽 끝 기호와 다음 조각 왼쪽 기호가 맞물려요. ▶ 다음은 ◀, ▲ 다음은 ▼, 나머지는 같은 기호끼리.", "첫 조각은 ‘광교산 아래’로 시작해요.", "광교산 아래 → 두 저수지가 있다 → 마을 사람들은 → 위를 웃방죽 → 아래를 → 아랫방죽이라 부른다"),
            solution = "“광교산 아래 두 저수지가 있다. 마을 사람들은 위를 웃방죽, 아래를 아랫방죽이라 부른다.” 뒷면의 숫자 획이 이어져 4172가 보입니다.",
            lock = StoryLockDefinition.Order(opts(
                "f4" to "● 위를 웃방죽, ▲",
                "f1" to "┃ 광교산 아래 ▶",
                "f6" to "■ 아랫방죽이라 부른다. ┃",
                "f3" to "◆ 마을 사람들은 ●",
                "f2" to "◀ 두 저수지가 있다. ◆",
                "f5" to "▼ 아래를 ■",
            ), listOf("f1", "f2", "f3", "f4", "f5", "f6")),
            wrongDefault = "찢긴 모양이 맞지 않습니다. 뒷면의 획도 끊겨 있어요.",
            success = "조각을 뒤집자 숫자 획이 이어집니다: 4 1 7 2. 사건 기록부에 소식지가 추가되었습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c4_index", kind = StepKind.DEDUCE,
            title = "감초 상자",
            prompt = "의원 일지 4장에 비방 마지막 장을 넣어 둔 상자가 나옵니다. 서고 색인함에서 고르세요.",
            hints = listOf("사건 기록부 → 의원 일지 4장을 읽어 보세요.", "약재 이름과 그릇 이름이 둘 다 같아야 해요.", "감초 상자 → 丙-17"),
            solution = "의원 일지 4장: 감초 상자. 색인 丙-17에 봉화함이 있습니다.",
            lock = StoryLockDefinition.Choice(opts("p17" to "감초 상자 · 丙-17", "p71" to "감찰 상자 · 丙-71", "p07" to "감초 주머니 · 丙-07"), "p17"),
            feedback = notes("p71" to "관리를 감찰한 문서다. 이름이 비슷할 뿐이다.", "p07" to "약재 한 줌뿐이다."),
            success = "丙-17 자리에서 봉화로만 열리는 함을 찾았습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c4_beacon", kind = StepKind.CAR,
            title = "봉화함",
            prompt = "함의 문구: “그날 고을의 형편을 봉화로 알려야 열린다.”\n봉수 신호: 1 평시 · 2 적이 나타남 · 3 적이 경계에 다가옴 · 4 적이 경계를 넘음 · 5 맞붙어 싸움\nP단에서 전조등을 켰다 끄는 횟수로 봉화를 올리세요(상향등 금지).",
            hints = listOf("만석거에서 찾은 의원 일지 3장을 다시 보세요.", "숨은 글자 ‘경계를 넘었다’ + 본문 ‘아직 칼을 뽑지 않았다’.", "봉화 4개 — 전조등 4번"),
            solution = "경계는 넘었지만 아직 싸움은 없었습니다. 봉화 4개입니다.",
            lock = StoryLockDefinition.CarSequence(List(4) { CarControl.HEADLIGHT }, listOf(CarControl.HEADLIGHT), CarVisual.Beacon(5), maxLength = 5),
            feedback = { key ->
                when (key.split(",").size) {
                    5 -> WrongNote("아직 칼을 맞대지 않았다. 봉화를 너무 많이 올렸다.")
                    3 -> WrongNote("놈들은 이미 경계 안에 있다.")
                    else -> WrongNote("함이 열리지 않는다. 그날의 형편을 다시 읽어라.")
                }
            },
            success = "함이 열렸습니다. 비방 마지막 장과 약재 ‘감초’를 얻었습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c4_vaccine", kind = StepKind.DEDUCE,
            title = "백신 조합",
            prompt = "재료 카드에서 넣을 것만 순서대로 고르세요. 넣지 말아야 할 재료가 하나 섞여 있습니다.\n① 밀서: “시작은 흐르는 것이다.”  ② 농서 뒷면: “곡식은 마지막이 아니다.”  ③ 비방 마지막 장: “약재는 곡식을 감싼 뒤에 넣는다. 성 밖의 물은 쓰지 마라.”",
            hints = listOf("세 문장이 각각 다른 순서 정보를 줘요.", "처음(흐르는 것)과 ‘마지막이 아닌 것’을 먼저 정하세요. 성 밖의 물은 빼요.", "흐르는 물 → 곡식 → 감초"),
            solution = "흐르는 물 → 곡식 → 감초. 남수문의 물(성 밖의 물)은 넣지 않습니다.",
            lock = StoryLockDefinition.Order(opts("water" to "멈추지 않고 흐르는 물", "south" to "남수문의 물", "grain" to "만 석의 곡식", "licorice" to "감초"), listOf("water", "grain", "licorice")),
            feedback = { key -> if ("south" in key.split(",")) WrongNote("약이 탁해졌다. 성 밖의 물은 쓰지 않는다.") else null },
            wrongDefault = "약이 엉긴다. 비방의 문장을 다시 보라.",
            success = "약이 맑게 가라앉습니다. 백신이 완성되었습니다.",
        ),
        StoryPuzzleDefinition(
            id = "c4_map", kind = StepKind.DEDUCE,
            title = "물길 그림",
            prompt = "노인의 구술: “위 방죽에서 물이 흘러 아래 방죽으로 모인다.” 비방: “약은 물이 모이는 곳에 풀어라.”\n게임 속 물길 그림의 두 호수 가운데 약을 풀 곳은?",
            hints = listOf("물은 위에서 아래로 흘러 모여요.", "소식지는 아래 것을 뭐라고 불렀나요?", "아래 호수(아랫방죽)"),
            solution = "물이 모이는 곳은 아래 호수, 아랫방죽입니다.",
            lock = StoryLockDefinition.Choice(opts("upper" to "위 호수 (웃방죽)", "lower" to "아래 호수 (아랫방죽)"), "lower"),
            feedback = notes("upper" to "물은 그곳에서 아래로 흘러 내려간다."),
        ),
        StoryPuzzleDefinition(
            id = "c4_place", kind = StepKind.DEDUCE,
            title = "백신을 전달할 곳",
            prompt = "모은 단서: 水 — 물을 따라가라 / 약은 물이 모이는 곳에 풀어라 / 광교산 아래 두 방죽이 있다 / 아래를 아랫방죽이라 부른다.\n지금 향할 곳은?",
            hints = listOf("두 방죽을 품은 공원을 찾으세요.", "광교저수지는 방죽이 하나뿐이에요.", "광교호수공원(원천호수)"),
            solution = "원천저수지(아랫방죽)와 신대저수지(웃방죽)를 품은 광교호수공원입니다.",
            lock = StoryLockDefinition.Choice(opts("manseok" to "만석거", "gwanggyo_res" to "광교저수지", "lake_park" to "광교호수공원", "banghwa" to "방화수류정"), "lake_park"),
            feedback = notes("manseok" to "그 물은 이미 지나왔다.", "gwanggyo_res" to "그곳엔 방죽이 하나뿐이다. 두 방죽을 찾아라.", "banghwa" to "물이 모이는 곳이 아니다."),
        ),
    )

    // ───────── CH.5 광교호수공원 ─────────
    private val ch5Steps = listOf(
        StoryPuzzleDefinition(
            id = "c5_testimony", kind = StepKind.TESTIMONY, replayNarration = true,
            title = "이동 중 들은 말",
            prompt = "오는 길에 들린 속삭임. 연이는 누구를 불렀나요?",
            hints = listOf("‘다시 듣기’로 속삭임을 다시 들어 보세요.", "연이의 수첩에도 같은 말이 나와요.", "오라버니"),
            solution = "연이: “오라버니… 물소리가 무서워요.”",
            lock = StoryLockDefinition.Choice(opts("brother" to "오라버니", "mother" to "어머니", "majesty" to "마마", "soldier" to "병사"), "brother"),
            wrongDefault = "속삭임을 다시 들어 보세요.",
        ),
        StoryPuzzleDefinition(
            id = "c5_window", kind = StepKind.SCENE,
            title = "물소리",
            prompt = "창문을 내려 물소리를 들어 보세요. 그 사이로 낮은 신음이 섞여 있습니다.",
            hints = emptyList(),
            solution = "창문을 내리면 물소리가 들립니다.",
            lock = StoryLockDefinition.CarSequence(listOf(CarControl.WINDOW), listOf(CarControl.WINDOW), maxLength = 1),
            success = "물소리 사이로, 무언가 물가를 서성입니다.",
        ),
        StoryPuzzleDefinition(
            id = "c5_lake", kind = StepKind.DEDUCE,
            title = "두 호수",
            prompt = "이 공원에는 호수가 두 개 있습니다. 약을 풀 곳은?",
            hints = listOf("소식지의 마지막 문장을 떠올려 보세요.", "물은 위 방죽에서 아래 방죽으로 모여요.", "원천호수(아랫방죽)"),
            solution = "아랫방죽은 원천호수(원천저수지)입니다.",
            lock = StoryLockDefinition.Choice(opts("wonchon" to "원천호수", "sindae" to "신대호수"), "wonchon"),
            feedback = notes("sindae" to "여긴 웃방죽이다. 물은 아래로 흐른다."),
        ),
        StoryPuzzleDefinition(
            id = "c5_bottle", kind = StepKind.DEDUCE,
            title = "약병 선반",
            prompt = "약병 선반에 번호가 붙은 병이 넷 있습니다. 소식지 뒷면에서 이어진 번호의 병을 고르세요.",
            hints = listOf("박물관에서 맞춘 소식지 뒷면을 떠올려 보세요.", "조각을 거꾸로 읽은 숫자가 미끼예요.", "4172"),
            solution = "소식지 뒷면의 번호는 4172입니다.",
            lock = StoryLockDefinition.Choice(opts("b4172" to "4172", "b2714" to "2714", "b1724" to "1724", "b4127" to "4127"), "b4172"),
            wrongDefault = "숫자가 뒤집혔다. 조각의 순서대로 다시 읽어라.",
            success = "약병의 마개가 열립니다.",
        ),
        StoryPuzzleDefinition(
            id = "c5_truth_who", kind = StepKind.VERDICT, verdict = true,
            title = "진상 추리 ①",
            prompt = "약이 퍼지기 전, 마지막 질문입니다. 사건 기록부의 문서를 모두 떠올려 보세요.\n첫 감염자는 누구인가요?",
            hints = listOf("연이의 수첩 — ‘오라버니가 준 약을 먹은 뒤로’.", "乙-04: ‘시험 삼아 새 처방 한 첩, 기록자 서’.", "연이"),
            solution = "첫 감염자는 의원이 시험 삼아 약을 먹인 궁녀 연이입니다.",
            lock = StoryLockDefinition.Choice(opts("yeoni" to "궁녀 연이", "hancheol" to "병사 한철", "kim" to "둔전 노인 김씨", "park" to "수문지기 박 서방"), "yeoni"),
        ),
        StoryPuzzleDefinition(
            id = "c5_truth_why", kind = StepKind.VERDICT, verdict = true,
            title = "진상 추리 ②",
            prompt = "의원 서도윤은 왜 비방을 찢어 숨겼을까요?",
            hints = listOf("의원 일지 4장 — ‘아무도 그 아이를 탓하게 둘 수 없다’.", "연이는 의원을 ‘오라버니’라 불렀어요.", "누이를 지키려고"),
            solution = "연이는 의원의 누이였습니다. 의원은 누이가 첫 감염자라는 사실을 감추려고 비방을 찢었습니다.",
            lock = StoryLockDefinition.Choice(opts("sister" to "누이를 지키려고", "king" to "왕을 해치려고", "wrong" to "비방이 틀려서", "thief" to "도둑맞을까 봐"), "sister"),
        ),
    )

    private val chapters: List<StoryChapter> = IntegratedSiyeok.episode.chapters.mapIndexed { index, base ->
        when (index) {
            0 -> base.copy(
                travelDrama = base.travelDrama + " 어둠 속에서 의원의 목소리가 들린다. \"회갑연 사흘 전이오. 전하의 글은 잔치가 열릴 전각에 숨겼소.\"",
                explore = "정문 신풍루를 지나, 회갑연이 열릴 전각을 둘러보세요. 전각의 현판을 꼭 확인하고 차로 돌아오세요.",
                revealText = "밀서 끝에 한 글자가 적혀 있습니다. 水 — 물을 따라가라. 내의원 비방의 첫 장도 얻었습니다. \"이 약에는 흐르는 물과 만 석의 들판에서 거둔 곡식이 든다. 섞는 순서는 기록에 남겼다. 완성한 약은 모든 물길이 모이는 곳에 풀어라.\" 잠겨 있던 연이의 수첩에는 이상한 증상이 적혀 있었습니다.",
                clue = "水 — 물을 따라가라",
                puzzle = null, steps = ch1Steps,
            )
            1 -> base.copy(
                travelDrama = base.travelDrama + " 장용영 병사가 숨을 고르며 말한다. \"해시에 놈들이 다리 앞에서 멈췄소. 물소리가 컸지.\"",
                explore = "수문 사이로 흐르는 수원천을 둘러보세요. 다리 아래 무지개 구멍(홍예)이 몇 칸인지 세어 두고 차로 돌아오세요.",
                revealTitle = "물은 멈추지 않았다",
                revealText = "판문 여섯을 내리고 가운데 한 칸만 열어 두자, 물이 그 칸으로 세차게 몰렸습니다. 놈들은 다리 앞에서 멈춰 섰습니다. 백신 재료 '멈추지 않고 흐르는 물'을 떴습니다. 병사가 말합니다. \"약은 물이 모이는 곳에 풀어야 하오.\"",
                clue = "약은 물이 모이는 곳에 풀어라",
                puzzle = null, steps = ch2Steps,
            )
            2 -> base.copy(
                travelDrama = base.travelDrama + " 노인이 말한다. \"만 석이 나야 고을이 산다고 임금님이 둑을 쌓으셨지. 위 방죽, 아래 방죽 얘기는 나중에 해 주리다.\"",
                explore = "둑 위 정자에 올라, 정자의 이름과 안내판을 확인하고 차로 돌아오세요.",
                revealText = "물길이 마을을 감싸 흐르자, 마을 사람들이 곡식 한 자루를 내어 줍니다. 백신 재료 '만 석의 들판에서 거둔 곡식'을 얻었습니다. 노인이 덧붙입니다. \"광교산 아래에 방죽이 둘 있지. 위에서 내려온 물이 아래로 모여.\" 의원 일지 3장의 붉은 먹점이 그날의 형편을 말해 줍니다.",
                clue = "광교산 아래 두 방죽이 있다",
                puzzle = null, steps = ch3Steps,
            )
            3 -> base.copy(
                travelDrama = base.travelDrama + " 2026년, 학예사의 전화가 걸려 온다. \"찢어진 소식지가 들어왔어요. 뒷면에 숫자 획이 있네요.\"",
                explore = "기증 전시실에서, 이 성을 쌓은 기록을 다시 세상에 내놓은 수집가의 이야기를 둘러보고 차로 돌아오세요.",
                revealText = "봉화함에서 비방 마지막 장을 찾아, 흐르는 물·곡식·감초 순서로 백신을 완성했습니다. 「고을 소식」은 광교산 아래 두 방죽 가운데 아래를 아랫방죽이라 부른다고 적고 있습니다. 뒷면의 번호는 4172. 백신을 전달할 곳은 두 방죽을 품은 광교호수공원입니다.",
                clue = "아래를 아랫방죽이라 부른다",
                puzzle = null, steps = ch4Steps,
            )
            4 -> base.copy(
                travelDrama = base.travelDrama + " 바람 소리 사이로 여린 목소리가 스친다. \"오라버니… 물소리가 무서워요.\"",
                arrival = "광교호수공원에 도착했습니다. 두 방죽은 지금 커다란 호수 둘이 되어 있습니다. 해가 지고, 물가에서 무언가 움직이기 시작합니다.",
                explore = "호숫가를 걸으며 두 호수의 위치를 살펴보고 차로 돌아오세요. 밤길은 조심히 걸어 주세요.",
                puzzle = null, steps = ch5Steps,
            )
            else -> error("Unexpected story chapter")
        }
    }

    val documents = listOf(
        CaseDocument("doc_physician_1", "내의원 의원 서도윤", "의원 일지 1장",
            "을묘년 윤2월 10일. 행궁에 궁녀 하나가 쓰러졌다가 밤사이 사라졌다. 이름은 연이, 내가 아는 아이다. 사흘 뒤 윤2월 13일이 혜경궁 마마의 회갑연이다. 잔치를 멈출 수는 없다. 전하의 밀서는 잔치가 열릴 전각에 숨겨 두었다.", 0),
        CaseDocument("doc_yeoni", "궁녀 연이", "연이의 수첩",
            "오라버니가 준 약을 먹은 뒤로 목이 자꾸 마르다. 밤마다 물소리가 무섭게 들린다. 마마의 잔치 날까지는 버텨야 한다. 오라버니는 아무에게도 말하지 말라 했다. 손끝이 차갑다.", 0, "c1_diary_lock"),
        CaseDocument("doc_patrol", "장용영 병사 한철", "장용영 순라 보고서",
            "윤2월 11일 밤 순라. 해시, 북수문 다리 앞에 검은 무리가 섰다. 물소리가 거셌고, 놈들은 한 발도 다리에 올리지 못했다. 자시 말, 물소리가 잦아들자 한 놈이 물을 건넜다. 원인은 알 수 없다.", 1),
        CaseDocument("doc_sluice", "수문지기 박 서방", "수문 일지",
            "해시에 판문을 모두 올렸다. 물이 거세게 흘렀다. 자시에 위에서 시켜 판문 셋을 내렸다. 물이 반으로 줄었다. 일곱 칸 가운데 물이 가장 깊게 파인 곳은 한가운데 칸이다.", 1),
        CaseDocument("doc_physician_2", "내의원 의원 서도윤", "의원 일지 2장",
            "연이를 찾지 못했다. 그 아이에게 처음 쓴 약은 청포 보자기에 싸서 서고에 두었다. 다시 보면 무엇이 잘못됐는지 알 수 있을지 모른다. 놈들은 흐르는 물을 두려워한다. 약은 물에 실어야 한다.", 1),
        CaseDocument("doc_e04", "서고 乙-04", "연이 진료 기록",
            "윤2월 초 진맥. 열 없음, 기력 약함. 시험 삼아 새 처방 한 첩. 기록자 서. 이 장은 남에게 보이지 말 것.", 1, "c2_index"),
        CaseDocument("doc_farm", "둔전 노인 김씨", "농서 조각",
            "물은 높은 데서 낮은 데로만 간다. 곡식은 고인 물이 닿지 않은 것이어야 한다.\n\n(뒷면) 약에 곡식을 쓸 때, 곡식은 마지막이 아니다.", 2),
        CaseDocument("doc_elder", "둔전 노인 김씨", "노인의 구술",
            "광교산 아래에 방죽이 둘 있지. 위에서 내려온 물이 아래로 모여. 이름은 박물관 양반들이 더 잘 알 거요.", 2),
        CaseDocument("doc_physician_3", "내의원 의원 서도윤", "의원 일지 3장",
            "아침에 둑 위 사람들의 얼굴이 【경】직되어 있었다. 노인은 【계】곡 물이 줄었다고 했다. 나는 곡식 자루【를】 지고 둑을 건넜다. 해가 【넘】어갈 무렵 북쪽 마을의 불이 꺼졌다. 아이들이 울【었】으나 소리가 나지 않았다. 【다】행히 둑 아래 물은 아직 흐르고, 장용영은 아직 칼을 뽑지 않았다.", 2),
        CaseDocument("doc_curator", "2026년 학예사", "학예사 메모",
            "기증 자료 사이에서 1795년 무렵의 의원 일지와 찢어진 「고을 소식」 한 장이 나왔습니다. 소식지는 찢긴 모양을 맞춰야 읽을 수 있어요. 뒷면에 먹으로 쓴 숫자 획이 걸쳐 있습니다. 순서가 맞아야 숫자가 이어집니다.", 3),
        CaseDocument("doc_physician_4", "내의원 의원 서도윤", "의원 일지 4장",
            "비방 마지막 장은 감초 상자에 넣고 봉화함으로 잠갔다. 그 아이에게 먼저 쓴 약이 화근이었다. 내가 만든 병을 내가 거두어야 한다. 아무도 그 아이를 탓하게 둘 수 없다.", 3),
        CaseDocument("doc_news", "「고을 소식」 (게임 속 기록)", "찢어진 소식지",
            "광교산 아래 두 저수지가 있다. 마을 사람들은 위를 웃방죽, 아래를 아랫방죽이라 부른다.\n\n(뒷면) 4 1 7 2", 3, "c4_paper"),
        CaseDocument("doc_recipe", "내의원 의원 서도윤", "비방 마지막 장",
            "약재는 곡식을 감싼 뒤에 넣는다. 성 밖의 물은 쓰지 마라. 약은 물이 모이는 곳에 풀어라.", 3, "c4_beacon"),
        CaseDocument("doc_physician_5", "내의원 의원 서도윤", "의원 일지 5장",
            "나는 그 아이를 버릴 수 없었다. 비방을 찢은 것은 약을 숨기려던 게 아니라, 그 아이가 처음이었다는 걸 숨기려던 것이다. 약이 물에 풀리면 그 아이도 돌아올 것이다. 이 기록을 찾은 이여, 그 아이의 이름을 잊지 말아 다오.", 4, "c5_bottle"),
    )

    val people = listOf(
        CasePerson("physician", "서도윤", "내의원 의원", "비방과 감염 경과를 기록했다. 일지를 다섯 장으로 나누어 숨겼다.", 0),
        CasePerson("yeoni", "연이", "행궁 궁녀", "회갑연 사흘 전 쓰러졌다가 사라졌다. 수첩에 ‘오라버니’가 나온다. 의원과의 관계는?", 0,
            truthNote = "서도윤의 누이. 의원이 시험 삼아 먹인 약으로 처음 병에 걸렸다."),
        CasePerson("hancheol", "한철", "장용영 병사", "놈들이 언제 어디서 멈췄는지 시각을 기록했다.", 1),
        CasePerson("park", "박 서방", "화홍문 수문지기", "판문을 올리고 내린 시각과 물 깊이를 적었다.", 1),
        CasePerson("kim", "김씨", "둔전 노인", "만석거 둑으로 피한 마을 사람. 두 방죽 이야기를 알고 있다.", 2),
        CasePerson("curator", "학예사", "2026년 박물관", "찢어진 소식지와 의원 일지를 복원하고 있다.", 3),
    )

    private const val INTRO = "약병의 마개가 열린다. 감초 향 사이로 오래 묵은 곡식 냄새가 번진다. 의원 서도윤의 목소리가 아주 가까이에서 들린다. \"물이 모이는 곳이오. 여기서 시작해, 고을로 퍼질 것이오.\""
    private const val NOTE_ALL = "이 이야기의 인물과 사건은 허구입니다. 1795년 정조의 화성 행차와 봉수당 회갑연, 만석거와 축만제는 실제 역사를 바탕으로 했습니다. 원천호수 전망대까지 걸어서 둘러보세요. 다음 주행 전에는 앱을 닫고 안전하게 출발하세요."

    val endings = mapOf(
        EndingKind.TRUE to StoryEnding(
            title = "연이의 이름", script = "The Name Remembered",
            body = "을묘년 윤2월 13일 새벽. 아랫방죽의 물이 한 번 크게 일렁인다. 물길을 따라 내려간 약은 둑을 넘고, 논을 적시고, 우물마다 스민다. 북쪽 마을에 꺼졌던 불이 하나씩 다시 켜진다.\n\n행궁 담장 아래, 한 궁녀가 무릎을 꿇은 채 숨을 고른다. 손끝에 다시 온기가 돈다. \"오라버니.\" 서도윤은 대답 대신 소매로 누이의 얼굴을 닦는다. 그는 끝내 아무에게도 그 아이가 처음이었다고 말하지 않는다. 대신 일지의 마지막 줄에 이렇게 적는다. \"병을 만든 것도 사람이고, 거둔 것도 사람이다. 연이의 이름을 여기 남긴다.\"\n\n그날 오후, 봉수당에서 잔치가 열린다. 노래와 춤 사이, 누구도 지난밤 일을 입에 올리지 않는다. 다만 잔칫상 끝에 물 한 그릇이 놓여 있었다고, 기록에 없는 이야기가 전해진다.",
            epilogue = "수원광교박물관 수장고. 학예사가 복원을 마친 일지를 덮는다. 마지막 장, 지워져 있던 이름 하나가 또렷하게 떠올라 있다. 연이. \"이 이름… 아까까지는 없었는데.\" 창밖으로 원천호수의 불빛이 물 위에 길게 번진다.",
            note = "모든 기록이 복원되었습니다.",
        ),
        EndingKind.NORMAL to StoryEnding(
            title = "한 장이 비어 있다", script = "One Page Missing",
            body = "약이 물에 풀린다. 물길을 따라 내려간 약은 둑을 넘어 고을로 번진다. 해가 뜰 무렵, 거리를 떠돌던 그림자들이 하나둘 쓰러져 잠든다. 고을은 살아남는다.\n\n그날 오후, 봉수당의 잔치는 예정대로 열린다. 서도윤은 잔치 마당 끝에 서서 오래도록 행궁 담장 쪽을 바라본다. 그가 기다리는 누군가는 끝내 모습을 보이지 않는다. 그는 일지의 마지막 장을 찢어 품에 넣는다. \"이 장만은, 아직 아무도 알아서는 안 되오.\"",
            epilogue = "학예사가 복원한 일지를 넘긴다. 한 장이 비어 있다. \"누구 이야기였을까요. 이 사람이 끝까지 숨긴 건.\" 원천호수의 물이 조용히 출렁인다.",
            note = "백신은 전해졌지만, 기록 한 장이 복원되지 않았습니다.",
        ),
        EndingKind.BAD to StoryEnding(
            title = "호숫가의 그림자", script = "Too Many Awakened",
            body = "약이 물에 풀린다. 그러나 너무 늦었다. 물결은 둑 앞에서 멈칫하더니 다시 잔잔해진다. 물길을 거슬러 내려오던 검은 무리가 물가에 늘어선다. 놈들은 물을 건너지 못한다. 그러나 떠나지도 않는다.\n\n서도윤의 목소리가 멀어진다. \"늦었소… 놈들이 너무 많이 깨어났소. 약이 닿지 못한 곳이 있소.\" 횃불이 하나씩 꺼진다. 봉수당의 잔칫상은 차려진 채 아무도 앉지 않는다.",
            epilogue = "해 질 녘 원천호수. 산책로의 가로등이 켜진다. 물가 저편에 누군가 서 있다. 사람의 모양을 했으나, 물 쪽으로는 한 발도 다가오지 않는다. 휴대전화 화면에 일지 한 줄이 떠오른다. \"아직 끝나지 않았다.\"",
            note = "시귀가 너무 많이 깨어났습니다. 힌트를 덜 쓰고 다시 도전해 보세요.",
        ),
    )

    val episode: StoryEpisode = IntegratedSiyeok.episode.copy(
        chapters = chapters,
        finalRevealTitle = "백신을 전달할 곳",
        finalRevealText = "광교산 아래 두 방죽 가운데, 물이 모이는 아래쪽이 아랫방죽입니다. 두 방죽은 지금 광교호수공원의 두 호수가 되었습니다.",
        ending = endings.getValue(EndingKind.TRUE).body,
        endingIntro = INTRO,
        disclaimer = NOTE_ALL,
        documents = documents,
        people = people,
        endings = endings,
    )

    /** 각성도 8 이상이면 배드, 아니면 진상 추리를 모두 맞혔을 때 진 엔딩. */
    const val BAD_THRESHOLD = 8
}
