package com.team.carescape.game

/** 장면 배경. 화면 쪽에서 장소별 그림으로 그린다. */
enum class SceneBg { AGENCY, TIME_RIFT, PALACE_DUSK, PALACE_NIGHT, SLUICE_NIGHT, RESERVOIR_DAWN, MUSEUM, LAKE_NIGHT }

/**
 * 장면 속 한 줄. [speaker]는 인물 id([StoryEpisode.speakerFor])이고, null이면 지문(내레이션)이다.
 * [bg]가 있으면 이 줄부터 배경이 바뀐다.
 */
data class Line(val speaker: String?, val text: String, val bg: SceneBg? = null)

/**
 * 사건 기록부 소지품. [fromStep]을 풀면 얻고, [usedBy] 단계를 풀면 '사용함'이 된다.
 * [glyph]는 소지품 칸에 새기는 한 글자.
 */
data class CaseItem(
    val id: String,
    val name: String,
    val description: String,
    val glyph: String,
    val fromStep: String,
    val usedBy: String? = null,
    /** 펼쳐 읽을 수 있는 물건(수첩·서찰)의 본문. 없으면 그림과 설명만 보여 준다. */
    val pages: List<String> = emptyList(),
)

/** 대사 줄을 음성 안내용 한 문단으로 잇는다. 인물의 말은 따옴표로 감싼다. */
fun List<Line>.spoken(): String = joinToString(" ") { if (it.speaker == null) it.text else "\"${it.text}\"" }

/** 스토리형 테마의 한 장소. 원본 flow1은 puzzle 없이 보존한다. */
data class StoryChapter(
    val place: String,
    val parking: String,
    /** 코스 카드에 보이는 한 줄 컨셉. */
    val concept: String,
    /** 이 장소로 이동하는 동안 소리로 들려줄 1795년 이야기. 주행 중 화면에는 쓰지 않는다. */
    val travelDrama: String,
    /** ① 도착 화면 이야기. */
    val arrival: String,
    /** 도착 화면 버튼 문구. */
    val arrivalAction: String,
    /** 둘러보는 동안 보여 줄 짧은 안내. 마지막 장소면 null(둘러보기 없이 바로 엔딩). */
    val explore: String?,
    /** ③ 단서 화면 제목. */
    val revealTitle: String,
    val revealText: String,
    /** 이 장소에서 얻는 백신 재료. 없으면 null. */
    val item: String? = null,
    /** 단서 카드 문구. */
    val clue: String? = null,
    /** 다음 행선지 안내 문구. 다음이 추리로 밝혀지는 곳이면 null. */
    val next: String? = null,
    val puzzle: StoryPuzzleDefinition? = null,
    /** flow3: 이 장소에서 차례로 푸는 단계들. 비어 있으면 [puzzle] 하나가 유일한 단계다. */
    val steps: List<StoryPuzzleDefinition> = emptyList(),
    /** flow3: 이동 중 오디오 장면(주행 중에는 소리로만). 비어 있으면 [travelDrama]만 쓴다. */
    val travelScene: List<Line> = emptyList(),
    /** flow3: 도착 장면 대사. 비어 있으면 [arrival] 문단을 그대로 보여 준다. */
    val arrivalScene: List<Line> = emptyList(),
    /** flow3: 단서 해금 장면 대사. 비어 있으면 [revealText]. */
    val revealScene: List<Line> = emptyList(),
) {
    val puzzleSteps: List<StoryPuzzleDefinition> get() = steps.ifEmpty { listOfNotNull(puzzle) }
}

/** 사건 기록부의 문서. [chapterIndex] 장소에서 차로 돌아온 뒤, [afterStep]을 풀었으면 열린다. */
data class CaseDocument(
    val id: String,
    val owner: String,
    val title: String,
    /** 【 】로 감싼 글자는 붉은 먹점으로 강조한다. */
    val body: String,
    val chapterIndex: Int,
    val afterStep: String? = null,
)

/** 인물 칸에 덧붙는 기록. [afterStep]을 풀어야 보인다. */
data class PersonNote(val afterStep: String, val text: String)

/**
 * 사건 기록부의 인물. [chapterIndex] 장소에 도착하면 기록부에 오른다.
 * [relation]은 관계도에서 서도윤과 잇는 말이고, 진상 추리를 맞히면 [truthRelation]·[truthNote]로 바뀐다.
 * [listed]가 false면 대사에만 나오고 기록부에는 오르지 않는다(조사국).
 */
data class CasePerson(
    val id: String,
    val name: String,
    val role: String,
    val note: String,
    val chapterIndex: Int,
    val truthNote: String? = null,
    val relation: String? = null,
    val truthRelation: String? = null,
    val notes: List<PersonNote> = emptyList(),
    val listed: Boolean = true,
)

enum class EndingKind(val label: String) { TRUE("진 엔딩"), NORMAL("일반 엔딩"), BAD("배드 엔딩") }

data class StoryEnding(val title: String, val script: String, val body: String, val epilogue: String, val note: String)

data class StoryEpisode(
    val id: String,
    val title: String,
    val subtitle: String,
    val prologue: String,
    val chapters: List<StoryChapter>,
    /** 마지막 장소는 이 화면에서 단서를 모아 공개한다. */
    val finalRevealTitle: String,
    val finalRevealText: String,
    val ending: String,
    val disclaimer: String,
    val rules: List<String> = emptyList(),
    val documents: List<CaseDocument> = emptyList(),
    val people: List<CasePerson> = emptyList(),
    /** 비어 있으면 [ending] 하나만 쓴다(flow1·2). */
    val endings: Map<EndingKind, StoryEnding> = emptyMap(),
    val endingIntro: String? = null,
    /** flow4: 소지품. */
    val caseItems: List<CaseItem> = emptyList(),
    /** 출발 전 프롤로그 장면. 비어 있으면 [prologue] 문단과 규칙을 페이지로 보여 준다. */
    val prologueScene: List<Line> = emptyList(),
) {
    val finalChapterIndex: Int get() = chapters.lastIndex
    fun speakerFor(id: String?): CasePerson? = id?.let { key -> people.firstOrNull { it.id == key } }
}

/**
 * 시연 시나리오 「시역(屍疫), 물을 건너지 못하는 자들」 (팀 기획 2026-11-12).
 * 팀 결정에 따라 장소 퀴즈·퍼즐은 쓰지 않고 이야기만 옮겼다. 퍼즐 자리는 비워 둔다.
 * 1795년 역병·시간여행·밀서·비방·'흐르는 물을 건너지 못한다'는 규칙은 모두 허구다.
 */
object Siyeok {
    val episode = StoryEpisode(
        id = "siyeok",
        title = "시역(屍疫)",
        subtitle = "물을 건너지 못하는 자들",
        prologue = "요원, 브리핑을 시작한다. 1795년 윤2월, 화성에 정체불명의 역병이 돌았다. 죽은 자가 다시 일어나는 병이다. " +
            "당시 내의원이 치료제의 비방을 만들었지만, 기록은 흩어졌고 약은 끝내 쓰이지 못했다. 그 결과 역사가 무너지고 있다. " +
            "1795년으로 건너가 비방과 재료를 모으고, 백신을 완성해 전달하라. 첫 번째 실마리는 왕의 어머니를 위한 잔치가 열린 곳에 있다.",
        chapters = listOf(
            StoryChapter(
                place = "화성행궁",
                parking = "화성행궁 주차장",
                concept = "왕의 밀서와 내의원 비방의 첫 장을 찾는다",
                travelDrama = "회갑연을 사흘 앞둔 행궁. 수라간 앞에서 궁녀 하나가 검은 피를 토하며 쓰러졌다. " +
                    "의원이 맥을 짚었을 때, 그 몸은 이미 차가웠다. 그런데 그날 밤, 그 궁녀가 사라졌다.",
                arrival = "1795년 윤2월, 화성행궁에 도착했습니다. 사흘 뒤면 혜경궁의 회갑연이 열립니다. " +
                    "행궁은 잔치 준비로 분주하지만, 어젯밤 쓰러진 궁녀 이야기에 모두 목소리를 낮추고 있습니다. " +
                    "내의원 의원이 당신에게 속삭입니다. \"잔치가 열릴 전각에 전하의 글을 숨겨 두었소. 그곳을 천천히 둘러보시오.\"",
                arrivalAction = "둘러보고 올게요",
                explore = "정문 신풍루를 지나, 회갑연이 열릴 전각을 천천히 둘러보고 차로 돌아오세요.",
                revealTitle = "물(水)을 따라가라",
                revealText = "의원이 건넨 밀서의 끝에 한 글자가 적혀 있습니다. 水 — 물을 따라가라. " +
                    "밀서와 함께 내의원 비방의 첫 장을 얻었습니다. \"이 약에는 두 가지가 든다. 멈추지 않고 흐르는 물, " +
                    "그리고 만 석의 들판에서 거둔 곡식. 섞는 순서는 기록에 남겼다. 완성한 약은 모든 물길의 근원에 풀어라.\"",
                clue = "水 — 물을 따라가라",
                next = "물이 성 안으로 들어오는 무지개 문, 화홍문",
            ),
            StoryChapter(
                place = "화홍문",
                parking = "화홍문 공영주차장",
                concept = "수문을 지켜 첫 번째 재료를 얻는다",
                travelDrama = "북쪽 들판에서 그것들이 몰려왔다. 장용영 병사가 외쳤다. '놈들은 물을 건너지 못한다! 수원천만 지키면 된다!' " +
                    "그런데 누군가 소리쳤다. '수문의 쇠살문이 열려 있다!'",
                arrival = "화홍문에 도착했습니다. 북쪽 들판에서 그것들이 몰려오고 있습니다. 수문을 지키던 장용영 병사가 외칩니다. " +
                    "\"놈들은 흐르는 물을 건너지 못하오! 수문만 지키면 되오!\"",
                arrivalAction = "둘러보고 올게요",
                explore = "병사를 도와, 수문 사이로 흐르는 수원천을 둘러보고 차로 돌아오세요.",
                revealTitle = "물은 멈추지 않았다",
                revealText = "병사들이 쇠살문을 내렸지만, 가운데 수문 하나는 열어 두었습니다. 물이 멈추면 놈들이 걸어 들어오기 때문입니다. " +
                    "덕분에 물은 멈추지 않았고, 백신 재료 하나를 얻었습니다. 병사가 말합니다. \"이 물은 북쪽, 광교산에서 내려오오.\"",
                item = "멈추지 않고 흐르는 물",
                clue = "북(北)으로 거슬러 올라라. 이 물의 머리는 광교산이다.",
                next = "북쪽, 왕이 백성을 위해 만든 큰 물, 만석공원(만석거)",
            ),
            StoryChapter(
                place = "만석공원",
                parking = "만석공원 제5공영주차장",
                concept = "정조가 만든 저수지의 물길을 지켜 두 번째 재료를 얻는다",
                travelDrama = "장안문 밖 너른 들판에는 왕이 새로 일군 둔전과 그 땅을 부치는 마을이 있었다. " +
                    "그해 봄 쌓은 저수지의 둑이 마르기도 전에, 들판 저편에서 그것들이 걸어왔다. " +
                    "마을 사람들은 둑 위로 도망쳤다. 물만이 그들을 지켜줄 수 있었다.",
                arrival = "만석거에 도착했습니다. 그해 봄 쌓은 저수지의 둑 위로, 둔전 마을 사람들이 피해 올라와 있습니다. " +
                    "한 노인이 당신을 붙잡습니다. \"전하께서 우리를 위해 만드신 물이오. 이 물만 있으면 놈들이 들어오지 못하오.\"",
                arrivalAction = "둘러보고 올게요",
                explore = "둑 위 정자에 올라, 물과 들판이 만나던 풍경을 둘러보고 차로 돌아오세요.",
                revealTitle = "물길이 마을을 감쌌다",
                revealText = "마을 사람들과 함께 물길을 지켜 냈습니다. 물이 마을을 감싸 흐르기 시작하자, 마을 사람들이 곡식 한 자루를 내어 줍니다. " +
                    "의원의 일지 조각도 함께 찾았습니다. \"약은 광교산 물이 모이는 아래쪽 골짜기에 풀어야, 물이 닿는 모든 곳으로 퍼진다.\"",
                item = "만 석의 들판에서 거둔 곡식",
                clue = "이 물 또한 광교산에서 내려왔다. 약은 광교산 물이 모이는 아래쪽 골짜기에.",
                next = "흩어진 기록이 모인 곳, 수원광교박물관",
            ),
            StoryChapter(
                place = "수원광교박물관",
                parking = "박물관 부설주차장",
                concept = "흩어진 기록에서 비방의 마지막 장을 얻는다",
                travelDrama = "왕은 그해의 일을 기록에서 지우라 명했다. 그러나 의원은 차마 모두 태우지 못했다. " +
                    "그는 기록을 여러 조각으로 찢어, 서로 다른 해의 기록 사이에 숨겼다. '시간의 순서대로 놓으면, 기록이 스스로 말할 것이다.'",
                arrival = "2026년, 수원광교박물관에 도착했습니다. 조사국: \"요원, 비방의 마지막 장은 의원이 찢어 여러 기록 사이에 숨겼다. " +
                    "이 시대에 남은 기록에서 찾아야 한다.\"",
                arrivalAction = "둘러보고 올게요",
                explore = "2층 기증 전시실에서, 이 성의 옛 기록을 다시 세상에 내놓은 사람의 이야기를 둘러보고 차로 돌아오세요.",
                revealTitle = "백신이 완성되었다",
                revealText = "흩어진 기록 조각을 시간의 순서대로 놓자, 기록이 말합니다. \"아랫방죽에서.\" " +
                    "비방의 마지막 장에 따라 곡식을 먼저, 물을 나중에 섞어 백신을 완성했습니다. " +
                    "조사국 메모: \"의원이 말한 골짜기는 훗날 저수지가 되었다.\"",
                item = "완성한 백신",
                clue = "아랫방죽에서",
                next = null,
            ),
            StoryChapter(
                place = "광교호수공원",
                parking = "원천호수 쪽 공영주차장",
                concept = "완성한 백신을 전달할 곳",
                travelDrama = "요원, 백신은 완성됐다. 이제 광교산 아래로 향한다. 1795년의 들판 위로, 2026년의 도시가 겹쳐 보이기 시작한다. " +
                    "의원이 말한 그 골짜기는, 지금 커다란 호수가 되어 있다.",
                arrival = "광교호수공원, 원천호수에 도착했습니다. 의원이 말한 그 골짜기는 지금 커다란 호수가 되어 있습니다. " +
                    "해가 지고, 물가에서 무언가 움직이기 시작합니다.",
                arrivalAction = "약병을 열어 백신 전달하기",
                explore = null,
                revealTitle = "임무 완료",
                revealText = "약병이 열리고, 백신이 물을 타고 퍼져 나갑니다.",
            ),
        ),
        finalRevealTitle = "백신을 전달할 곳",
        finalRevealText = "광교산에서 내려온 물이 모이는 아래쪽 골짜기는 훗날 저수지가 되었고, 옛사람들은 그곳을 '아랫방죽'이라 불렀습니다. " +
            "광교호수공원의 두 저수지 가운데 원천저수지가 바로 그 아랫방죽입니다.",
        ending = "요원, 해냈다. 약이 물을 타고 퍼져 나간다. 물로 이어진 고을 곳곳에서, 쓰러졌던 사람들이 하나둘 숨을 고르기 시작한다. " +
            "이제 알겠나. 왜 처음부터 물을 따라가라고 했는지. 이 고을의 이름은 수원(水原). 물 수(水)에 근원 원(原), 물의 근원이라는 뜻이다. " +
            "이 땅을 지킨 것은 처음부터 물이었다. 그리고 그 물의 근원에 마지막 약을 전한 것은, 바로 당신이다.",
        disclaimer = "실제 역사와 장소를 배경으로 한 허구의 이야기입니다. 1795년의 역병, 시간여행, 정조의 밀서와 내의원 비방, " +
            "'흐르는 물을 건너지 못한다'는 규칙은 모두 지어낸 설정입니다.",
    )
}
