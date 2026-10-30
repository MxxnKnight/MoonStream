package com.moon.moonstream.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moon.moonstream.R
import com.moon.moonstream.tdlib.AuthState
import com.moon.moonstream.tdlib.TelegramManager
import com.moon.moonstream.ui.components.LimePillButton
import com.moon.moonstream.ui.components.MsTextField
import com.moon.moonstream.ui.theme.MsColors

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val context = LocalContext.current

    // Retrieve the singleton manager
    val manager = remember {
        TelegramManager.getInstance(context)
    }

    val authState by manager.authState.collectAsState()

    var step by remember { mutableStateOf(0) } // 0=api, 1=phone, 2=code, 3=password
    var apiIdText by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Watch TDLib auth state and move to next step automatically
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.WaitingPhoneNumber -> {
                isLoading = false
                step = 1
            }
            is AuthState.WaitingCode -> {
                isLoading = false
                step = 2
            }
            is AuthState.WaitingPassword -> {
                isLoading = false
                step = 3
            }
            is AuthState.Ready -> {
    isLoading = false

    // Create the real backend and install it
    val realBackend = com.moon.moonstream.backend.TdlibMoonStreamBackend(
        context = context.applicationContext,
        telegramManager = manager
    )
    com.moon.moonstream.backend.BackendProvider.install(realBackend)

    // Also keep the old UI state happy
    com.moon.moonstream.ui.state.AppUiState.setLoggedIn(context)

    onLoggedIn()
}
            is AuthState.LoggedOut -> {
                isLoading = false
            }
            else -> {}
        }
    }

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
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp)),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "MoonStream",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MsColors.TextPrimary,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = when (step) {
                0 -> "Enter your Telegram API credentials"
                1 -> "Enter your phone number"
                2 -> "Enter the code Telegram sent you"
                else -> "Enter your 2FA password"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        // ========== STEP 0: API ID + HASH ==========
        if (step == 0) {
            MsTextField(
                value = apiIdText,
                onValueChange = {
                    apiIdText = it.filter { c -> c.isDigit() }
                    errorMessage = null
                },
                hint = "API ID (numbers only)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            MsTextField(
                value = apiHash,
                onValueChange = {
                    apiHash = it.trim()
                    errorMessage = null
                },
                hint = "API Hash",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LimePillButton(
                text = if (isLoading) "Starting..." else "Continue",
                onClick = {
                    val id = apiIdText.toIntOrNull()
                    if (id == null || id == 0 || apiHash.length < 10) {
                        errorMessage = "Please enter valid API ID and Hash"
                        return@LimePillButton
                    }
                    isLoading = true
                    errorMessage = null
                    manager.setApiCredentials(id, apiHash)
                    manager.start()
                },
                enabled = !isLoading && apiIdText.isNotBlank() && apiHash.length > 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ========== STEP 1: PHONE ==========
        if (step == 1) {
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
                text = if (isLoading) "Sending..." else "Send Code",
                onClick = {
                    if (phone.filter { it.isDigit() }.length < 7) {
                        errorMessage = "Enter a valid phone number"
                        return@LimePillButton
                    }
                    isLoading = true
                    errorMessage = null
                    manager.setPhoneNumber(phone)
                },
                enabled = !isLoading && phone.filter { it.isDigit() }.length >= 7,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ========== STEP 2: CODE ==========
        if (step == 2) {
            Text(
                text = "Code sent to $phone",
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
                text = if (isLoading) "Checking..." else "Verify Code",
                onClick = {
                    if (code.length < 4) {
                        errorMessage = "Enter the code"
                        return@LimePillButton
                    }
                    isLoading = true
                    errorMessage = null
                    manager.checkCode(code)
                },
                enabled = !isLoading && code.length >= 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ========== STEP 3: 2FA PASSWORD ==========
        if (step == 3) {
            val hint = (authState as? AuthState.WaitingPassword)?.hint ?: ""

            Text(
                text = if (hint.isNotBlank()) "Hint: $hint" else "Two-Step Verification is enabled",
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
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            LimePillButton(
                text = if (isLoading) "Checking..." else "Confirm Password",
                onClick = {
                    if (password.isBlank()) {
                        errorMessage = "Enter your password"
                        return@LimePillButton
                    }
                    isLoading = true
                    errorMessage = null
                    manager.checkPassword(password)
                },
                enabled = !isLoading && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Loading indicator
        if (isLoading) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = MsColors.Accent)
        }

        // Error message
        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Your API credentials are only stored on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MsColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
