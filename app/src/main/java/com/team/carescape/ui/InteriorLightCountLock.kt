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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.team.carescape.game.BeaconLightMapping
import com.team.carescape.game.CarVisual

/** Fill from the left using one base flame plus the number of current ON reading lamps. */
@Composable
internal fun InteriorLightCountLock(
    lights: List<Boolean?>,
    enabled: Boolean,
    sdkInput: Boolean,
    message: String,
    onToggle: (Int) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    feedbackContent: @Composable () -> Unit = {},
) {
    val allKnown = lights.size == BeaconLightMapping.LIGHT_COUNT && lights.all { it != null }
    val litCount = if (allKnown) BeaconLightMapping.beaconCount(lights.count { it == true }) else null
    val canSubmit = enabled && allKnown
    val latestLights by rememberUpdatedState(lights)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSdkInput by rememberUpdatedState(sdkInput)
    val latestCanSubmit by rememberUpdatedState(canSubmit)
    val latestOnToggle by rememberUpdatedState(onToggle)
    val latestOnSubmit by rememberUpdatedState(onSubmit)

    Column(
        modifier = modifier
            .testTag("interior-light-lock")
            .background(Esc.panel)
            .border(1.dp, Esc.goldDim)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("봉화", style = Esc.noto(20, Esc.cream))
        Text("첫 봉화는 항상 켜져 있습니다.", style = Esc.noto(14, Esc.creamSoft), textAlign = TextAlign.Center)
        Text(
            text = if (sdkInput) "켜진 실내 독서등 수만큼 왼쪽부터 표시합니다." else "MOCK 시연 · 꺼진 봉화 +1 / 켜진 봉화 −1",
            style = Esc.noto(13, Esc.creamSoft),
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(BeaconLightMapping.BEACON_COUNT) { slot ->
                val fixed = slot < BeaconLightMapping.BASE_LIT
                val lightIndex = slot - BeaconLightMapping.BASE_LIT
                val light = if (fixed) true else litCount?.let { slot < it }
                val state = when (light) {
                    true -> "켜짐"
                    false -> "꺼짐"
                    null -> "미확인"
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = 48.dp)
                        .heightIn(min = 128.dp)
                        .testTag(if (fixed) "beacon-base" else "interior-light-$lightIndex")
                        .semantics(mergeDescendants = true) {
                            contentDescription = if (fixed) "1번 봉화, 항상 켜짐, 고정"
                                else "${slot + 1}번 봉화"
                            stateDescription = if (fixed) "항상 켜짐 · 고정" else state
                        }
                        .then(
                            if (fixed || sdkInput) Modifier else Modifier.clickable(
                                enabled = canSubmit,
                                role = Role.Button,
                                onClick = {
                                    val current = latestLights
                                    if (latestEnabled && !latestSdkInput &&
                                        current.size == BeaconLightMapping.LIGHT_COUNT && current.all { it != null }
                                    ) {
                                        val count = BeaconLightMapping.beaconCount(current.count { it == true })
                                        val rawIndex = if (slot < count) current.indexOfLast { it == true }
                                            else current.indexOfFirst { it == false }
                                        if (rawIndex >= 0) latestOnToggle(rawIndex)
                                    }
                                },
                            ),
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Text("${slot + 1}", style = Esc.noto(13, Esc.gold))
                    Box(Modifier.size(40.dp, 52.dp), contentAlignment = Alignment.Center) {
                        // Keep each beacon pit and restore the previous reading-light flame icon.
                        LockVisual(CarVisual.Beacon(1), emptyList())
                        if (light == true) {
                            Text("🔥", Modifier.align(Alignment.TopCenter), style = Esc.noto(24, Esc.gold))
                        } else if (light == null) {
                            Text("?", Modifier.padding(bottom = 18.dp), style = Esc.noto(22, Esc.cream))
                        }
                    }
                    Text(
                        text = if (fixed) "기본\n고정" else "추가\n불씨",
                        style = Esc.noto(12, Esc.creamSoft),
                        textAlign = TextAlign.Center,
                    )
                    Text(state, style = Esc.noto(13, Esc.creamSoft), textAlign = TextAlign.Center)
                }
            }
        }
        Text(
            text = if (allKnown) {
                "켜진 봉화: $litCount / ${BeaconLightMapping.BEACON_COUNT}"
            } else "봉화 수 확인 중",
            modifier = Modifier.testTag("interior-light-count"),
            style = Esc.noto(16, Esc.cream),
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(message, style = Esc.noto(13, Esc.creamSoft), textAlign = TextAlign.Center)
        }
        Text("봉화를 맞춘 뒤 제출하세요.", style = Esc.noto(13, Esc.creamSoft))
        ParchmentButton(
            text = "제출",
            onClick = { if (latestCanSubmit) latestOnSubmit() },
            modifier = Modifier.testTag("story-puzzle-submit"),
            enabled = canSubmit,
            minWidth = 140.dp,
            minHeight = 48.dp,
        )
        feedbackContent()
    }
}
