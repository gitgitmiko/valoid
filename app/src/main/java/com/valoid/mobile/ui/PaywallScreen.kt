package com.valoid.mobile.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.valoid.mobile.billing.ProManager

private class Perk(val icon: ImageVector, val title: String, val body: String)

private val PERKS = listOf(
    Perk(Icons.Filled.ShoppingCart, "Toko harian tanpa iklan", "Toko harian langsung terbuka tanpa perlu menonton iklan."),
    Perk(Icons.Filled.Favorite, "Skin favorit", "Simpan skin incaranmu dan lihat kapan muncul di tokomu."),
    Perk(Icons.Filled.Notifications, "Notifikasi favorit", "Dapat pemberitahuan di hari skin favoritmu masuk toko."),
    Perk(Icons.Filled.Person, "Banyak akun Riot", "Tautkan beberapa akun Riot dan berpindah dengan sekali ketuk."),
)

@Composable
fun PaywallScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val isPro by ProManager.isPro.collectAsStateWithLifecycle()
    val price by ProManager.price.collectAsStateWithLifecycle()
    val message by ProManager.message.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        ProManager.clearMessage()
        ProManager.refresh()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Tutup") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ValoidLogo(104.dp)
            Text("VALOID PRO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text(
                "Toko tanpa iklan, skin favorit dengan notifikasi, dan banyak akun Riot.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PERKS.forEach { perk ->
                        Row {
                            Icon(perk.icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                            Column(Modifier.padding(start = 14.dp)) {
                                Text(perk.title, fontWeight = FontWeight.SemiBold)
                                Text(perk.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(24.dp)) {
            if (isPro) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Valoid Pro aktif", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Bulanan", fontWeight = FontWeight.SemiBold)
                            Text("Ditagih setiap bulan, batalkan kapan saja.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                        Text("${price ?: ProManager.FALLBACK_PRICE}/bln", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                Button(
                    onClick = { (context as? Activity)?.let { ProManager.purchase(it) } },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(56.dp),
                ) {
                    Text("Berlangganan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { ProManager.refresh() }) { Text("Pulihkan pembelian") }
                TextButton(onClick = {
                    val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${ProManager.PRODUCT_ID}&package=${context.packageName}")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) { Text("Kelola langganan") }
            }
        }
    }
}

/** Label kecil "PRO" untuk fitur berlangganan. */
@Composable
fun ProBadge() {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primary) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text("PRO", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
