package com.example.rhythme

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What the big counter shows right now: [tenths] of a second out of [ofMs], with a caption. */
data class PanelReadout(val tenths: Long, val ofMs: Long, @StringRes val captionRes: Int, val color: Color)

/**
 * Practice panel: slides down from the top of the screen over the controls while the player
 * practices, because the pad's own text is under the finger. Shows the chosen rhythm and the
 * running time in a large font. Leaves by itself after a pause, or when pushed up / tapped.
 */
@Composable
fun PracticePanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    bpm: Int,
    timeSignature: TimeSignature,
    note: NoteLength,
    rest: NoteLength,
    noteMs: Long,
    restMs: Long,
    demoMode: Boolean,
    readout: () -> PanelReadout,
    modifier: Modifier = Modifier
) {
    val dismiss by rememberUpdatedState(onDismiss)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.93f))
                .pointerInput(Unit) {
                    // A push upward sends the panel back
                    var pushed = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pushed = 0f },
                        onVerticalDrag = { _, dy ->
                            pushed += dy
                            if (pushed < -24.dp.toPx()) dismiss()
                        }
                    )
                }
                .clickable { dismiss() }
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val onPanel = MaterialTheme.colorScheme.onSurfaceVariant

            // Summary of what was chosen
            Text(
                stringResource(R.string.bpm_label, bpm) + "  ·  " + Tempo.nameFor(bpm) + "  ·  " + timeSignature.label +
                    "  ·  " + stringResource(if (demoMode) R.string.mode_demo else R.string.mode_follow),
                fontSize = 14.sp,
                color = onPanel,
                maxLines = 1
            )
            Row(
                modifier = Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                NoteGlyph(note, onPanel, height = 30.dp)
                Text(stringResource(R.string.seconds_2dp, noteMs / 1000.0), fontSize = 18.sp, color = onPanel)
                Spacer(Modifier.width(24.dp))
                RestGlyph(rest, onPanel, height = 30.dp)
                Text(stringResource(R.string.seconds_2dp, restMs / 1000.0), fontSize = 18.sp, color = onPanel)
            }

            // The running time, large; recomposes only when a tenth of a second passes
            val shown by remember { derivedStateOf(readout) }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    stringResource(R.string.seconds_1dp, shown.tenths / 10.0),
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    color = shown.color,
                    maxLines = 1
                )
                Text(
                    "  / " + stringResource(R.string.seconds_2dp, shown.ofMs / 1000.0),
                    fontSize = 20.sp,
                    color = onPanel,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
            Text(stringResource(shown.captionRes), fontSize = 20.sp, color = shown.color)

            // Handle: hints that the panel can be pushed back up
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .background(onPanel.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
            )
        }
    }
}
