package com.team.carescape.ui

import androidx.compose.foundation.background
import com.team.carescape.travel.toSaved
import com.team.carescape.travel.slotKey
import com.team.carescape.travel.Timeline
import com.team.carescape.travel.TimedItem
import com.team.carescape.travel.GuideMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.team.carescape.game.StoryPhase
import com.team.carescape.travel.TravelParty
import com.team.carescape.travel.DayPlan
import com.team.carescape.travel.GuideStops
import com.team.carescape.travel.TourGuide
import com.team.carescape.travel.GuideStop
import com.team.carescape.travel.Interest
import com.team.carescape.travel.Itinerary
import com.team.carescape.travel.Meal
import com.team.carescape.travel.Place
import com.team.carescape.travel.PlanItem
import com.team.carescape.travel.TravelResult
import com.team.carescape.travel.TravelTab
import com.team.carescape.travel.TravelViewModel
import com.team.carescape.travel.TripLength
import com.team.carescape.travel.TripProfile
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 여행 도우미(부가 서비스). 로비에서만 들어온다. 방탈출 진행 기록은 읽기만 한다(마지막 장소 공개 여부).
 * 정차 중에만 보인다(EscapeApp이 주행 중에는 화면 전체를 가린다).
 */
@Composable
fun TravelGuideScreen(game: GameViewModel) {
    val vm: TravelViewModel = viewModel()
    val story = game.story
    val revealed = story != null && (story.chapterIndex == story.episode.finalChapterIndex || story.phase == StoryPhase.FINAL_REVEAL)
    LaunchedEffect(revealed) { vm.updateFinalRevealed(revealed); if (vm.profile != null) vm.refresh() }

    GoldFrame(padTop = 24.dp, padH = 32.dp, padBottom = 20.dp) {
        Column(Modifier.fillMaxSize().testTag("travel-guide"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val profile = vm.profile
            Row(Modifier.fillMaxWidth().padding(end = 150.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Travel Guide", style = Esc.pinyon(30))
                    Text(vm.catalogStatus, Modifier.testTag("travel-catalog-status"), style = Esc.noto(12, Esc.label))
                }
                if (profile != null && !vm.editing) {
                    Row(
                        Modifier.border(1.dp, Esc.gold, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = vm::startEdit)
                            .testTag("travel-edit").padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(listOfNotNull(profile.length.label, profile.start?.let { "${it.monthValue}/${it.dayOfMonth} 출발" }, profile.companion.label).joinToString(" · "),
                            Modifier.testTag("travel-summary"), style = Esc.nanum(16))
                        Text("바꾸기", style = Esc.noto(13, Esc.gold))
                    }
                    Box(Modifier.weight(1f))
                    GuideMode.entries.forEach { m ->
                        ModeChip(if (m == GuideMode.SAVED) "${m.label} ${vm.visiblePlan.saved.size}" else m.label, vm.mode == m, "travel-mode-${m.name}") { vm.switchMode(m) }
                    }
                } else Box(Modifier.weight(1f))
                OutlineButton("로비", game::goLobby, Modifier.testTag("travel-lobby"), minHeight = 44.dp, fontSize = 15)
            }
            if (vm.editing || profile == null) ProfileForm(profile ?: TripProfile(), canCancel = profile != null, onSave = vm::saveProfile, onCancel = vm::cancelEdit)
            else GuideBody(vm, game, profile)
        }
    }
}

// ───────── 여행 정보 입력 ─────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileForm(initial: TripProfile, canCancel: Boolean, onSave: (TripProfile) -> Unit, onCancel: () -> Unit) {
    var p by remember { mutableStateOf(initial) }
    val today = remember { LocalDate.now() }
    Column(
        Modifier.fillMaxSize().parchment().border(1.dp, Esc.goldDim).padding(horizontal = 36.dp, vertical = 24.dp).verticalScroll(rememberScrollState()).testTag("travel-profile"),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("어떤 여행인가요?", style = Esc.nanum(28, Esc.ink))
        Text("입력한 정보는 이 기기에만 저장되고, 방탈출 진행에는 영향을 주지 않아요.", style = Esc.noto(15, Esc.inkSoft))
        Field("일정") { TripLength.entries.forEach { Chip(it.label, p.length == it, "travel-length-${it.name}") { p = p.copy(length = it) } } }
        Field("출발 날짜") {
            val d = p.start
            Chip("미정", d == null, "travel-date-none") { p = p.copy(startDate = null) }
            if (d == null) Chip("날짜 정하기", false, "travel-date-set") { p = p.copy(startDate = today.toString()) }
            else {
                Chip("◀", false, "travel-date-prev") { if (d.isAfter(today)) p = p.copy(startDate = d.minusDays(1).toString()) }
                Text("${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})", Modifier.testTag("travel-date"), style = Esc.nanum(20, Esc.ink))
                Chip("▶", false, "travel-date-next") { p = p.copy(startDate = d.plusDays(1).toString()) }
            }
        }
        Field("누구와") { TravelParty.entries.forEach { Chip(it.label, p.companion == it, "travel-with-${it.name}") { p = p.copy(companion = it) } } }
        Field("관심사 (여러 개)") {
            Interest.entries.forEach { i -> Chip(i.label, i in p.interests, "travel-like-${i.name}") { p = p.copy(interests = if (i in p.interests) p.interests - i else p.interests + i) } }
        }
        Field("식사 (여러 개)") {
            Meal.entries.forEach { m -> Chip(m.label, m in p.meals, "travel-meal-${m.name}") { p = p.copy(meals = if (m in p.meals) p.meals - m else p.meals + m) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ParchmentButton("이대로 추천받기", { onSave(p) }, Modifier.testTag("travel-save"), minWidth = 220.dp, minHeight = 52.dp)
            if (canCancel) OutlineButton("취소", onCancel, minHeight = 52.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(label, Modifier.width(150.dp), style = Esc.nanum(18, Esc.inkBody))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.border(if (on) 2.dp else 1.dp, if (on) Esc.seal else Esc.parchEdge)
            .background(if (on) Esc.seal.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Checkbox, onClick = onClick).testTag(tag)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) { Text(label, style = Esc.noto(16, if (on) Esc.seal else Esc.inkBody)) }
}

// ───────── 본문: 왼쪽 방문 순서 + 오른쪽 일정/둘러보기/저장 ─────────

/** 탭(카테고리)마다 지도 핀·칩 색. */
private fun tabColor(t: TravelTab): Color = when (t) {
    TravelTab.PARKING -> Color(0xFF5E8FC9)
    TravelTab.FOOD -> Color(0xFFD8794E)
    TravelTab.CAFE -> Color(0xFFC9A15A)
    TravelTab.SIGHT -> Color(0xFF4FA88E)
    TravelTab.ACTIVITY -> Color(0xFF9A86D6)
    TravelTab.STAY, TravelTab.NIGHT -> Color(0xFFB87AA8)
}

private fun tabMark(t: TravelTab): String = when (t) {
    TravelTab.PARKING -> "P"; TravelTab.FOOD -> "식"; TravelTab.CAFE -> "茶"; TravelTab.SIGHT -> "觀"
    TravelTab.ACTIVITY -> "體"; TravelTab.STAY -> "宿"; TravelTab.NIGHT -> "夜"
}

@Composable
private fun GuideBody(vm: TravelViewModel, game: GameViewModel, profile: TripProfile) {
    val days = vm.days()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RouteOverview(vm, days, Modifier.weight(1.1f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (vm.mode) {
                GuideMode.SCHEDULE -> SchedulePanel(vm, game, profile, days)
                GuideMode.EXPLORE -> ExplorePanel(vm, game, profile)
                GuideMode.SAVED -> SavedPanel(vm, game, days)
                GuideMode.TOUR -> TourGuidePanel(vm, game)
                GuideMode.CAR -> CarPlanPanel(game, GuideStops.all, vm::isLocked, Modifier.weight(1f))
            }
        }
    }
}

// ───────── 앱에서 준비한 코스 목록 ─────────

@Composable
private fun RouteOverview(vm: TravelViewModel, days: List<DayPlan>, modifier: Modifier) {
    val stops = GuideStops.all.filterNot { vm.isLocked(it.index) }
    val dayStops = days.getOrNull(vm.day - 1)?.items?.filterIsInstance<PlanItem.Stop>()?.map { it.stop.index }.orEmpty()
    CourseStopCards(stops, vm.selectedStop, dayStops, vm.isLocked(GuideStops.all.lastIndex), vm::select, modifier)
}

/** 화면 상태와 선택 콜백만 받으며 지도 SDK나 ViewModel을 소유하지 않는다. */
@Composable
internal fun CourseStopCards(
    stops: List<GuideStop>, selectedStop: Int, dayStops: List<Int>, finalLocked: Boolean,
    onSelectStop: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    Column(modifier.parchment().border(1.dp, Esc.goldDim).padding(18.dp).testTag("travel-course"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Course Guide", style = Esc.pinyon(28, Esc.wine))
        Text("방문할 곳을 골라 주변 장소를 확인하세요.", style = Esc.noto(14, Esc.inkSoft))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stops.forEach { stop ->
                val selected = stop.index == selectedStop
                Row(Modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp, if (selected) Esc.seal else Esc.parchEdge)
                    .background(if (selected) Esc.seal.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(role = Role.Button) { onSelectStop(stop.index) }.testTag("travel-course-stop-${stop.index}")
                    .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).background(Esc.seal, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${stop.index + 1}", style = Esc.noto(16, Esc.parchment, FontWeight.Bold))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stop.name, style = Esc.nanum(21, Esc.ink))
                        Text(if (stop.index in dayStops) "선택한 날짜의 방문 장소" else "코스에 포함된 장소", style = Esc.noto(12, Esc.inkSoft))
                    }
                    if (selected) Text("선택", style = Esc.noto(13, Esc.seal))
                }
            }
            if (finalLocked) Text("마지막 장소는 공개 후 표시됩니다.", Modifier.testTag("travel-course-final-locked"), style = Esc.noto(14, Esc.inkSoft))
        }
        Text("장소 정보는 미리 준비한 목록입니다.\n영업·요금·주차 가능 여부는 방문 전 확인하세요.", style = Esc.noto(12, Esc.inkSoft))
    }
}

// ───────── 일정 ─────────

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SchedulePanel(vm: TravelViewModel, game: GameViewModel, profile: TripProfile, days: List<DayPlan>) {
    val today = days.getOrNull(vm.day - 1) ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        days.forEach { d ->
            ModeChip("${d.day}일차" + (d.date?.let { " · ${it.monthValue}/${it.dayOfMonth}" } ?: ""), vm.day == d.day, "travel-day-${d.day}") { vm.selectDay(d.day) }
        }
    }
    Column(
        Modifier.fillMaxWidth().height(250.dp).background(Esc.panel).border(1.dp, Esc.goldDim).padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()).testTag("travel-timeline"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Timeline.of(today).forEach { t -> TimelineRow(t, today.day, vm) }
        today.closedStops().forEach { Text("⚠ ${it.name}은 ${it.closedOn!!.getDisplayName(TextStyle.FULL, Locale.KOREAN)} 휴관일 수 있어요. 방문 전 확인하세요.",
            Modifier.testTag("travel-closed-warning"), style = Esc.noto(13, Esc.alarm)) }
        Text("시간·이동 시간은 직선거리로 어림한 값이에요. 실제는 내비게이션에서 확인하세요.", style = Esc.noto(12, Esc.label))
    }
    CategoryChips(vm, profile)
    ResultCards(vm, game, Modifier.weight(1f))
}

@Composable
private fun TimelineRow(t: TimedItem, day: Int, vm: TravelViewModel) {
    val time = "%02d:%02d".format(t.start.hour, t.start.minute)
    t.travelBefore?.let {
        Text("   ┊  차로 약 ${it}분", style = Esc.noto(13, Esc.creamSoft))
    }
    when (val item = t.item) {
        is PlanItem.Stop -> Row(
            Modifier.fillMaxWidth().background(if (vm.selectedStop == item.stop.index) Esc.gold.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(role = Role.Button) { vm.select(item.stop.index) }.testTag("travel-stop-${item.stop.index}").padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(time, Modifier.width(52.dp), style = Esc.cormorant(17, Esc.gold, 0f, FontWeight.Bold))
            Box(Modifier.size(24.dp).background(Esc.seal, CircleShape), contentAlignment = Alignment.Center) {
                Text("${item.stop.index + 1}", style = Esc.cormorant(14, Esc.parchment, 0f, FontWeight.Bold))
            }
            Text(if (item.locked) "??? · 마지막 장소" else item.stop.name, Modifier.weight(1f), style = Esc.nanum(17))
            Text("관광 약 1시간", style = Esc.noto(12, Esc.creamSoft))
        }
        is PlanItem.Eat, is PlanItem.Sleep -> {
            val key = slotKey(day, item)!!
            val filled = vm.visiblePlan.place(key)
            val label = if (item is PlanItem.Eat) item.meal.label else "숙소"
            val near = GuideStops.all[(item as? PlanItem.Eat)?.nearIndex ?: (item as PlanItem.Sleep).nearIndex].name
            val active = vm.activeSlot == key
            Row(
                Modifier.fillMaxWidth().padding(start = 62.dp)
                    .border(if (active) 2.dp else 1.dp, if (active) Esc.gold else Esc.goldDim)
                    .background(if (filled != null) Esc.gold.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(role = Role.Button) { vm.pickSlot(key, item) }.testTag("travel-slot-$key").padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (item is PlanItem.Sleep) "체크인" else time, style = Esc.noto(12, Esc.creamSoft))
                Text(if (filled != null) "$label · ${filled.name}" else "+ $label 자리 · $near 근처", Modifier.weight(1f),
                    style = Esc.noto(15, if (filled != null) Esc.cream else Esc.creamMuted))
                if (filled != null) Text("비우기", Modifier.clickable(role = Role.Button) { vm.clearSlot(key) }.testTag("travel-slot-clear-$key").padding(4.dp), style = Esc.noto(12, Esc.gold))
                else if (active) Text("아래에서 고르세요", style = Esc.noto(12, Esc.gold))
            }
        }
    }
}

@Composable
private fun CategoryChips(vm: TravelViewModel, profile: TripProfile) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Itinerary.tabs(profile).forEach { t ->
            val on = vm.tab == t
            Row(
                Modifier.heightIn(min = 44.dp).border(if (on) 2.dp else 1.dp, if (on) tabColor(t) else Esc.goldDim, RoundedCornerShape(22.dp))
                    .background(if (on) tabColor(t).copy(alpha = 0.22f) else Esc.panel, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Tab) { vm.selectTab(t) }.testTag("travel-tab-${t.name}").padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(20.dp).background(tabColor(t), CircleShape), contentAlignment = Alignment.Center) { Text(tabMark(t), style = Esc.noto(10, Color.White, FontWeight.Bold)) }
                Text(t.label, style = Esc.nanum(15, if (on) Esc.cream else Esc.creamSoft))
            }
        }
    }
}

@Composable
private fun ResultCards(vm: TravelViewModel, game: GameViewModel, modifier: Modifier) {
    val stop = GuideStops.all[vm.selectedStop]
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            (if (vm.isLocked(stop.index)) "??? · 마지막 장소" else stop.name) + " 근처 ${vm.tab.label}" +
                (vm.activeSlot?.let { " · 담으면 일정 칸에 들어가요" } ?: ""),
            Modifier.testTag("travel-stop-title"), style = Esc.noto(14, Esc.gold),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (vm.isLocked(stop.index)) { LockedStop(vm); return@Box }
            when (val r = vm.result) {
                null -> Center("카테고리를 고르세요.")
                is TravelResult.Ok -> if (r.places.isEmpty()) Center("이 분류에 준비된 장소가 아직 없어요.\n주차장이나 다른 분류를 확인해 주세요.") else Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    r.places.forEachIndexed { i, p -> PlaceCard(p, i, vm, game) }
                }
            }
        }
        Text("수동 선정 목록 · 2026-11-15 확인 · 실시간 영업·주차 정보 없음", style = Esc.noto(11, Esc.label))
    }
}

@Composable
private fun PlaceCard(p: Place, i: Int, vm: TravelViewModel, game: GameViewModel) {
    val open = p == vm.selectedPlace
    val saved = vm.visiblePlan.isSaved(p.toSaved(vm.selectedStop, vm.tab).id)
    Column(
        Modifier.fillMaxWidth().border(if (open) 2.dp else 1.dp, if (open) Esc.gold else Esc.goldDim).background(if (open) Esc.gold.copy(alpha = 0.10f) else Esc.panel)
            .clickable(role = Role.Button) { vm.selectedPlace = if (open) null else p }.testTag("travel-place-$i").padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(26.dp).background(tabColor(vm.tab), CircleShape), contentAlignment = Alignment.Center) { Text(tabMark(vm.tab), style = Esc.noto(11, Color.White, FontWeight.Bold)) }
            Column(Modifier.weight(1f)) {
                Text(p.name, style = Esc.nanum(16))
                Text(listOfNotNull(p.category.takeIf { it.isNotBlank() }, p.distanceM?.let(::distanceLabel)).joinToString(" · "), style = Esc.noto(12, Esc.creamSoft))
            }
            OutlineButton(if (vm.activeSlot != null) "+ 일정에 담기" else if (saved) "저장됨" else "+ 담기",
                { vm.put(p) }, Modifier.testTag("travel-put-$i"), enabled = vm.activeSlot != null || !saved, minHeight = 40.dp, fontSize = 14)
        }
        if (open) {
            if (p.address.isNotBlank()) Text(p.address, style = Esc.noto(13, Esc.creamMuted))
            if (p.note.isNotBlank()) Text(p.note, style = Esc.noto(12, Esc.creamSoft))
            p.link?.let { Text("공식 안내: ${java.net.URI(it).host}", style = Esc.noto(11, Esc.label)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                p.phone?.let { Text("☎ $it", style = Esc.noto(13, Esc.creamMuted)) }
                Text("출처: ${p.source}", Modifier.weight(1f), style = Esc.noto(11, Esc.label))
                if (p.navigationLat != null && p.navigationLng != null) OutlineButton(p.navigationLabel, { if (!vm.isLocked(vm.selectedStop)) game.guideToPlace(p.navigationName, p.navigationLat, p.navigationLng) }, Modifier.testTag("travel-navigate"), minHeight = 40.dp, fontSize = 14)
            }
            game.sdkMessage?.let { Text(it, style = Esc.noto(12, Esc.creamSoft)) }
        }
    }
}

@Composable
private fun LockedStop(vm: TravelViewModel) {
    Column(
        Modifier.fillMaxSize().background(Esc.panel).border(1.dp, Esc.goldDim).padding(20.dp).testTag("travel-locked"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("방탈출에서 밝혀지는 장소예요", style = Esc.nanum(21))
        Text("사건을 진행 중이라면 최종 추리 뒤에 열려요.\n방탈출 없이 여행만 하신다면 지금 열어 볼 수 있어요.", style = Esc.noto(14, Esc.creamMuted, lineHeight = 1.6f), textAlign = TextAlign.Center)
        OutlineButton("스포일러 주의 · 그래도 보기", vm::optInSpoiler, Modifier.testTag("travel-spoiler"), minHeight = 44.dp, fontSize = 15)
    }
}

// ───────── 둘러보기: 경유지 아무 곳이나 골라 카테고리별로 ─────────

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ExplorePanel(vm: TravelViewModel, game: GameViewModel, profile: TripProfile) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GuideStops.all.forEach { s ->
            ModeChip(if (vm.isLocked(s.index)) "${s.index + 1} ???" else "${s.index + 1} ${s.name.take(5)}", vm.selectedStop == s.index, "travel-explore-stop-${s.index}") { vm.select(s.index) }
        }
    }
    CategoryChips(vm, profile)
    ResultCards(vm, game, Modifier.weight(1f))
}

// ───────── 관광 15분 가이드: 장소를 골라 꼭 볼 것을 체크 ─────────

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.TourGuidePanel(vm: TravelViewModel, game: GameViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GuideStops.all.forEach { s ->
            ModeChip(if (vm.isLocked(s.index)) "${s.index + 1} ???" else "${s.index + 1} ${s.name.take(5)}", vm.selectedStop == s.index, "travel-tour-stop-${s.index}") { vm.select(s.index) }
        }
    }
    val stop = GuideStops.all.getOrNull(vm.selectedStop)
    val spot = stop?.let { TourGuide.spot(it.checkpointId) }
    when {
        stop == null || spot == null -> Center("이 장소의 가이드는 준비 중이에요.")
        vm.isLocked(stop.index) -> Center("마지막 장소는 사건에서 공개된 뒤에 볼 수 있어요.")
        else -> TourChecklist(spot, stop.name, game, Modifier.weight(1f).verticalScroll(rememberScrollState()))
    }
}

// ───────── 저장 ─────────

@Composable
private fun SavedPanel(vm: TravelViewModel, game: GameViewModel, days: List<DayPlan>) {
    val slotOf = vm.visiblePlan.slots.entries.groupBy({ it.value }, { it.key })
    Text("담은 곳 ${vm.visiblePlan.saved.size}", style = Esc.nanum(20))
    if (vm.visiblePlan.saved.isEmpty()) { Center("아직 담은 곳이 없어요.\n일정이나 둘러보기에서 「+ 담기」를 눌러 모아 보세요.", "travel-saved-empty"); return }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("travel-saved"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        vm.visiblePlan.saved.forEach { s ->
            val tab = runCatching { TravelTab.valueOf(s.tab) }.getOrDefault(TravelTab.FOOD)
            val usedIn = slotOf[s.id].orEmpty().mapNotNull { key ->
                val (d, kind) = key.split(":").let { it[0].toInt() to it[1] }
                if (d > days.size) null else "${d}일차 " + (runCatching { Meal.valueOf(kind).label }.getOrNull() ?: "숙소")
            }
            Row(
                Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(26.dp).background(tabColor(tab), CircleShape), contentAlignment = Alignment.Center) { Text(tabMark(tab), style = Esc.noto(11, Color.White, FontWeight.Bold)) }
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = Esc.nanum(16))
                    Text(s.address, style = Esc.noto(12, Esc.creamSoft))
                    Text("출처: ${s.source}", style = Esc.noto(11, Esc.label))
                    if (s.note.isNotBlank()) Text(s.note, style = Esc.noto(12, Esc.creamSoft))
                    Text(listOf(GuideStops.all.getOrNull(s.stopIndex)?.let { if (vm.isLocked(it.index)) "???" else it.name } ?: "", s.category, usedIn.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        style = Esc.noto(12, Esc.creamSoft))
                }
                if (s.navigationLat != null && s.navigationLng != null) OutlineButton(s.navigationLabel, { if (!vm.isLocked(s.stopIndex)) game.guideToPlace(s.navigationName, s.navigationLat, s.navigationLng) }, minHeight = 40.dp, fontSize = 14)
                OutlineButton("빼기", { vm.unsave(s.id) }, Modifier.testTag("travel-unsave"), minHeight = 40.dp, fontSize = 14)
            }
        }
        game.sdkMessage?.let { Text(it, style = Esc.noto(12, Esc.creamSoft)) }
    }
}

@Composable
private fun ModeChip(label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 40.dp).border(if (on) 2.dp else 1.dp, if (on) Esc.gold else Esc.goldDim).background(if (on) Esc.gold.copy(alpha = 0.18f) else Esc.panel)
            .clickable(role = Role.Tab, onClick = onClick).testTag(tag).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Esc.nanum(15, if (on) Esc.cream else Esc.creamSoft)) }
}

@Composable
private fun Center(text: String, tag: String = "travel-message") {
    Box(Modifier.fillMaxSize().background(Esc.panel).border(1.dp, Esc.goldDim).padding(20.dp), contentAlignment = Alignment.Center) {
        Text(text, Modifier.testTag(tag), style = Esc.noto(15, Esc.creamMuted, lineHeight = 1.6f), textAlign = TextAlign.Center)
    }
}

private fun distanceLabel(m: Int) = if (m < 1000) "직선 ${m}m" else "직선 %.1fkm".format(m / 1000.0)
