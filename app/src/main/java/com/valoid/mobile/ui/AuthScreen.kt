package com.valoid.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valoid.mobile.R

private enum class AuthMode { LOGIN, REGISTER, RESET }

@Composable
fun AuthScreen(vm: AuthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    fun switchTo(next: AuthMode) {
        mode = next
        localError = null
        vm.clearMessages()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ValoidLogo(96.dp)
        Text(
            "VALOID HUB",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            when (mode) {
                AuthMode.LOGIN -> "Masuk ke akunmu"
                AuthMode.REGISTER -> "Buat akun baru"
                AuthMode.RESET -> "Reset password"
            },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode != AuthMode.RESET) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        if (mode == AuthMode.REGISTER) {
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it },
                label = { Text("Ulangi password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        (localError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        state.info?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
        }

        Button(
            enabled = !state.loading,
            onClick = {
                localError = null
                when {
                    email.isBlank() -> localError = "Email tidak boleh kosong."
                    mode == AuthMode.RESET -> vm.sendReset(email)
                    password.length < 6 -> localError = "Password minimal 6 karakter."
                    mode == AuthMode.REGISTER && password != confirm -> localError = "Password tidak sama."
                    mode == AuthMode.REGISTER -> vm.register(email, password)
                    else -> vm.signIn(email, password)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(2.dp))
            } else {
                Text(
                    when (mode) {
                        AuthMode.LOGIN -> "Masuk"
                        AuthMode.REGISTER -> "Daftar"
                        AuthMode.RESET -> "Kirim tautan reset"
                    },
                )
            }
        }

        if (mode != AuthMode.RESET) {
            OutlinedButton(
                enabled = !state.loading,
                onClick = {
                    vm.signInWithGoogle(context, context.getString(R.string.default_web_client_id))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) { Text("Lanjutkan dengan Google") }
        }

        if (mode == AuthMode.LOGIN) {
            TextButton(onClick = { switchTo(AuthMode.RESET) }) { Text("Lupa password?") }
            TextButton(onClick = { switchTo(AuthMode.REGISTER) }) { Text("Belum punya akun? Daftar") }
        } else {
            TextButton(onClick = { switchTo(AuthMode.LOGIN) }) { Text("Kembali ke login") }
        }
    }
}
