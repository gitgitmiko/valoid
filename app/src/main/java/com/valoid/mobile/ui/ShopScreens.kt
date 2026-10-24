package com.valoid.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.valoid.mobile.data.HistoryDay
import com.valoid.mobile.data.RiotRegion
import com.valoid.mobile.data.SkinOffer

/** Kartu ajakan menghubungkan akun Riot; region dideteksi otomatis setelah login. */
@Composable
fun ConnectRiotPrompt(
    state: ShopUiState,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.AccountCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Text(
                    "Hubungkan Riot Games",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Text(
                    "Tautkan akunmu untuk melihat toko harian, Night Market, dan statistikmu.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
                Button(
                    onClick = onConnect,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(52.dp),
                ) {
                    Text(if (state.loading) "MENGHUBUNGKAN..." else "HUBUNGKAN SEKARANG", fontWeight = FontWeight.SemiBold)
                }
                androidx.compose.material3.TextButton(onClick = onRetry, enabled = !state.loading, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Coba lagi")
                }
            }
        }
    }
}

@Composable
fun OfferListScreen(
    title: String,
    emptyText: String,
    offers: List<SkinOffer>,
    endsAtMillis: Long,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOfferClick: (SkinOffer) -> Unit,
    header: @Composable () -> Unit = {},
    footer: @Composable () -> Unit = {},
    favoriteIds: Set<String> = emptySet(),
    onToggleFavorite: (SkinOffer) -> Unit = {},
    locked: (@Composable () -> Unit)? = null,
) {
    val secondsLeft = rememberSecondsLeft(endsAtMillis)
    // Muat ulang otomatis begitu toko berganti.
    LaunchedEffect(endsAtMillis, offers.isNotEmpty()) {
        if (offers.isEmpty()) return@LaunchedEffect
        delay((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0) + 3000)
        onRefresh()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { header() }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (offers.isNotEmpty()) Text("Reset dalam ${formatCountdown(secondsLeft)}")
                }
                OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Muat ulang") }
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        when {
            locked != null -> item { locked() }
            loading && offers.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            offers.isEmpty() -> item { Text(emptyText, Modifier.padding(vertical = 48.dp)) }
            else -> items(offers, key = { it.id }) {
                SkinCard(
                    it,
                    favorite = it.id.lowercase() in favoriteIds,
                    onClick = { onOfferClick(it) },
                    onToggleFavorite = { onToggleFavorite(it) },
                )
            }
        }
        item { footer() }
    }
}

@Composable
private fun SkinCard(
    offer: SkinOffer,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = offer.iconUrl,
                contentDescription = offer.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp, 64.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(offer.name, fontWeight = FontWeight.Bold)
                if (offer.discountedCost != null) {
                    Text("${formatAmount(offer.discountedCost)} VP  (-${offer.discountPercent}%)", color = MaterialTheme.colorScheme.primary)
                    Text("${formatAmount(offer.cost)} VP", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("${formatAmount(offer.cost)} VP")
                }
            }
            FavoriteButton(favorite, onClick = onToggleFavorite)
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

/** Kartu pengganti toko harian bagi pengguna gratis sebelum menonton iklan. */
@Composable
fun StoreLockedCard(
    watching: Boolean,
    error: String?,
    allowSkip: Boolean,
    onWatch: () -> Unit,
    onSkip: () -> Unit,
    onPro: () -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Lock,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text("Toko harian terkunci", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Tonton iklan singkat untuk membuka toko harianmu selama 1 jam.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            Button(onClick = onWatch, enabled = !watching, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp)) {
                Text(if (watching) "Memuat iklan..." else "Tonton iklan", fontWeight = FontWeight.SemiBold)
            }
            if (allowSkip) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Iklan tidak tersedia, buka toko") }
            }
            androidx.compose.material3.TextButton(onClick = onPro, modifier = Modifier.padding(top = 4.dp)) {
                Text("Tanpa iklan dengan Valoid Pro")
            }
        }
    }
}

/** Sisa waktu sesi toko harian untuk pengguna gratis. */
@Composable
fun StoreSessionBanner(endsAtMillis: Long, onPro: () -> Unit) {
    val left = rememberSecondsLeft(endsAtMillis)
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Akses toko berakhir dalam %02d:%02d".format(left / 60, left % 60),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.TextButton(onClick = onPro) { Text("Tanpa iklan") }
        }
    }
}
