package com.team.carescape.game

/**
 * flow4 「시역」: flow3 퍼즐판에서 차량 조작을 줄이고(판문 와이퍼·비상등, 창문 연출만 남김),
 * 앱 안 자물쇠(십이지 시각 다이얼, 물길·봉화 누르기)와 소지품 쓰기를 늘린 판.
 * flow3 저장 기록은 [SiyeokCase.episode]로 그대로 읽는다. 장면 대사는 flow3와 같다.
 */
object SiyeokFlow4 {
    private val opts = SiyeokCase::opts
    private fun notes(vararg pairs: Pair<String, String>) = SiyeokCase.notes(*pairs)
    private fun from(list: List<StoryPuzzleDefinition>, id: String) = list.first { it.id == id }

    val caseItems = listOf(
        CaseItem("secret_letter", "밀서", "전하의 밀서. “그것들은 흐르는 길을 건너지 못한다.” 끝에 水 한 글자.", "密", "c1_water"),
        CaseItem("recipe_first", "비방 첫 장", "“이 약에는 흐르는 물과 만 석의 들판에서 거둔 곡식이 든다. 완성한 약은 모든 물길이 모이는 곳에 풀어라.”", "方", "c1_water"),
        CaseItem("notebook", "연이의 수첩", "0213으로 연 수첩. 오라버니가 준 약, 마른 목, 무서운 물소리.", "冊", "c1_diary_lock"),
        CaseItem("key_eul", "서고 열쇠패 乙", "연이의 수첩 사이에 끼워져 있던 나무 패. 한 면에 乙이 새겨져 있다.", "乙", "c1_diary_lock", usedBy = "c2_drawer"),
        CaseItem("bojagi", "청포 보자기", "乙-04 서랍에서 나온 푸른 보자기. 빈 약첩 하나와 진료 기록이 싸여 있었다.", "布", "c2_drawer"),
        CaseItem("strong_water", "흐르는 물 한 병", "화홍문 가운데 칸에서 뜬, 멈추지 않고 흐르는 물. 백신 재료.", "水", "c2_gates", usedBy = "c4_vaccine"),
        CaseItem("grain", "곡식 자루", "둔전 마을 사람들이 내어 준 만 석 들판의 곡식. 백신 재료.", "穀", "c3_year", usedBy = "c4_vaccine"),
        CaseItem("flint", "부싯돌", "김 노인이 쥐여 준 부싯돌. “밤길에 쓰시오.”", "火", "c3_year", usedBy = "c4_flint"),
        CaseItem("news", "「고을 소식」", "맞춰 붙인 소식지. 아래 방죽을 아랫방죽이라 부른다. 뒷면 번호 4172. (게임 속 기록)", "報", "c4_paper"),
        CaseItem("licorice", "감초", "봉화함 안 비방 마지막 장과 함께 든 약재.", "草", "c4_beacon", usedBy = "c4_vaccine"),
        CaseItem("vaccine", "완성한 백신", "흐르는 물 → 곡식 → 감초 순으로 섞은 약.", "藥", "c4_vaccine", usedBy = "c5_release"),
    )
    private val pool = caseItems.map { it.id }
    private fun use(answer: String) = StoryLockDefinition.UseItem(answer, pool)

    private val hours = listOf("子 자시", "丑 축시", "寅 인시", "卯 묘시", "辰 진시", "巳 사시", "午 오시", "未 미시", "申 신시", "酉 유시", "戌 술시", "亥 해시")

    private val ch1 = SiyeokCase.ch1Steps.filter { it.id != "c1_bolt" }.map { step ->
        when (step.id) {
            "c1_water" -> step.copy(success = "밀서 끝에 한 글자가 떠오릅니다. 水 — 물을 따라가라. 소지품에 ‘밀서’와 ‘비방 첫 장’이 들어왔습니다.")
            "c1_diary_lock" -> step.copy(success = "수첩이 열렸습니다. 책장 사이에서 乙 글자가 새겨진 나무 패가 떨어집니다. 소지품에 ‘연이의 수첩’과 ‘서고 열쇠패 乙’이 들어왔습니다.")
            else -> step
        }
    }

    private val ch2 = listOf(
        from(SiyeokCase.ch2Steps, "c2_testimony"),
        from(SiyeokCase.ch2Steps, "c2_arches"),
        StoryPuzzleDefinition(
            id = "c2_timebox", speaker = "park", kind = StepKind.DEDUCE,
            title = "수문지기의 함",
            prompt = "박 서방: “내 일지는 이 함에 넣고 잠갔소. 첫째 바퀴는 놈들이 다리 앞에서 멈춘 때, 둘째 바퀴는 한 놈이 물을 건넌 때요. 병사 나리 보고서에 다 적혀 있소.”\n두 바퀴를 돌려 시각을 맞추세요.",
            hints = listOf("사건 기록부 → 장용영 순라 보고서를 읽어 보세요.", "멈춘 때는 오는 길에 들은 바로 그 시각이에요. 건넌 때는 그다음 시각이에요.", "첫째 亥(해시), 둘째 子(자시)"),
            solution = "순라 보고서: 해시에 멈췄고, 자시 말에 한 놈이 건넜습니다. 亥 · 子.",
            lock = StoryLockDefinition.SymbolDial(listOf(hours, hours), listOf(11, 0)),
            feedback = notes("0,11" to "순서가 뒤바뀌었소. 멈춘 것이 먼저요.", "11,11" to "멈춘 때는 맞소. 건넌 건 그다음 시각이오."),
            wrongDefault = "함이 꿈쩍도 하지 않는다. 보고서의 시각을 다시 보라.",
            success = "함이 열렸습니다. 사건 기록부에 ‘수문 일지’가 추가되었습니다.",
        ),
        from(SiyeokCase.ch2Steps, "c2_compare"),
        from(SiyeokCase.ch2Steps, "c2_index").copy(success = "乙-04 서랍을 찾았습니다. 그런데 서랍이 잠겨 있습니다."),
        StoryPuzzleDefinition(
            id = "c2_drawer", kind = StepKind.DEDUCE,
            title = "잠긴 서랍",
            prompt = "乙-04 서랍 앞판에 乙 글자 모양의 홈이 파여 있습니다. 소지품 가운데 맞는 것을 골라 쓰세요.",
            hints = listOf("소지품을 살펴보세요. 연이의 수첩에서 떨어진 것이 있어요.", "乙 글자가 새겨진 것을 찾으세요.", "서고 열쇠패 乙"),
            solution = "연이의 수첩에서 나온 ‘서고 열쇠패 乙’로 서랍을 엽니다.",
            lock = use("key_eul"),
            feedback = notes("secret_letter" to "밀서로는 서랍이 열리지 않는다.", "notebook" to "수첩을 홈에 대 보았지만 맞지 않는다. 수첩 안에 무엇이 있었지?"),
            wrongDefault = "홈에 맞지 않는다.",
            success = "딸깍 — 서랍이 열립니다. 청포 보자기 안에 연이의 진료 기록과 빈 약첩이 있습니다. 소지품에 ‘청포 보자기’가 들어왔습니다.",
        ),
        from(SiyeokCase.ch2Steps, "c2_gates").copy(success = "가운데 칸으로 물이 세차게 몰립니다. 소지품에 ‘흐르는 물 한 병’이 들어왔습니다."),
    )

    private val ch3 = SiyeokCase.ch3Steps.map { step ->
        when (step.id) {
            "c3_channel" -> step.copy(
                kind = StepKind.DEDUCE,
                prompt = "농서 조각: “물은 높은 데서 낮은 데로만 간다. 곡식은 고인 물이 닿지 않은 것이어야 한다.”\n물은 화면 위에서 아래로 흐릅니다. 갈림길마다 물을 보낼 쪽을 누르세요(왼쪽·오른쪽은 화면 기준).",
                lock = StoryLockDefinition.Sequence(
                    opts(arrayOf("L" to "◀ 왼쪽 물길", "R" to "오른쪽 물길 ▶")), listOf("L", "R", "R"), SiyeokCase.waterMap, maxLength = 3,
                ),
                feedback = { key ->
                    val p = key.split(",")
                    when {
                        p.size < 3 -> WrongNote("갈림길 세 곳을 모두 정해야 합니다.")
                        p[0] == "R" || p[1] == "L" -> WrongNote("물이 거꾸로 오르지 못한다.")
                        else -> WrongNote("곡식이 고인 물에 젖었다. 약으로 쓸 수 없다.")
                    }
                },
            )
            "c3_year" -> step.copy(success = "마을 사람들이 곡식 한 자루를 내어 줍니다. 김 노인이 부싯돌도 쥐여 줍니다. “밤길에 쓰시오.” 소지품에 ‘곡식 자루’와 ‘부싯돌’이 들어왔습니다.")
            else -> step
        }
    }

    private val ch4 = SiyeokCase.ch4Steps.flatMap { step ->
        when (step.id) {
            "c4_paper" -> listOf(step.copy(success = "조각을 뒤집자 숫자 획이 이어집니다: 4 1 7 2. 소지품에 「고을 소식」이 들어왔습니다."))
            "c4_beacon" -> listOf(
                StoryPuzzleDefinition(
                    id = "c4_flint", speaker = "physician", kind = StepKind.DEDUCE,
                    title = "꺼진 화구",
                    prompt = "丙-17 자리의 봉화함. 화구 다섯 개가 모두 비어 있고, 함 옆에 ‘불을 먼저 얻어라’라고 새겨져 있습니다. 소지품에서 쓸 것을 고르세요.",
                    hints = listOf("불을 일으킬 수 있는 것이 소지품에 있어요.", "만석거에서 김 노인이 건넨 것이에요.", "부싯돌"),
                    solution = "김 노인이 준 부싯돌로 불씨를 얻습니다.",
                    lock = use("flint"),
                    feedback = notes("strong_water" to "물을 부으면 불은 영영 붙지 않는다.", "secret_letter" to "밀서를 태울 수는 없다."),
                    wrongDefault = "그것으로는 불이 붙지 않는다.",
                    success = "부싯돌을 치자 불씨가 일어납니다.",
                ),
                step.copy(
                    kind = StepKind.DEDUCE,
                    prompt = "함의 문구: “그날 고을의 형편을 봉화로 알려야 열린다.”\n봉수 신호: 1 평시 · 2 적이 나타남 · 3 적이 경계에 다가옴 · 4 적이 경계를 넘음 · 5 맞붙어 싸움\n화구를 눌러 봉화를 올리세요.",
                    hints = listOf("만석거에서 찾은 의원 일지 3장을 다시 보세요.", "숨은 글자 ‘경계를 넘었다’ + 본문 ‘아직 칼을 뽑지 않았다’.", "봉화 4개"),
                    lock = StoryLockDefinition.Sequence(opts(arrayOf("FIRE" to "화구에 불 올리기")), List(4) { "FIRE" }, CarVisual.Beacon(5), maxLength = 5),
                    success = "함이 열렸습니다. 비방 마지막 장과 함께, 소지품에 ‘감초’가 들어왔습니다.",
                ),
            )
            "c4_vaccine" -> listOf(step.copy(
                prompt = "소지품의 재료를 약탕기에 넣을 순서대로 고르세요. 넣지 말아야 할 것이 하나 섞여 있습니다.\n① 밀서: “시작은 흐르는 것이다.”  ② 농서 뒷면: “곡식은 마지막이 아니다.”  ③ 비방 마지막 장: “약재는 곡식을 감싼 뒤에 넣는다. 성 밖의 물은 쓰지 마라.”",
                lock = StoryLockDefinition.Order(
                    opts(arrayOf("water" to "흐르는 물 한 병", "south" to "남수문의 물", "grain" to "곡식 자루", "licorice" to "감초")),
                    listOf("water", "grain", "licorice"),
                ),
                success = "약이 맑게 가라앉습니다. 소지품에 ‘완성한 백신’이 들어왔습니다.",
            ))
            else -> listOf(step)
        }
    }

    private val ch5 = SiyeokCase.ch5Steps.flatMap { step ->
        if (step.id != "c5_bottle") listOf(step) else listOf(
            step.copy(success = "4172번 병의 마개가 열립니다. 병 속은 비어 있습니다."),
            StoryPuzzleDefinition(
                id = "c5_release", speaker = "physician", kind = StepKind.DEDUCE,
                title = "물에 풀 것",
                prompt = "서도윤: “빈 병이오. 여기에 담아 물에 풀 것은 하나뿐이오.” 소지품에서 고르세요.",
                hints = listOf("박물관에서 마지막으로 만든 것이에요.", "세 재료를 섞어 만든 것이에요.", "완성한 백신"),
                solution = "흐르는 물·곡식·감초로 만든 ‘완성한 백신’을 병에 담아 원천호수에 풉니다.",
                lock = use("vaccine"),
                feedback = notes("news" to "소식지는 길을 알려 주었을 뿐이오.", "notebook" to "그 수첩은… 내가 간직하겠소."),
                wrongDefault = "그것은 약이 아니오.",
                success = "약이 원천호수의 물에 풀립니다.",
            ),
        )
    }

    private val stepsByChapter = listOf(ch1, ch2, ch3, ch4, ch5)

    private val stepMoves = mapOf("doc_sluice" to "c2_timebox", "doc_e04" to "c2_drawer")
    private val documents = SiyeokCase.documents.map { d -> stepMoves[d.id]?.let { d.copy(afterStep = it) } ?: d }
    private val people = SiyeokCase.people.map { p ->
        p.copy(notes = p.notes.map { n -> if (n.afterStep == "c2_index") n.copy(afterStep = "c2_drawer") else n })
    }

    val episode: StoryEpisode = SiyeokCase.episode.copy(
        chapters = SiyeokCase.chapters.mapIndexed { i, c -> c.copy(steps = stepsByChapter[i]) },
        documents = documents,
        people = people,
        caseItems = caseItems,
    )
}
