package com.moon.moonstream.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.moonstream.ui.theme.MsColors
import com.moon.moonstream.ui.theme.MsRadii

// ---------------------------------------------------------------------------
// Mockup-faithful shared controls. Flat cards, lime pills with dark text,
// Inter everywhere via MaterialTheme typography.
// ---------------------------------------------------------------------------

/** Primary action: lime filled pill, dark semibold text. */
@Composable
fun LimePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 15,
) {
    val bg = if (enabled) MsColors.Lime else MsColors.TrackOff
    val fg = if (enabled) MsColors.OnLime else MsColors.TextSecondary
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize.sp,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Secondary action: outlined pill, lime text. */
@Composable
fun MsOutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 14,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, MsColors.TextSecondary.copy(alpha = 0.55f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = fontSize.sp,
            color = if (enabled) MsColors.Lime else MsColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Lime text button (e.g. "Create entry", "wrong title?"). */
@Composable
fun LimeTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: Int = 14,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        fontSize = fontSize.sp,
        color = MsColors.Lime,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

/** Flat mockup card: #161B24, 20dp corners, no shadow. */
@Composable
fun MsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(MsRadii.Card),
        color = MsColors.Surface,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = content,
    )
}

/** Rounded outline text field, 16dp corners, lime focus ring. */
@Composable
fun MsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = if (hint.isNotEmpty()) {
            { Text(hint, color = MsColors.TextSecondary) }
        } else null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(MsRadii.Field),
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MsColors.TextPrimary,
            unfocusedTextColor = MsColors.TextPrimary,
            cursorColor = MsColors.Lime,
            focusedBorderColor = MsColors.Lime,
            unfocusedBorderColor = MsColors.TextSecondary.copy(alpha = 0.35f),
            focusedPlaceholderColor = MsColors.TextSecondary,
            unfocusedPlaceholderColor = MsColors.TextSecondary,
        ),
    )
}

/** Neutral gray outline circle with an "i" — the on-brand info affordance. */
@Composable
fun InfoDot(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(1.5.dp, MsColors.TextSecondary, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "i",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextSecondary,
        )
    }
}

/** Lime-tinted quality chip, 8dp corners. */
@Composable
fun QualityChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MsRadii.Chip))
            .background(MsColors.Lime.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.Lime,
        )
    }
}

/** Blue ANIME badge. */
@Composable
fun AnimeBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MsColors.Blue)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ANIME",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

/** Mockup toggle: lime track when on, dark track when off. */
@Composable
fun MsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MsColors.OnLime,
            checkedTrackColor = MsColors.Lime,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = MsColors.TextSecondary,
            uncheckedTrackColor = MsColors.TrackOff,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Mockup slider: lime. */
@Composable
fun MsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = MsColors.Lime,
            activeTrackColor = MsColors.Lime,
            inactiveTrackColor = MsColors.TrackOff,
        ),
    )
}

/** Pulsing status dot (red = off, green = on). */
@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, pulse: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "dotPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "dotScale",
    )
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(900)),
        label = "dotAlpha",
    )
    Box(
        modifier = modifier
            .size(14.dp)
            .scale(if (pulse) scale else 1f)
            .alpha(if (pulse) alpha else 1f)
            .background(color, CircleShape),
    )
}

/**
 * Power glyph built from primitives (font-independent — never a text glyph).
 * A determinate progress arc (80% sweep, rotated so the gap sits at the top)
 * plus a vertical bar. No Canvas/Stroke needed.
 */
@Composable
fun PowerGlyph(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 3.dp) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = 0.8f,
            modifier = Modifier
                .fillMaxSize()
                .rotate(36f),
            color = color,
            strokeWidth = strokeWidth,
            strokeCap = StrokeCap.Round,
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = strokeWidth / 2)
                .width(strokeWidth)
                .fillMaxHeight(0.34f)
                .clip(RoundedCornerShape(strokeWidth / 2))
                .background(color),
        )
    }
}

/**
 * The mockup's big power switch: large pill track, subtle outer ring,
 * white circular thumb with a dark power glyph, springy toggle animation.
 * NOT the default Material Switch.
 */
@Composable
fun PowerSwitch(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val knobOffset by animateFloatAsState(
        targetValue = if (on) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "powerKnob",
    )
    val trackWidth = 128.dp
    val trackHeight = 68.dp
    val thumbSize = 54.dp
    val travel = trackWidth - thumbSize - 14.dp // 7dp inset each side

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(if (on) MsColors.Lime.copy(alpha = 0.22f) else MsColors.SurfaceAlt)
            .border(
                3.dp,
                if (on) MsColors.Lime else MsColors.TextSecondary.copy(alpha = 0.5f),
                CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 7.dp + travel * knobOffset)
                .size(thumbSize)
                .background(Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(color = MsColors.Bg, modifier = Modifier.size(26.dp))
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

/** Sheet content wrapper: title + body, mockup spacing. */
@Composable
fun MsSheetContent(title: String, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )
        body()
    }
}

/** Section label inside forms. */
@Composable
fun MsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MsColors.TextPrimary,
    )
}

/** Lime group header for Settings. */
@Composable
fun MsGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MsColors.Lime,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Key-value stat row used on the Server screen. */
@Composable
fun MsStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    MsCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MsColors.TextPrimary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}
