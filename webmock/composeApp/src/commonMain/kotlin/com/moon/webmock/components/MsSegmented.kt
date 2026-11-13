package com.moon.webmock.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.moon.webmock.theme.MsColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// MsSegmented + BareIconButton, ported verbatim from the app's MsControls.kt.
// ---------------------------------------------------------------------------

@Composable
fun MsSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Indices that can't be picked (rendered greyed, taps ignored) — the
     *  Fanart.tv poster option uses this until a key is saved. */
    disabled: Set<Int> = emptySet(),
) {
    if (options.isEmpty()) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val padPx = with(density) { 4.dp.toPx() }
    var trackWidthPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }

    // Equal segments from the track width alone: segment i runs from
    // i * segW to (i + 1) * segW inside the 4dp track padding.
    val segW = if (trackWidthPx > 0) (trackWidthPx - 2 * padPx) / options.size else 0f
    val pillW = (segW - 2 * padPx).coerceAtLeast(0f)
    fun pillX(i: Int) = i.coerceIn(0, options.size - 1) * segW + padPx

    val pillXAnim = remember { Animatable(0f) }
    var pillReady by remember { mutableStateOf(false) }

    // Snap into place on first measure, slide on every change after that.
    // The glide uses the Library tabs' spring (0.9 / 260 — slow and
    // visible, moon's fix 2026-11-11) so the pill moves like the rest
    // of the app instead of snapping.
    LaunchedEffect(selected, segW) {
        if (segW <= 0f) return@LaunchedEffect
        val target = pillX(selected.coerceIn(options.indices))
        if (!pillReady) {
            pillXAnim.snapTo(target)
            pillReady = true
        } else {
            pillXAnim.animateTo(
                target,
                animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = 260f,
                ),
            )
        }
    }

    val slideSpec = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = 260f,
    )
    val dragState = rememberDraggableState { delta ->
        scope.launch {
            pillXAnim.snapTo(
                (pillXAnim.value + delta).coerceIn(pillX(0), pillX(options.size - 1)),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { trackWidthPx = it.width }
            .clip(CircleShape)
            .background(MsColors.SurfaceAlt)
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (segW > 0f) {
                        val center = pillXAnim.value + pillW / 2f
                        val nearest = (center / segW).toInt()
                            .coerceIn(0, options.size - 1)
                        if (nearest != selected && nearest !in disabled) {
                            onSelect(nearest) // LaunchedEffect slides the pill there.
                        } else {
                            pillXAnim.animateTo(pillX(selected), slideSpec)
                        }
                    }
                },
            )
            .padding(4.dp),
    ) {
        // The sliding pill (behind the labels).
        Box(
            modifier = Modifier
                .offset { IntOffset(pillXAnim.value.roundToInt(), 0) }
                .width(with(density) { pillW.toDp() })
                .height(with(density) { rowHeightPx.toDp() })
                .clip(CircleShape)
                .background(MsColors.Accent),
        )
        // Labels on top — equal segments, taps select.
        Row(modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { rowHeightPx = it.height }) {
            options.forEachIndexed { i, label ->
                val isSel = i == selected
                val isDisabled = i in disabled
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable(enabled = !isDisabled) { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isDisabled -> MsColors.TextSecondary.copy(alpha = 0.45f)
                            isSel -> MsColors.OnAccent
                            else -> MsColors.TextSecondary
                        },
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Bare icon button — no background circle, just a tinted glyph. */
@Composable
fun BareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MsColors.TextPrimary,
    enabled: Boolean = true,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else MsColors.TextSecondary.copy(alpha = 0.4f),
        )
    }
}

