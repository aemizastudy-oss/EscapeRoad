package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.StoryPhase
import com.team.carescape.travel.CarEnergy
import com.team.carescape.travel.CarTripPlanner
import com.team.carescape.travel.EnergyVerdict
import com.team.carescape.travel.GuideStop
import com.team.carescape.travel.TourCheckStore
import com.team.carescape.travel.TourSpot

// ───────── 관광 15분 가이드 ─────────

/** 장소별 '꼭 볼 것' 체크리스트. 여행 도우미와 관광 중 화면에서 같이 쓴다. [large]는 관광 중 화면(차 밖에서 보는 큰 글씨). */
@Composable
internal fun TourChecklist(spot: TourSpot, place: String, game: GameViewModel, modifier: Modifier = Modifier, large: Boolean = false) {
    val done = spot.looks.indices.count { TourCheckStore.key(spot.checkpointId, it) in game.tourChecks }
    Column(
        modifier.background(Esc.panel).border(1.dp, Esc.goldDim).padding(if (large) 20.dp else 14.dp).testTag("tour-guide-${spot.checkpointId}"),
        verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("관광 15분 가이드", style = Esc.noto(if (large) 15 else 13, Esc.gold, lineHeight = 1.2f))
                Text("$place · 꼭 볼 것", style = Esc.nanum(if (large) 24 else 19))
            }
            Text("$done / ${spot.looks.size}", style = Esc.nanum(if (large) 22 else 17, if (done == spot.looks.size) Esc.gold else Esc.creamSoft))
        }
        spot.looks.forEachIndexed { i, look ->
            val key = TourCheckStore.key(spot.checkpointId, i)
            val checked = key in game.tourChecks
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Checkbox) { game.toggleTourCheck(key) }
                    .testTag("tour-check-$key").padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(if (large) 30.dp else 24.dp).border(2.dp, Esc.gold).background(if (checked) Esc.gold else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { if (checked) Text("✓", style = Esc.noto(if (large) 18 else 15, Color(0xFF1C130C))) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(look.title, style = Esc.nanum(if (large) 20 else 16, if (checked) Esc.creamSoft else Esc.cream))
                        if (look.inGame) Text(
                            "사건 단서", Modifier.border(1.dp, Esc.oxblood).background(Esc.oxblood.copy(alpha = 0.5f)).padding(horizontal = 6.dp, vertical = 1.dp),
                            style = Esc.noto(if (large) 13 else 11, Esc.cream, lineHeight = 1.2f),
                        )
                    }
                    Text(look.detail, style = Esc.noto(if (large) 16 else 13, Esc.creamMuted, lineHeight = 1.45f))
                }
            }
        }
        Text("📷 사진 찍기 좋은 곳 · ${spot.photo}", style = Esc.noto(if (large) 16 else 13, Esc.creamSoft))
        spot.note?.let { Text(it, style = Esc.noto(if (large) 14 else 12, Esc.label)) }
    }
}

// ───────── 내 차 맞춤 이동 계획 ─────────

/** 남은 코스와 차의 에너지·주행 가능 거리를 견준다. 거리·시간은 직선 거리로 만든 추정치다. */
@Composable
internal fun CarPlanPanel(game: GameViewModel, stops: List<GuideStop>, isLocked: (Int) -> Boolean, modifier: Modifier = Modifier) {
    val story = game.story
    // 사건 진행 중이면 지금 장소부터, 시작 전이거나 끝났으면 전체 코스를 계획한다.
    val inProgress = story != null && story.phase != StoryPhase.ENDED
    val from = if (inProgress) story!!.chapterIndex else 0
    val energy = game.vehicle.let { CarEnergy(it.batteryPercent, it.fuelPercent, it.rangeKm) }
    val plan = CarTripPlanner.plan(stops, from, energy)
    val tone = when (plan.verdict) {
        EnergyVerdict.ENOUGH -> Color(0xFF7FB98A)
        EnergyVerdict.TIGHT, EnergyVerdict.LOW -> Color(0xFFE0B055)
        EnergyVerdict.CHARGE -> Color(0xFFE07A5F)
        else -> Esc.creamSoft
    }
    fun name(s: GuideStop) = if (isLocked(s.index)) "마지막 장소 (공개 전)" else s.name
    Column(modifier.verticalScroll(rememberScrollState()).testTag("travel-car-plan"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("내 차 상태", style = Esc.cormorant(13, Esc.gold, 0.2f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EnergyTile("배터리", energy.batteryPercent?.let { "$it%" }, Modifier.weight(1f))
                EnergyTile("연료", energy.fuelPercent?.let { "$it%" }, Modifier.weight(1f))
                EnergyTile("주행 가능", energy.rangeKm?.let { "${it}km" }, Modifier.weight(1f))
            }
            Text(plan.message, Modifier.testTag("travel-car-verdict"), style = Esc.noto(15, tone, lineHeight = 1.5f))
            Text(if (game.isMock) "시연 데이터 · 실제 차량 값이 아니에요" else "차량에서 읽은 값 · 못 읽은 값은 '—'로 둬요", style = Esc.noto(12, Esc.label))
        }
        if (plan.legs.isEmpty()) {
            Text("마지막 장소예요. 남은 이동 구간이 없어요.", style = Esc.noto(15, Esc.creamMuted))
        } else {
            Column(Modifier.fillMaxWidth().background(Esc.panel).border(1.dp, Esc.goldDim).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (inProgress) "남은 이동" else "전체 코스", Modifier.weight(1f), style = Esc.nanum(17))
                    Text("약 ${plan.remainingKm}km · 운전 약 ${plan.driveMinutes}분 · 관광 ${plan.tourMinutes}분", style = Esc.noto(13, Esc.gold))
                }
                plan.legs.forEachIndexed { i, leg ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${leg.from.index + 1}→${leg.to.index + 1}", Modifier.width(44.dp), style = Esc.noto(13, if (i == 0) Esc.gold else Esc.label))
                        Text("${name(leg.from)} → ${name(leg.to)}", Modifier.weight(1f), style = Esc.noto(14, if (i == 0) Esc.cream else Esc.creamSoft), maxLines = 1)
                        Text("약 ${leg.estimateKm}km · ${leg.estimateMinutes}분", style = Esc.noto(13, Esc.creamSoft))
                    }
                }
                plan.restHint?.let { Text(it, style = Esc.noto(13, Color(0xFFE0B055))) }
                Text(
                    "거리·시간은 장소 사이 직선 거리 × ${CarTripPlanner.ROAD_FACTOR}, 평균 ${CarTripPlanner.CITY_KMH.toInt()}km/h로 만든 추정이에요. 실제 경로·교통은 길 안내에서 확인하세요.",
                    style = Esc.noto(12, Esc.label, lineHeight = 1.45f),
                )
            }
        }
        if (plan.verdict == EnergyVerdict.CHARGE || plan.verdict == EnergyVerdict.TIGHT || plan.verdict == EnergyVerdict.LOW) {
            Text(
                "충전소·주유소는 정차한 상태에서 차량 내비게이션의 주변 검색으로 찾아 주세요. 운전 중에는 화면을 조작하지 마세요.",
                Modifier.fillMaxWidth().border(1.dp, tone).padding(12.dp), style = Esc.noto(14, Esc.cream, lineHeight = 1.5f),
            )
        }
    }
}

@Composable
private fun EnergyTile(label: String, value: String?, modifier: Modifier) {
    Column(modifier.border(1.dp, Esc.goldDim).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Esc.noto(12, Esc.label))
        Text(value ?: "—", style = Esc.nanum(22, if (value == null) Esc.label else Esc.cream), textAlign = TextAlign.Center)
    }
}
