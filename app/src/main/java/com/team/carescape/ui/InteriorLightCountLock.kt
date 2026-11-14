package com.team.carescape.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Current states in front-left, front-right, rear-left, rear-right order. */
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
    val allKnown = lights.size == 4 && lights.all { it != null }
    val canSubmit = enabled && allKnown
    val latestEnabled by rememberUpdatedState(enabled)
    val latestSdkInput by rememberUpdatedState(sdkInput)
    val latestCanSubmit by rememberUpdatedState(canSubmit)
    val latestOnToggle by rememberUpdatedState(onToggle)
    val latestOnSubmit by rememberUpdatedState(onSubmit)
    val positions = listOf("앞 왼쪽", "앞 오른쪽", "뒤 왼쪽", "뒤 오른쪽")

    Column(
        modifier = modifier
            .testTag("interior-light-lock")
            .background(Esc.panel)
            .border(1.dp, Esc.goldDim)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("봉화와 실내 조명", style = Esc.noto(20, Esc.cream))
        Text(
            text = if (sdkInput) "차량에서 읽은 실내 조명 상태" else "MOCK 시연 · 조명 카드를 눌러 바꿔 보세요",
            style = Esc.noto(13, Esc.creamSoft),
            textAlign = TextAlign.Center,
        )
        repeat(2) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val light = lights.getOrNull(index)
                    val state = when (light) {
                        true -> "켜짐"
                        false -> "꺼짐"
                        null -> "미확인"
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 100.dp)
                            .background(if (light == true) Esc.gold.copy(alpha = 0.18f) else Color.Transparent)
                            .border(1.dp, if (light == true) Esc.gold else Esc.goldDim)
                            .testTag("interior-light-$index")
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${positions[index]} 실내 조명"
                                stateDescription = state
                            }
                            .then(
                                if (sdkInput) Modifier else Modifier.clickable(
                                    enabled = enabled,
                                    role = Role.Button,
                                    onClick = {
                                        if (latestEnabled && !latestSdkInput) latestOnToggle(index)
                                    },
                                ),
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        Text(positions[index], style = Esc.noto(14, Esc.cream))
                        Text(
                            text = if (light == true) "🔥" else if (light == null) "?" else "○",
                            style = Esc.noto(24, if (light == true) Esc.gold else Esc.creamSoft),
                        )
                        Text(state, style = Esc.noto(14, Esc.creamSoft))
                    }
                }
            }
        }
        Text(
            text = if (allKnown) "켜진 실내 조명: ${lights.count { it == true }}개" else "켜진 실내 조명 수 확인 중",
            modifier = Modifier.testTag("interior-light-count"),
            style = Esc.noto(16, Esc.cream),
            textAlign = TextAlign.Center,
        )
        if (message.isNotBlank()) {
            Text(message, style = Esc.noto(13, Esc.creamSoft), textAlign = TextAlign.Center)
        }
        Text("상태를 맞춘 뒤 제출하세요.", style = Esc.noto(13, Esc.creamSoft))
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
