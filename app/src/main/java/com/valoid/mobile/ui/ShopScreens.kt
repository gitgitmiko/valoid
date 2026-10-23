package com.valoid.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.HistoryDay
import com.valoid.mobile.data.RiotRegion
import com.valoid.mobile.data.SkinOffer

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    return if (h >= 24) "${h / 24}h ${h % 24}j" else "${h}j ${m}m"
}

@Composable
fun ConnectRiotPrompt(
    state: ShopUiState,
    onRegion: (RiotRegion) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Hubungkan akun Riot", style = MaterialTheme.typography.titleLarge)
        Text(
            "Login dilakukan langsung di halaman resmi Riot. Valoid tidak menyimpan password atau token kamu.",
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RiotRegion.entries.forEach { r ->
                FilterChip(selected = r == state.region, onClick = { onRegion(r) }, label = { Text(r.name) })
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Button(onClick = onConnect, enabled = !state.loading, modifier = Modifier.padding(top = 16.dp)) {
            Text(if (state.loading) "Menghubungkan..." else "Login dengan Riot")
        }
    }
}

@Composable
fun OfferListScreen(
    title: String,
    emptyText: String,
    offers: List<SkinOffer>,
    remainingSeconds: Long,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (offers.isNotEmpty()) Text("Reset dalam ${formatDuration(remainingSeconds)}")
            }
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Muat ulang") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.fillMaxSize()) {
            when {
                loading && offers.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                offers.isEmpty() -> Text(emptyText, Modifier.align(Alignment.Center))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(offers, key = { it.id }) { SkinCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SkinCard(offer: SkinOffer) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = offer.iconUrl,
                contentDescription = offer.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp, 64.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(offer.name, fontWeight = FontWeight.Bold)
                if (offer.discountedCost != null) {
                    Text("${offer.discountedCost} VP  (-${offer.discountPercent}%)", color = MaterialTheme.colorScheme.primary)
                    Text("${offer.cost} VP", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${offer.cost} VP")
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(days: List<HistoryDay>, loading: Boolean, error: String?, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Riwayat toko", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Muat ulang") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Box(Modifier.fillMaxSize()) {
            when {
                loading && days.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                days.isEmpty() -> Text(
                    "Belum ada riwayat. Buka tab Toko setelah login Riot, hasilnya tersimpan otomatis.",
                    Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(days, key = { it.date }) { day ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(day.date, fontWeight = FontWeight.Bold)
                                day.offers.forEach { o ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(o.iconUrl, o.name, Modifier.size(64.dp, 40.dp), contentScale = ContentScale.Fit)
                                        Text(o.name, Modifier.padding(start = 8.dp).weight(1f))
                                        Text("${o.cost} VP")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
