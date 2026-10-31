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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import android.widget.Toast
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
    val bg = if (enabled) MsColors.Accent else MsColors.TrackOff
    val fg = if (enabled) MsColors.OnAccent else MsColors.TextSecondary
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
            color = if (enabled) MsColors.Accent else MsColors.TextSecondary,
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
        color = MsColors.Accent,
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
    // Single-line fields get a compact 48dp field so the box hugs the text
    // instead of floating in Material's roomy default padding. Multi-line
    // fields keep the standard OutlinedTextField.
    // NOTE: forcing 48dp on OutlinedTextField clips the top of the text —
    // its inner padding is built for a 56dp+ box (and this Compose version
    // has no contentPadding override). So the compact field is a
    // BasicTextField with a hand-drawn border: exact height, text perfectly
    // centered, nothing to clip.
    val compact = singleLine && minLines <= 1
    if (compact) {
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            hint = hint,
            keyboardOptions = keyboardOptions,
            trailingIcon = trailingIcon,
        )
    } else {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            textStyle = MaterialTheme.typography.bodyMedium,
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
                cursorColor = MsColors.Accent,
                focusedBorderColor = MsColors.Accent,
                unfocusedBorderColor = MsColors.TextSecondary.copy(alpha = 0.35f),
                focusedPlaceholderColor = MsColors.TextSecondary,
                unfocusedPlaceholderColor = MsColors.TextSecondary,
            ),
        )
    }
}

/**
 * Compact 48dp single-line text field with a hand-drawn border.
 * The text is vertically centered in the full inner height, so it can
 * never clip the way a height-forced OutlinedTextField does.
 */
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        if (focused) MsColors.Accent else MsColors.TextSecondary.copy(alpha = 0.35f)
    val borderWidth = if (focused) 2.dp else 1.dp

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(48.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MsColors.TextPrimary),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MsColors.Accent),
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MsRadii.Field))
                    .border(borderWidth, borderColor, RoundedCornerShape(MsRadii.Field))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && hint.isNotEmpty()) {
                        Text(
                            hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MsColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        },
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
            .background(MsColors.Accent.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MsColors.Accent,
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
            checkedThumbColor = MsColors.OnAccent,
            checkedTrackColor = MsColors.Accent,
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
            thumbColor = MsColors.Accent,
            activeTrackColor = MsColors.Accent,
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

    // CRASH FIX (v0.3.0): the bouncy spring overshoots below 0 when toggling
    // 1 -> 0, which made `7.dp + travel * knobOffset` negative. Negative
    // padding throws IllegalArgumentException ("Padding must be non-negative")
    // and killed the app on every server-off toggle. Clamp the thumb travel.
    val clampedOffset = knobOffset.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(if (on) MsColors.Accent.copy(alpha = 0.22f) else MsColors.SurfaceAlt)
            .border(
                3.dp,
                if (on) MsColors.Accent else MsColors.TextSecondary.copy(alpha = 0.5f),
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
                .padding(start = 7.dp + travel * clampedOffset)
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
        color = MsColors.Accent,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Key-value stat row used on the Server screen. */
@Composable
fun MsStatBox(label: String, value: String, modifier: Modifier = Modifier) {    MsCard(modifier = modifier) {
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

/**
 * Design-system dropdown: SurfaceAlt field, 16dp corners, lime check on the
 * selected option. Used by language pickers, quality pickers, etc.
 */
@Composable
fun MsDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(MsRadii.Field))
                .background(MsColors.SurfaceAlt)
                .border(
                    1.dp,
                    if (expanded) MsColors.Accent else MsColors.TextSecondary.copy(alpha = 0.35f),
                    RoundedCornerShape(MsRadii.Field),
                )
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MsColors.TextSecondary,
                    )
                }
                Text(
                    text = selected,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MsColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Open options",
                tint = if (expanded) MsColors.Accent else MsColors.TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MsColors.SurfaceAlt),
        ) {
            options.forEach { option ->
                val isSel = option == selected
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSel) MsColors.Accent else MsColors.TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSel) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MsColors.Accent,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

/**
 * Small pill segmented control for settings (poster style, anime numbering).
 * Selected pill = accent fill + dark text; unselected = dark fill + gray text.
 *
 * Hardened (v0.5.2): labels can NEVER stack vertically. The track fills the
 * available width and scrolls horizontally if the segments overflow; each
 * segment keeps a minimum width and its label is single-line, no-wrap,
 * ellipsized. Whatever the labels, the worst case is a scrollable row —
 * never letter-by-letter vertical text.
 */
@Composable
fun MsSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .clip(CircleShape)
            .background(MsColors.SurfaceAlt)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 96.dp)
                    .clip(CircleShape)
                    .background(if (isSel) MsColors.Accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) MsColors.OnAccent else MsColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Copy button that morphs into a checkmark on tap: the copy icon pops into a
 * solid accent circle with a dark check + a brief "Copied!" label, then
 * settles back. Replaces the bare toast — the state change IS the feedback.
 */
@Composable
fun CopyMorphButton(
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MsColors.TextSecondary,
    showLabel: Boolean = true,
) {
    var copied by remember { mutableStateOf(false) }
    val pop by animateFloatAsState(
        targetValue = if (copied) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "copyPop",
    )
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1600)
            copied = false
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onCopy()
                    copied = true
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (copied) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .scale(pop.coerceIn(0.01f, 1.2f))
                    .background(MsColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Copied",
                    tint = MsColors.OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (showLabel) {
                Text(
                    text = "Copied!",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MsColors.Accent,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy",
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Settings action button with a scan lifecycle, in the Channels screen's
 * scan-animation language: idle → scanning (spinner + animated dots, ~2s,
 * not re-tappable) → done (springy accent check, then back to idle).
 * Used for dead-link and duplicate checks.
 * TODO(Phase 3): wire to the real scan jobs; keep the animation language.
 */
@Composable
fun ScanActionButton(
    idleText: String,
    scanningText: String,
    doneToast: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    // Animated ellipsis dots while scanning.
    var dots by remember { mutableIntStateOf(0) }
    LaunchedEffect(scanning) {
        if (scanning) {
            while (true) {
                kotlinx.coroutines.delay(400)
                dots = (dots + 1) % 4
            }
        } else {
            dots = 0
        }
    }

    // Springy pop for the done check, matching the Channels DoneCheck.
    val pop by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scanBtnPop",
    )
    LaunchedEffect(done) {
        if (done) {
            kotlinx.coroutines.delay(1800)
            done = false
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .border(1.dp, MsColors.TextSecondary.copy(alpha = 0.55f), CircleShape)
            .clickable(enabled = !scanning) {
                if (!scanning && !done) {
                    scanning = true
                    scope.launch {
                        kotlinx.coroutines.delay(2000)
                        scanning = false
                        done = true
                        Toast.makeText(context, doneToast, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            scanning -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MsColors.Accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = scanningText + ".".repeat(dots),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MsColors.Accent,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .scale(pop.coerceIn(0.01f, 1.2f))
                            .background(MsColors.Accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Done",
                            tint = MsColors.OnAccent,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MsColors.Accent,
                    )
                }
            }
            else -> {
                Text(
                    text = idleText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MsColors.Accent,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
