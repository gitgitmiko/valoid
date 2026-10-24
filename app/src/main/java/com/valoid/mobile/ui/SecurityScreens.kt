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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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

/** Isi tab Pengaturan: akun Valoid, akun Riot, kunci aplikasi, FAQ, dan Tentang. */
@Composable
fun SettingsTab(
    email: String?,
    riotConnected: Boolean,
    riotConnecting: Boolean,
    lockEnabled: Boolean,
    lockAvailable: Boolean,
    message: String?,
    onToggleLock: (Boolean) -> Unit,
    onConnectRiot: () -> Unit,
    onDisconnectRiot: () -> Unit,
    onSignOut: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenAbout: () -> Unit,
    isPro: Boolean = false,
    onOpenPaywall: () -> Unit = {},
    debugPro: Boolean? = null,
    onDebugPro: (Boolean) -> Unit = {},
) {
    var confirmDisconnect by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Pengaturan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("Valoid Pro") {
                Text(if (isPro) "Aktif. Terima kasih sudah mendukung Valoid!" else "Toko tanpa iklan, skin favorit dan notifikasinya, serta banyak akun Riot.")
                if (isPro) {
                    OutlinedButton(onClick = onOpenPaywall) { Text("Detail langganan") }
                } else {
                    Button(onClick = onOpenPaywall) { Text("Lihat Valoid Pro") }
                }
                debugPro?.let { on ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mode Pro (uji, hanya build debug)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Switch(checked = on, onCheckedChange = onDebugPro)
                    }
                }
            }

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
                } else {
                    Button(onClick = onConnectRiot, enabled = !riotConnecting) {
                        Text(if (riotConnecting) "Menghubungkan..." else "Hubungkan akun Riot")
                    }
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

            SettingsLink("Pertanyaan umum (FAQ)", onOpenFaq)
            SettingsLink("Tentang Valoid", onOpenAbout)
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

@Composable
private fun SettingsLink(title: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
