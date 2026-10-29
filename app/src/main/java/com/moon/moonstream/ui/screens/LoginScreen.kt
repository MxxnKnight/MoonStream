package com.moon.moonstream.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moon.moonstream.R
import com.moon.moonstream.backend.BackendProvider
import com.moon.moonstream.backend.FakeMoonStreamBackend
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.theme.MsColors

/**
 * Login screen – currently still uses the FAKE login.
 * Steps prepared for real Telegram later:
 * 0 = Phone number
 * 1 = OTP / Code
 * 2 = 2FA Password (if needed)
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var step by remember { mutableStateOf(0) } // 0=phone, 1=code, 2=password
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MsColors.Bg)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "MoonStream",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(28.dp)),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "MoonStream",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when (step) {
                0 -> "Log in with your Telegram account"
                1 -> "Enter the code Telegram sent you"
                else -> "Enter your 2FA password"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        // ---------- STEP 0: Phone Number ----------
        if (step == 0) {
            MsTextField(
                value = phone,
                onValueChange = {
                    phone = it.filter { c -> c.isDigit() || c == '+' }.take(16)
                    errorMessage = null
                },
                hint = "Phone number (e.g. +919876543210)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LimePillButton(
                text = "Continue",
                onClick = {
                    if (phone.filter { it.isDigit() }.length >= 7) {
                        step = 1
                    } else {
                        errorMessage = "Please enter a valid phone number"
                    }
                },
                enabled = phone.filter { it.isDigit() }.length >= 7,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---------- STEP 1: OTP / Code ----------
        if (step == 1) {
            Text(
                text = "Code sent to $phone (fake for now)",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
            )

            Spacer(Modifier.height(12.dp))

            MsTextField(
                value = code,
                onValueChange = {
                    code = it.filter { c -> c.isDigit() }.take(6)
                    errorMessage = null
                },
                hint = "12345",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LimePillButton(
                text = "Verify Code",
                onClick = {
                    // For now we still use the fake login.
                    // Later this will call the real TDLib checkCode()
                    (BackendProvider.backend as? FakeMoonStreamBackend)?.fakeLogin()
                    onLoggedIn()
                },
                enabled = code.length >= 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "← Back",
                color = MsColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(8.dp)
                    .clickable { step = 0 }
            )
        }

        // ---------- STEP 2: 2FA Password (ready for later) ----------
        if (step == 2) {
            Text(
                text = "Your account has Two-Step Verification enabled",
                style = MaterialTheme.typography.bodySmall,
                color = MsColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            MsTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                },
                hint = "2FA Password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                // visualTransformation = PasswordVisualTransformation(),  // uncomment if you want dots
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LimePillButton(
                text = "Confirm Password",
                onClick = {
                    // Later: call real TDLib checkPassword()
                    (BackendProvider.backend as? FakeMoonStreamBackend)?.fakeLogin()
                    onLoggedIn()
                },
                enabled = password.length >= 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Currently using FAKE login (any code works).\nReal Telegram login will be connected later.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
