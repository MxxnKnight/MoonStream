package com.moon.webmock.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moon.webmock.theme.MsColors

// ---------------------------------------------------------------------------
// MsControls subset, ported verbatim from the app's MsControls.kt so mockups
// render the exact components the app uses.
// ---------------------------------------------------------------------------

/** iOS PRIMARY: Accent fill, white text, 14dp corners. */
@Composable
fun LimePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 17,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MsColors.Accent,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF38383A),
            disabledContentColor = MsColors.TextSecondary,
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        PillLabel(text = text, fontSize = fontSize, icon = icon, bold = true)
    }
}

/** iOS SECONDARY: #1C1C1E fill, Accent text, 14dp corners. */
@Composable
fun MsOutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 17,
    icon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1C1C1E),
            contentColor = MsColors.Accent,
            disabledContainerColor = Color(0xFF1C1C1E),
            disabledContentColor = MsColors.TextSecondary,
        ),
        shape = RoundedCornerShape(14.dp),
        contentPadding = contentPadding,
    ) {
        PillLabel(text = text, fontSize = fontSize, icon = icon, bold = false)
    }
}

/** Label inside a pill: optional 16dp glyph + the text, kept on one line. */
@Composable
private fun PillLabel(
    text: String,
    fontSize: Int,
    icon: ImageVector?,
    bold: Boolean,
) {
    if (icon == null) {
        Text(
            text = text,
            style = if (bold) MaterialTheme.typography.bodyLarge
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text,
                style = if (bold) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = fontSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** iOS-style switch: 51x31, green when on. */
@Composable
fun MsToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF30D158) else Color(0xFF39393B),
        label = "iosTrack",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        label = "iosKnob",
    )
    Box(
        modifier = modifier
            .size(51.dp, 31.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knobOffset)
                .padding(start = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
