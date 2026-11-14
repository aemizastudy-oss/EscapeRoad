package com.team.carescape.game

/**
 * flow5 「시역」: 다섯 장소를 모두 퀴즈 목록 대신 직접 뒤지는 탐색 장면으로 푸는 판.
 * 확인용 퀴즈는 장면과 문서로 옮겼다. 판문은 실제 SDK의 오른쪽(닫기)·왼쪽(열기) 깜빡이로 입력한다.
 * 좌표는 각 장면 배경(1280×720)에 맞췄다. 아래 약 150px는 살펴보기 상자가 덮으므로 비워 둔다.
 */
object SiyeokFlow5 {
    private fun step(chapter: Int, id: String) = SiyeokFlow4.episode.chapters[chapter].puzzleSteps.first { it.id == id }

    // ───────── 1. 화성행궁 ─────────
    private val ch1Steps = listOf(
        step(0, "c1_plaque").copy(
            title = "지워진 현판",
            prompt = "봉수당 현판의 가운데 글자가 비바람에 지워져 있습니다. 행궁을 둘러보며 직접 본 현판 글자로 채우세요.",
            hints = listOf("혜경궁의 회갑연이 열린 전각, 봉수당의 현판을 보고 오세요.", "봉수당(奉壽堂) — 세 글자의 가운데예요.", "壽 — 목숨 수입니다."),
            success = "가운데에 壽를 다시 새기자 현판 뒤에서 무언가 덜컹거립니다.",
        ),
        step(0, "c1_water").copy(
            title = "현판 뒤 봉투",
            prompt = "현판 뒤 틈에 봉인된 봉투가 끼워져 있습니다. 겉봉: “그것들은 흐르는 길을 건너지 못한다. 현판 가운데 글자와 소리가 같되, 흐르는 것을 고르면 열리리라.”",
            hints = listOf("보기는 모두 ‘수’로 읽혀요.", "겉봉의 첫 문장, 그것들이 건너지 못하는 것을 떠올려 보세요.", "흐르는 것은 물, 水입니다."),
            success = "봉인이 풀립니다. 소지품에 ‘밀서’와 ‘비방 첫 장’이 들어왔습니다.",
        ),
        step(0, "c1_diary_lock").copy(
            title = "담장 아래 수첩",
            prompt = "담장 그늘에 떨어진 수첩이 잠겨 있습니다. 표지: “내가 모시는 분의 잔치 날을 잊지 마라.”\n날짜 네 자리(월·일)를 맞추세요.",
            hints = listOf("장면 어딘가에 날짜가 적힌 종이가 있어요.", "등불 받침 아래를 살펴보고, 사건 기록부의 의원 일지 1장을 읽어 보세요.", "윤2월 13일 → 0213"),
        ),
    )

    val palace = ExploreScene(
        bg = SceneBg.PALACE_DUSK,
        intro = "해 질 녘 행궁 마당. 의원은 잠시 자리를 비웠습니다. 마당 곳곳을 눌러 살펴보세요.",
        hotspots = listOf(
            Hotspot("x_bowl", "수라간 앞 사발", 430f, 470f, 80f, 46f, prop = "bowl", record = true, keepProp = true,
                look = "검은 얼룩이 말라붙은 사발이 깨져 있다. 누군가 물을 찾아 기어간 자국이 담장 쪽으로 이어진다.",
                doneLook = "깨진 사발. 기어간 자국은 담장 그늘에서 끊긴다."),
            Hotspot("x_wall", "담장 그늘", 292f, 414f, 70f, 38f, requires = "x_bowl", prop = "notebook", lockStep = "c1_diary_lock",
                look = "자국이 끝나는 담장 그늘에 손때 묻은 수첩이 떨어져 있다. 끈으로 단단히 묶여 열리지 않는다.",
                doneLook = "수첩이 있던 자리. 젖은 손자국만 남았다."),
            Hotspot("x_lantern", "등불 받침", 572f, 322f, 60f, 30f, prop = "paper_scrap", record = true,
                look = "등불 받침 아래 접힌 종이가 끼워져 있다. 의원의 글씨다. (사건 기록부에 ‘의원 일지 1장’이 추가되었습니다)",
                doneLook = "등불이 흔들린다. 받침 아래는 이제 비어 있다."),
            Hotspot("x_plaque", "봉수당 현판", 800f, 216f, 130f, 50f, lockStep = "c1_plaque",
                look = "현판의 가운데 글자가 비바람에 지워져 있다. 奉 □ 堂.",
                doneLook = "奉壽堂. 다시 새긴 글자가 노을빛에 번들거린다."),
            Hotspot("x_behind", "현판 뒤 틈", 830f, 268f, 72f, 26f, requires = "c1_plaque", prop = "secret_letter", lockStep = "c1_water",
                look = "현판이 살짝 들리며 틈이 생겼다. 붉게 봉인된 봉투가 끼워져 있다.",
                doneLook = "틈은 비어 있다."),
            Hotspot("x_door", "봉수당 문", 716f, 298f, 200f, 84f, speaker = "physician",
                look = "아직 들어가선 안 되오. 잔치 준비로 사람이 많소. 현판이나 잘 보아 두시오."),
            Hotspot("x_lamp_r", "오른쪽 등불", 1114f, 240f, 44f, 72f,
                look = "등불 심지가 짧다. 오늘 밤을 넘기지 못하겠다."),
            Hotspot("x_hall_l", "왼쪽 전각", 0f, 290f, 240f, 150f,
                look = "덧문이 굳게 닫혀 있다. 안에서 누군가 물을 찾는 소리가 들린 것 같다."),
        ),
    )

    // ───────── 2. 화홍문 ─────────
    private val ch2Steps = listOf(
        step(1, "c2_arches").copy(
            title = "무지개 구멍",
            prompt = "판문을 다시 정하려면 먼저 이 수문의 칸 수부터 알아야 합니다. 물이 성을 지나는 무지개 구멍(홍예)은 몇 칸인가요?",
        ),
        step(1, "c2_timebox"),
        step(1, "c2_drawer").copy(
            title = "창고 궤짝",
            prompt = "수문지기 창고의 궤짝이 잠겨 있습니다. 자물쇠 판에 乙 글자 모양의 홈이 파여 있습니다. 소지품 가운데 맞는 것을 골라 쓰세요.",
            hints = listOf("다리 위에 떨어진 의원의 일지를 먼저 읽어 보세요.", "乙 글자가 새겨진 소지품이 있어요. 화성행궁에서 얻었어요.", "서고 열쇠패 乙"),
            success = "딸깍 — 궤짝이 열립니다. 청포 보자기 안에 연이의 진료 기록과 빈 약첩이 있습니다. 소지품에 ‘청포 보자기’가 들어왔습니다.",
        ),
        step(1, "c2_gates").copy(
            prompt = "병사: “판문을 내려 물을 한 칸으로 모아야 약에 쓸 센 물을 뜰 수 있소. 하지만 일곱을 다 막으면 물이 멎고, 놈들이 건너오오.”\n수문 일지를 참고해 1번 칸부터 차례로 정하세요. 오른쪽 깜빡이 = 수문 닫기, 왼쪽 깜빡이 = 수문 열기. 같은 방향을 다시 입력하려면 껐다 켜세요. 켜 둔 동안의 점멸은 추가 입력이 아닙니다.",
            hints = listOf("열어 둘 칸은 딱 하나예요.", "수문 일지: 가장 깊은 곳은 일곱 칸의 한가운데예요.", "오른쪽 깜빡이 3번 → 왼쪽 깜빡이 1번 → 오른쪽 깜빡이 3번"),
            solution = "가운데 4번 칸만 열어 둡니다. 오른쪽 깜빡이 3번, 왼쪽 깜빡이 1번, 오른쪽 깜빡이 3번 후 제출하세요. 각 입력 사이에는 깜빡이를 꺼 주세요.",
            lock = StoryLockDefinition.CarSequence(
                listOf(CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_LEFT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT, CarControl.TURN_RIGHT),
                listOf(CarControl.TURN_RIGHT, CarControl.TURN_LEFT), CarVisual.Sluice(7), maxLength = 7,
            ),
            feedback = { key ->
                val parts = key.split(",")
                when {
                    parts.size == 7 && parts.all { it == "TURN_RIGHT" } -> WrongNote("물이 멎었다… 판문 너머로 그림자가 넘어온다. (시귀 각성도 +2)", 2)
                    parts.size < 7 -> WrongNote("일곱 칸을 모두 정해야 합니다.")
                    parts.count { it == "TURN_LEFT" } > 1 -> WrongNote("물이 흩어진다. 한 칸으로 모아라.")
                    else -> WrongNote("물이 얕아 길어 올릴 수 없다. 가장 깊은 곳은 어디였나?")
                }
            },
        ),
    )

    val sluice = ExploreScene(
        bg = SceneBg.SLUICE_NIGHT,
        intro = "윤2월 11일 밤, 화홍문. 다리 위에 병사들이 서 있고, 판문은 반쯤 내려져 있습니다. 다리와 물가를 살펴보세요.",
        hotspots = listOf(
            Hotspot("x2_post", "성문 옆 병사 초소", 1100f, 250f, 180f, 190f, speaker = "hancheol", record = true,
                look = "어젯밤 일은 보고서에 다 적었소. 가져가 읽어 보시오. (사건 기록부에 ‘장용영 순라 보고서’가 추가되었습니다)",
                doneLook = "해시엔 멈췄는데, 자시 말엔 건넜소. 그 사이 뭐가 달라졌는지 모르겠소."),
            Hotspot("x2_box", "성벽 위 수문지기의 함", 232f, 288f, 64f, 42f, prop = "dial_box", lockStep = "c2_timebox", keepProp = true, speaker = "park",
                look = "내 일지는 그 함에 넣고 잠갔소. 바퀴 둘을 맞춰야 열리오.",
                doneLook = "함은 열려 있고, 수문 일지는 기록부에 옮겨 적었다."),
            Hotspot("x2_page", "성벽 위 종이", 860f, 306f, 56f, 28f, prop = "paper_scrap", record = true,
                look = "성벽 위에 누군가 급히 지나가다 떨어뜨린 종이. 의원의 글씨다. (사건 기록부에 ‘의원 일지 2장’이 추가되었습니다)",
                doneLook = "바람에 젖은 자국만 남았다."),
            Hotspot("x2_arch", "첫째 홍예", 160f, 380f, 110f, 90f, lockStep = "c2_arches",
                look = "다리 아래로 무지개 모양 구멍이 줄지어 있다. 물이 그 사이로 성 안에 든다.",
                doneLook = "일곱 칸. 가운데 넷째 칸이 가장 깊다."),
            Hotspot("x2_lever", "판문 도르래", 572f, 418f, 78f, 60f, requires = "c2_arches", lockStep = "c2_gates", prop = "gate_wheel", keepProp = true,
                look = "일곱 판문을 하나씩 올리고 내리는 도르래다. 칸 수를 알았으니 이제 정할 수 있다.",
                doneLook = "가운데 칸으로 물이 세차게 몰린다."),
            Hotspot("x2_chest", "수문지기 창고 궤짝", 990f, 402f, 70f, 48f, requires = "x2_page", prop = "chest", lockStep = "c2_drawer", keepProp = true,
                look = "다리 밑 창고에 낡은 궤짝이 있다. 자물쇠 판에 乙 글자 홈이 있다.",
                doneLook = "빈 궤짝. 감초 냄새가 희미하게 남았다."),
            Hotspot("x2_pavilion", "화홍문 누각", 330f, 140f, 460f, 160f,
                look = "수문 위 누각. 아름다우나, 물이 모이는 곳은 아니다."),
            Hotspot("x2_water", "수원천", 120f, 500f, 300f, 56f,
                look = "물살이 거세다. 건너편 그림자 하나가 물가에서 멈칫 물러선다."),
        ),
    )

    // ───────── 3. 만석거 ─────────
    private val waterHeightGrid = WaterHeightGrid(
        rows = listOf(
            listOf(1, 16, 17, 15, 13),
            listOf(2, 18, 20, 19, 11),
            listOf(12, 24, 25, 21, 9),
            listOf(14, 23, 22, 6, 7),
            listOf(10, 3, 4, 5, 8),
        ),
        start = 25,
    )
    private val ch3Steps = listOf(
        step(2, "c3_pavilion").copy(
            title = "이끼 낀 현판",
            prompt = "둑 위 정자의 현판이 이끼에 덮여 있습니다. 둘러보며 직접 본 현판의 한자 세 글자를 바퀴로 맞추세요.\n소리가 같은 글자가 섞여 있으니 모양을 보고 고르세요.",
            hints = listOf("둑 위 정자 앞 현판이나 안내판의 한자를 그대로 맞춰요.", "정자 이름은 영화정이에요. 첫 글자는 ‘맞을 영(迎)’.", "迎 · 華 · 亭"),
            solution = "만석거 둑 위의 정자는 영화정(迎華亭)입니다. (현장 현판 표기 확인 필요)",
            lock = StoryLockDefinition.SymbolDial(
                listOf(
                    listOf("映 영", "永 영", "迎 영", "英 영"),
                    listOf("和 화", "華 화", "花 화", "火 화"),
                    listOf("井 정", "庭 정", "停 정", "亭 정"),
                ),
                listOf(2, 1, 3),
            ),
            feedback = { null },
            wrongDefault = "현판의 글자와 모양이 다르다. 소리만 같은 글자가 섞여 있다.",
        ),
        step(2, "c3_channel").copy(
            title = "둑 끝 수문",
            prompt = "물은 높은 곳에서 낮은 곳으로 흐릅니다. 이동 방향을 차례로 입력하세요.\n1. 중앙 25에서 출발합니다.\n2. 상하좌우의 더 작은 칸으로만 갑니다. 대각선은 금지입니다.\n3. 갈 수 있는 칸 중 가장 작은 수를 고릅니다.\n4. 더 작은 이웃이 없으면 끝납니다.",
            hints = listOf(
                "전체 표의 최솟값으로 바로 가지 않아요. 현재 칸의 상하좌우 네 이웃만 비교하세요.",
                "25의 이웃은 20·24·21·22예요. 가장 작은 20으로 간 뒤, 20에서는 17로 갑니다.",
                "위 → 위 → 오른쪽 → 오른쪽 → 아래 → 아래 → 아래 → 왼쪽 → 아래 → 왼쪽 → 왼쪽",
            ),
            solution = "25 → 20 → 17 → 15 → 13 → 11 → 9 → 7 → 6 → 5 → 4 → 3. 이동 방향은 ↑ ↑ → → ↓ ↓ ↓ ← ↓ ← ←입니다. 마지막 3의 이웃 10·23·4는 모두 더 크므로 멈춥니다.",
            lock = StoryLockDefinition.Directions(waterHeightGrid.directions, heightGrid = waterHeightGrid),
            feedback = { key ->
                if (key.split(",").size != waterHeightGrid.directions.size) {
                    WrongNote("더 작은 이웃 칸이 없어질 때까지 물길을 따라가며, 이동 한 번마다 방향 하나를 기록하세요.")
                } else {
                    WrongNote("각 칸에서 상하좌우의 더 작은 숫자 중 가장 작은 것을 고르세요. 대각선과 표 밖으로는 갈 수 없습니다.")
                }
            },
            wrongDefault = "중앙 25에서 시작해, 상하좌우의 더 작은 이웃 중 가장 낮은 칸을 다시 찾아보세요.",
            success = "물길이 높이 3에 닿자 수문의 잠금이 풀립니다. 논으로 이어지는 물길이 열렸습니다.",
        ),
        step(2, "c3_year").copy(
            title = "둑머리 비석",
            prompt = "둑머리 비석이 반쯤 깨져 있습니다. “만석거 정조 19년(1795) 축조 … 서쪽 축만제 정조 23년 ○○○○ 축조.”\n축만제를 쌓은 해는?",
        ),
    )

    val reservoir = ExploreScene(
        bg = SceneBg.RESERVOIR_DAWN,
        intro = "새벽 만석거. 둔전 마을 사람들이 둑 위로 피해 있습니다. 둑과 물가를 살펴보세요.",
        hotspots = listOf(
            Hotspot("x3_elder", "둔전 노인", 340f, 232f, 72f, 84f, speaker = "kim", record = true,
                look = "광교산 아래에 방죽이 둘 있지. 위에서 내려온 물이 아래로 모여. 그리고 의원 나리가 곡식 자루를 지고 둑을 건너갔소. 누굴 찾는 눈치였소. 작년인 갑(甲)년에 화성을 쌓기 시작했고, 올해가 을(乙)년이니, 내년인 병(丙)년이면 화성이 완성되겠군. (사건 기록부에 ‘노인의 구술’이 추가되었습니다)",
                doneLook = "둑 끝 수문을 잘 돌려야 하오. 곡식이 고인 물에 젖으면 못 쓰오."),
            Hotspot("x3_farmbook", "둑 위 종이", 520f, 300f, 50f, 22f, prop = "paper_scrap", record = true,
                look = "바람에 날려 온 농서 조각이다. (사건 기록부에 ‘농서 조각’이 추가되었습니다)",
                doneLook = "둑 위에 이슬만 맺혀 있다."),
            Hotspot("x3_pavilion", "영화정 현판", 1000f, 234f, 150f, 36f, lockStep = "c3_pavilion",
                look = "둑 위 정자의 현판이 이끼에 덮여 읽히지 않는다.",
                doneLook = "영화정(迎華亭). 이끼를 걷어 냈다."),
            Hotspot("x3_sluice", "둑 끝 수문", 878f, 344f, 70f, 54f, requires = "x3_farmbook", prop = "gate_wheel", lockStep = "c3_channel", keepProp = true,
                look = "수문 옆에 5×5 높이표와 방향 자물쇠가 있다. 가운데 숫자는 25다. 농서 조각의 네 규칙으로 물길을 찾아야 한다.",
                doneLook = "높이 3까지 이어진 물길. 방향 자물쇠가 풀리고 논으로 물이 흐른다."),
            Hotspot("x3_stele", "둑머리 비석", 150f, 236f, 40f, 52f, prop = "stele", lockStep = "c3_year", keepProp = true,
                look = "둑머리에 깨진 비석이 서 있다. 저수지를 쌓은 해가 새겨져 있다.",
                doneLook = "萬石渠 — 1795. 그리고 축만제 1799."),
            Hotspot("x3_diary", "갈대 사이 종이", 40f, 420f, 56f, 30f, requires = "c3_channel", prop = "paper_scrap", record = true,
                look = "물길이 바뀌자 갈대 사이에 걸린 종이가 드러났다. 몇 글자에 붉은 먹점이 찍혀 있다. (사건 기록부에 ‘의원 일지 3장’이 추가되었습니다)",
                doneLook = "갈대가 바람에 눕는다."),
            Hotspot("x3_water", "만석거", 400f, 430f, 300f, 60f,
                look = "‘만 석’이 나기를 바라며 쌓은 물. 둑 아래로 물이 아직 흐른다."),
        ),
    )

    // ───────── 4. 수원광교박물관 ─────────
    private val ch4Steps = listOf(
        step(3, "c4_uigwe").copy(
            prompt = "기증 진열장 설명판이 비어 있습니다. 이 박물관에 이름을 남긴 수집가가 다시 세상에 내놓은, 화성을 쌓은 과정의 기록은?",
        ),
        step(3, "c4_paper"),
        step(3, "c4_index").copy(
            title = "수장고 궤짝",
            prompt = "의원은 비방 마지막 장을 어느 상자에 넣었을까요? 사건 기록부의 의원 일지와 노인의 구술을 읽고, 궤짝의 상자를 고르세요.",
            hints = listOf(
                "의원 일지 4장과, 만석거에서 얻은 노인의 구술을 함께 보세요.",
                "노인: 작년 갑(甲)년에 시작, 올해 을(乙)년, 내년 병(丙)년에 화성 완성. 번호는 일지 1장 첫머리의 날짜예요.",
                "丙 상자 · 10",
            ),
            lock = StoryLockDefinition.Choice(
                SiyeokCase.opts(
                    "e10" to "乙 상자 · 10", "p13" to "丙 상자 · 13", "p10" to "丙 상자 · 10",
                    "d10" to "丁 상자 · 10", "a10" to "甲 상자 · 10",
                ),
                "p10",
            ),
            feedback = SiyeokCase.notes(
                "e10" to "乙은 올해의 글자다. 화성은 내년에 완성된다.",
                "a10" to "甲은 작년, 화성을 쌓기 시작한 해의 글자다.",
                "d10" to "丁은 내후년이다. 화성은 내년에 완성된다.",
                "p13" to "잔칫날이다. 연이 이름을 처음 적은 날은 일지 1장 첫머리에 있다.",
            ),
            success = "丙 상자 10번이 열립니다. 상자 뚜껑 안쪽에 손톱으로 긁은 글씨가 있습니다. ‘윤2월 열흘, 연아.’ 그 아래 봉화함이 놓여 있습니다.",
        ),
        step(3, "c4_flint"),
        step(3, "c4_beacon"),
        step(3, "c4_vaccine"),
        step(3, "c4_place").copy(
            prompt = "벽에 걸린 고을 지도에 물길이 그려져 있습니다. 모은 단서: 水 — 물을 따라가라 / 약은 물이 모이는 곳에 풀어라 / 광교산 아래 두 방죽이 있다 / 아래를 아랫방죽이라 부른다.\n백신을 전달할 곳은?",
        ),
    )

    val museum = ExploreScene(
        bg = SceneBg.MUSEUM,
        intro = "2026년, 수원광교박물관 수장고. 학예사가 잠시 자리를 비웠습니다. 진열장과 서랍을 살펴보세요.",
        hotspots = listOf(
            Hotspot("x4_memo", "학예사 메모", 630f, 420f, 60f, 30f, prop = "paper_scrap", record = true, speaker = "curator",
                look = "조각을 맞추면 뒷면 숫자 획이 이어져요. 메모 남겨 둘게요. (사건 기록부에 ‘학예사 메모’가 추가되었습니다)",
                doneLook = "소식지는 가운데 진열장에 있어요."),
            Hotspot("x4_case1", "기증 진열장", 700f, 200f, 110f, 250f, lockStep = "c4_uigwe",
                look = "기증자의 이름이 붙은 진열장. 설명판이 비어 있다.",
                doneLook = "『화성성역의궤』 — 甲寅(1794)에 시작해 丙辰(1796)에 마친 성의 기록."),
            Hotspot("x4_torn", "찢어진 소식지", 880f, 300f, 200f, 100f, lockStep = "c4_paper",
                look = "「고을 소식」 한 장이 여섯 조각으로 찢겨 있다. 뒷면에 숫자 획이 걸쳐 있다.",
                doneLook = "맞춘 소식지. 뒷면 번호 4172."),
            Hotspot("x4_map", "벽의 고을 지도", 930f, 140f, 220f, 120f, requires = "c4_paper", lockStep = "c4_place",
                look = "소식지를 맞추자 벽의 고을 지도가 눈에 들어온다. 광교산 아래 호수 둘이 그려져 있다.",
                doneLook = "두 방죽을 품은 곳, 광교호수공원."),
            Hotspot("x4_diary", "선반 위 상자", 100f, 130f, 110f, 90f, record = true,
                look = "선반 위 상자에 기증 자료가 들어 있다. 그 사이에 의원 일지 한 장이 끼워져 있다. (사건 기록부에 ‘의원 일지 4장’이 추가되었습니다)",
                doneLook = "‘내가 만든 병을 내가 거두어야 한다.’"),
            Hotspot("x4_drawer", "수장고 궤짝", 100f, 420f, 135f, 80f, requires = "x4_diary", lockStep = "c4_index",
                look = "작은 상자가 줄지어 든 수장고 궤짝. 상자마다 甲(갑)·乙(을)·丙(병)·丁(정) 글자와 번호가 붙어 있다.",
                doneLook = "丙 상자 10번이 열려 있다. 뚜껑 안쪽에 ‘윤2월 열흘, 연아.’"),
            Hotspot("x4_beaconbox", "봉화함", 325f, 468f, 110f, 76f, requires = "c4_index", prop = "beacon_box", keepProp = true,
                lockStep = "c4_flint", thenLock = "c4_beacon",
                look = "丙 상자 10번 자리에 봉화함이 있다. 화구 다섯이 비어 있고 ‘불을 먼저 얻어라’라고 새겨져 있다.",
                midLook = "화구에 불씨가 붙었다. 그날 고을의 형편만큼 봉화를 올려야 함이 열린다.",
                doneLook = "봉화 넷이 타오르고 함이 열렸다."),
            Hotspot("x4_brew", "약탕기", 500f, 440f, 110f, 90f, requires = "c4_beacon", prop = "pot", lockStep = "c4_vaccine", keepProp = true,
                look = "비방 마지막 장대로 재료를 넣을 약탕기다.",
                doneLook = "맑게 가라앉은 약. 백신이 완성되었다."),
        ),
    )

    // ───────── 5. 광교호수공원 ─────────
    private val ch5Steps = listOf(
        step(4, "c5_window"),
        step(4, "c5_lake").copy(
            prompt = "둑머리 그림판에 방죽 이름 자리가 지워져 있습니다. 소식지와 노인의 말을 떠올려, 약을 풀 물을 고르세요.",
            lock = StoryLockDefinition.Choice(
                SiyeokCase.opts(
                    "sindae" to "웃방죽 · 훗날 신대호수", "gwanggyo" to "광교산 골짜기 못", "wonchon" to "아랫방죽 · 훗날 원천호수",
                    "manseok" to "만석거", "seoho" to "축만제(서호)",
                ),
                "wonchon",
            ),
            feedback = SiyeokCase.notes(
                "sindae" to "여긴 웃방죽이다. 물은 아래로 흐른다.", "gwanggyo" to "방죽이 하나뿐인 산 위의 물이다.",
                "manseok" to "그 물은 이미 지나왔다.", "seoho" to "축만제는 아직 쌓지도 않았다. 4년 뒤의 물이다.",
            ),
        ),
        step(4, "c5_bottle").copy(
            prompt = "약병 선반의 병마다 네 자리 번호 자물쇠가 달려 있습니다. 박물관에서 맞춘 소식지 뒷면의 번호로 여세요.",
            hints = listOf("소지품의 「고을 소식」을 펼쳐 보세요.", "조각을 바른 순서로 맞췄을 때 뒷면에 이어진 숫자예요.", "4172"),
            lock = StoryLockDefinition.Numeric("4172"),
            feedback = SiyeokCase.notes("2714" to "숫자가 뒤집혔다. 조각의 순서대로 다시 읽어라.", "1724" to "숫자가 뒤집혔다. 조각의 순서대로 다시 읽어라.", "4127" to "끝 두 자리가 바뀌었다."),
            wrongDefault = "자물쇠가 꿈쩍도 하지 않는다.",
        ),
        step(4, "c5_release"),
        step(4, "c5_truth_who"),
        step(4, "c5_truth_why"),
    )

    val lake = ExploreScene(
        bg = SceneBg.LAKE_NIGHT,
        intro = "1795년 윤2월 12일 해 질 녘, 광교산 아래 아랫방죽. 둑길에 등불이 켜지고, 물가에서 무언가 서성입니다. 둑길을 살펴보세요.",
        hotspots = listOf(
            Hotspot("x5_shadow", "둑길 끝의 그림자", 1075f, 110f, 180f, 230f, speaker = "yeoni", record = true, prop = "shadow_figure", keepProp = true,
                look = "…오라버니? 물소리가 무서워요.",
                doneLook = "그림자는 물 쪽으로 한 발도 다가오지 않는다."),
            Hotspot("x5_window", "물소리", 200f, 330f, 400f, 90f, lockStep = "c5_window",
                look = "물소리 사이로 낮은 신음이 섞여 있다. 창문을 내리면 더 잘 들릴 것 같다.",
                doneLook = "물소리가 차 안까지 들어온다."),
            Hotspot("x5_sign", "둑머리 그림판", 1098f, 410f, 62f, 60f, prop = "sign", lockStep = "c5_lake", keepProp = true,
                look = "방죽 두 개가 그려진 그림판. 이름 자리가 지워져 있다.",
                doneLook = "아랫방죽 — 훗날의 원천호수."),
            Hotspot("x5_shelf", "약병 선반", 1000f, 484f, 92f, 56f, requires = "c5_lake", prop = "bottles", lockStep = "c5_bottle", keepProp = true,
                look = "둑길 가에 번호 붙은 약병 넷이 놓여 있다.",
                doneLook = "4172번 병만 마개가 열려 있다."),
            Hotspot("x5_shore", "아랫방죽 물가", 420f, 462f, 220f, 60f, requires = "c5_bottle", lockStep = "c5_release",
                look = "물이 모이는 곳. 여기에 약을 풀어야 한다.",
                doneLook = "약이 물을 타고 퍼져 나간다."),
            Hotspot("x5_tower", "등불 아래 · 서도윤", 900f, 360f, 84f, 170f, requires = "c5_release", speaker = "physician",
                lockStep = "c5_truth_who", thenLock = "c5_truth_why",
                look = "마지막으로 묻겠소. 이 병은 누구에게서 시작되었소?",
                midLook = "…그렇소. 그렇다면, 내가 왜 비방을 찢어 숨겼는지도 알겠구려.",
                doneLook = "이 기록을 찾은 이여, 그 아이의 이름을 잊지 말아 다오."),
        ),
    )

    private val scenes = listOf(palace, sluice, reservoir, museum, lake)
    private val stepsByChapter = listOf(ch1Steps, ch2Steps, ch3Steps, ch4Steps, ch5Steps)

    /** 문서는 장면에서 찾아야 열린다. */
    private val docSources = mapOf(
        "doc_physician_1" to "x_lantern",
        "doc_patrol" to "x2_post",
        "doc_physician_2" to "x2_page",
        "doc_farm" to "x3_farmbook",
        "doc_elder" to "x3_elder",
        "doc_physician_3" to "x3_diary",
        "doc_curator" to "x4_memo",
        "doc_physician_4" to "x4_diary",
    )
    private val documents = SiyeokFlow4.episode.documents.map { d ->
        val moved = docSources[d.id]?.let { d.copy(afterStep = it) } ?: d
        when (d.id) {
            "doc_farm" -> moved.copy(body = "수문 높이표를 읽는 네 규칙\n\n1. 가운데 25에서 시작한다.\n2. 상하좌우로 맞닿은 한 칸으로만 간다. 대각선은 건너지 못한다.\n3. 현재 숫자보다 작은 이웃 칸 중 가장 작은 숫자로 간다.\n4. 더 작은 이웃 칸이 없으면 멈춘다.\n\n지나간 방향을 차례로 자물쇠에 기록하라.\n\n곡식은 고인 물이 닿지 않은 것이어야 한다.\n\n(뒷면) 약에 곡식을 쓸 때, 곡식은 마지막이 아니다.")
            "doc_physician_2" -> moved.copy(body = "연이를 찾지 못했다. 그 아이에게 처음 쓴 약은 청포 보자기에 싸서 수문지기 창고의 乙 궤짝에 두었다. 다시 보면 무엇이 잘못됐는지 알 수 있을지 모른다. 놈들은 흐르는 물을 두려워한다. 약은 물에 실어야 한다.")
            "doc_e04" -> moved.copy(owner = "수문지기 창고 乙 궤짝")
            else -> moved
        }
    }
    private val noteSources = mapOf("c3_testimony" to "x3_elder", "c5_testimony" to "x5_shadow")
    private val people = SiyeokFlow4.episode.people.map { p ->
        p.copy(notes = p.notes.map { n -> noteSources[n.afterStep]?.let { n.copy(afterStep = it) } ?: n })
    }

    val episode: StoryEpisode = SiyeokFlow4.episode.copy(
        chapters = SiyeokFlow4.episode.chapters.mapIndexed { i, c ->
            val base = c.copy(steps = stepsByChapter[i], scene = scenes[i])
            if (i == 0) base.copy(explore = "정문 신풍루를 지나 회갑연이 열릴 전각을 둘러보세요. 현판 글자를 꼭 기억해 두세요.") else base
        },
        documents = documents,
        people = people,
    )
}
