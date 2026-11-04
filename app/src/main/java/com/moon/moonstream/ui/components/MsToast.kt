package com.moon.moonstream.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moon.moonstream.ui.theme.MsColors
import kotlinx.coroutines.delay

/**
 * In-app toast replacing android.widget.Toast. Show via [MsToastHostState].
 */
class MsToastHostState {
    var message by mutableStateOf<String?>(null)
        private set
    private var generation = 0

    fun show(msg: String) {
        generation++
        message = msg
    }

    internal fun dismissIf(gen: Int) {
        if (gen == generation) message = null
    }

    internal fun currentGen(): Int = generation
}

@Composable
fun MsToastHost(
    state: MsToastHostState,
    modifier: Modifier = Modifier,
    durationMs: Long = 2200,
) {
    val msg = state.message
    val gen = state.currentGen()
    LaunchedEffect(msg, gen) {
        if (msg != null) {
            delay(durationMs)
            state.dismissIf(gen)
        }
    }
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = msg != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            Text(
                text = msg.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 15.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    // Clear the floating nav pill (sits ~90dp tall at the bottom).
                    .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C1C1E).copy(alpha = 0.92f))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            )
        }
    }
}

/** App-wide toast state. Screens call MsToast.show("...") instead of Toast.makeText. */
object MsToast {
    val host = MsToastHostState()
    fun show(msg: String) = host.show(msg)
}
