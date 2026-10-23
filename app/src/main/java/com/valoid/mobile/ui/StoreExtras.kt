package com.valoid.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.AccessoryOffer
import com.valoid.mobile.data.BundleOffer
import com.valoid.mobile.data.Wallet
import java.text.NumberFormat
import java.util.Locale

private const val CURRENCY_ICON = "https://media.valorant-api.com/currencies/%s/displayicon.png"
private val ICON_VP = CURRENCY_ICON.format("85ad13f7-3d1b-5128-9eb2-7cd8ee0b5741")
private val ICON_KINGDOM = CURRENCY_ICON.format("85ca954a-41f2-ce94-9b45-8ca3dd39a00d")
private val ICON_RADIANITE = CURRENCY_ICON.format("e59aa87c-4cbf-517a-5983-6e81511be9b7")

private val numberFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))

/** 6802 -> "6.802" */
fun formatAmount(value: Int): String = numberFormat.format(value)

@Composable
fun VpIcon(size: Int = 16) {
    AsyncImage(ICON_VP, "VP", Modifier.size(size.dp))
}

@Composable
fun WalletBar(wallet: Wallet) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Balance(ICON_VP, "Valorant Points", wallet.valorantPoints, Modifier.weight(1f))
            Balance(ICON_KINGDOM, "Kingdom Credits", wallet.kingdomCredits, Modifier.weight(1f))
            Balance(ICON_RADIANITE, "Radianite", wallet.radianite, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Balance(icon: String, label: String, amount: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(icon, label, Modifier.size(20.dp))
        Text(formatAmount(amount), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
fun BundleRow(bundles: List<BundleOffer>, endsAtMillis: (BundleOffer) -> Long, onClick: (BundleOffer) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(end = 16.dp)) {
        items(bundles, key = { it.id }) { bundle ->
            BundleCard(bundle, endsAtMillis(bundle), onClick = { onClick(bundle) }, modifier = Modifier.width(300.dp))
        }
    }
}

@Composable
fun BundleCard(bundle: BundleOffer, endsAtMillis: Long, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val secondsLeft = rememberSecondsLeft(endsAtMillis)
    val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(bundle.imageUrl, bundle.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
                ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(bundle.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏱ ${formatCountdown(secondsLeft)}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text("  •  ", color = Color.White)
                    VpIcon(14)
                    Text(" ${formatAmount(bundle.price)}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (bundle.discountPercent > 0) {
                        Text(
                            " ${formatAmount(bundle.basePrice)}",
                            color = Color.White.copy(alpha = 0.6f),
                            textDecoration = TextDecoration.LineThrough,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = modifier) { content() }
    } else {
        Card(shape = RoundedCornerShape(16.dp), modifier = modifier) { content() }
    }
}

private const val ICON_KINGDOM_CREDITS = "https://media.valorant-api.com/currencies/85ca954a-41f2-ce94-9b45-8ca3dd39a00d/displayicon.png"

/** Grid dua kolom; bukan Lazy agar bisa diletakkan di dalam LazyColumn toko. */
@Composable
fun AccessoryGrid(accessories: List<AccessoryOffer>, endsAtMillis: Long) {
    val secondsLeft = rememberSecondsLeft(endsAtMillis)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Aksesori", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("⏱ ${formatCountdown(secondsLeft)}", style = MaterialTheme.typography.bodyMedium)
        }
        accessories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { AccessoryCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AccessoryCard(item: AccessoryOffer, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f), contentAlignment = Alignment.Center) {
                if (item.iconUrl != null) {
                    AsyncImage(item.iconUrl, item.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                } else {
                    // Title tidak punya gambar; tampilkan teksnya sebagai label.
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                        Text(
                            item.name.uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            Text(
                if (item.amount > 1) "${item.type.label} ×${item.amount}" else item.type.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                AsyncImage(ICON_KINGDOM_CREDITS, "Kingdom Credits", Modifier.size(16.dp))
                Text(" ${formatAmount(item.cost)}", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favorite) "Hapus dari favorit" else "Tambah ke favorit",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
