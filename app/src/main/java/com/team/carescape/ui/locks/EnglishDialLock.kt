package com.team.carescape.ui.locks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.carescape.R
import com.team.carescape.game.LockInput
import com.team.carescape.ui.Esc
import com.team.carescape.ui.ParchmentButton

/** The text editor owns IME focus; callers own the answer and submit policy. */
@Composable
fun EnglishDialLock(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    length: Int = 5,
    feedbackContent: (@Composable () -> Unit)? = null,
) {
    require(length == 5) { "The supplied English lock artwork has five windows." }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(enabled) {
        if (!enabled) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    DisposableEffect(focusManager, keyboard) {
        onDispose {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    Column(
        modifier.widthIn(max = 360.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("자물쇠를 눌러 영어 5글자를 입력하세요", style = Esc.noto(16, Esc.creamMuted))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val unit = maxWidth / 440f
            BasicTextField(
                value = value,
                onValueChange = { raw -> if (enabled) onValueChange(LockInput.normalizeEnglish(raw, length)) },
                modifier = Modifier
                    .size(maxWidth, unit * 480f)
                    .semantics { contentDescription = "영어 자물쇠 입력" },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    // Completing the keyboard must never evaluate or submit an answer.
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize()) {
                        Image(painterResource(R.drawable.english_lock), null, Modifier.fillMaxSize())
                        repeat(length) { index ->
                            Box(
                                Modifier
                                    .offset(unit * (60f + index * 68f), unit * 300f)
                                    .size(unit * 48f, unit * 72f)
                                    .background(Color(0xFFF3E7CB)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    value.getOrNull(index)?.toString() ?: "",
                                    style = Esc.cormorant(42, Esc.ink, spacing = 0f)
                                        .copy(fontSize = (42f * unit.value).sp),
                                )
                            }
                        }
                        // Keep the genuine editor in the layout for IME, selection and semantics.
                        // Its decoration above renders the characters in the supplied artwork.
                        Box(Modifier.fillMaxSize().alpha(0f), contentAlignment = Alignment.Center) {
                            innerTextField()
                        }
                    }
                },
            )
        }
        ParchmentButton(
            "제출",
            onClick = {
                if (enabled && value.length == length && value == LockInput.normalizeEnglish(value, length)) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSubmit(value)
                }
            },
            enabled = enabled && value.length == length,
            minWidth = 180.dp,
            minHeight = 48.dp,
        )
        feedbackContent?.invoke()
    }
}
