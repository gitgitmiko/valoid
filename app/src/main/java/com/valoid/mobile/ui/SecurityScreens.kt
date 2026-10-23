package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Menutupi seluruh aplikasi sampai pengguna lolos autentikasi. */
@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit, onLeave: () -> Unit) {
    BackHandler(onBack = onLeave)
    LaunchedEffect(Unit) { onUnlock() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Menyerap sentuhan agar aplikasi di bawahnya tidak bisa disentuh.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Text("Valoid terkunci", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) { Text("Buka kunci") }
    }
}

@Composable
fun SettingsScreen(
    email: String?,
    riotConnected: Boolean,
    lockEnabled: Boolean,
    lockAvailable: Boolean,
    message: String?,
    onToggleLock: (Boolean) -> Unit,
    onDisconnectRiot: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Pengaturan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("Akun Valoid") {
                Text(email ?: "-")
                OutlinedButton(onClick = onSignOut) { Text("Keluar dari Valoid") }
            }

            SettingsCard("Akun Riot") {
                Text(
                    if (riotConnected) "Terhubung. Cookie sesi Riot tersimpan di HP ini untuk login otomatis dan notifikasi."
                    else "Tidak terhubung.",
                )
                if (riotConnected) {
                    OutlinedButton(onClick = { confirmDisconnect = true }) { Text("Putuskan akun Riot") }
                }
            }

            SettingsCard("Kunci aplikasi") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Minta sidik jari, wajah, atau kunci layar saat membuka Valoid",
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(checked = lockEnabled, onCheckedChange = onToggleLock, enabled = lockAvailable || lockEnabled)
                }
                if (!lockAvailable && !lockEnabled) {
                    Text(
                        "Atur kunci layar (PIN/pola/sidik jari) di pengaturan HP terlebih dahulu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            SettingsCard("Privasi") {
                Text(
                    "Username dan password Riot diketik langsung di halaman resmi Riot dan tidak dibaca Valoid. " +
                        "Token Riot hanya ada di memori. Firestore hanya menyimpan riwayat toko dan favorit.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Putuskan akun Riot?") },
            text = {
                Text("Cookie login Riot dihapus dari HP ini. Login otomatis dan notifikasi favorit berhenti sampai kamu login Riot lagi.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    onDisconnectRiot()
                }) { Text("Putuskan") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}
