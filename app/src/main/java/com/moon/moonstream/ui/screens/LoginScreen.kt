package com.moon.moonstream.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moon.moonstream.R
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.state.AppUiState
import com.moon.moonstream.ui.theme.MsColors

/**
 * Fake Telegram login shown after the splash on first launch.
 * Any phone number + any code lets him in — no validation at all.
 * TODO(Phase 2): real TDLib auth — API id/hash, phone → code → 2FA → QR,
 * secure session persistence instead of this boolean.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) } // 0 = phone, 1 = code
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "MoonStream",
            modifier = Modifier
                .size(110.dp)
                .clip(RoundedCornerShape(26.dp)),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "MoonStream",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (step == 0) "Log in with your Telegram account" else "Enter the code",
            style = MaterialTheme.typography.bodyMedium,
            color = MsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        if (step == 0) {
            MsTextField(
                value = phone,
                onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(16) },
                hint = "Phone number",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LimePillButton(
                text = "Continue",
                onClick = { step = 1 },
                enabled = phone.filter { it.isDigit() }.length >= 7,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Fake login — any number works (TODO: real Telegram auth)",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        } else {
            Text(
                "Code sent to +$phone (fake)",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            MsTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                hint = "12345",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            LimePillButton(
                text = "Verify",
                onClick = {
                    AppUiState.setLoggedIn(context)
                    AppUiState.addLog("AUTH", "Telegram login accepted (fake)")
                    onLoggedIn()
                },
                enabled = code.length >= 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Fake login — any code works (TODO: real Telegram auth)",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )
        }
    }
}
