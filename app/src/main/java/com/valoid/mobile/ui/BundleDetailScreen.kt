package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.BundleItem
import com.valoid.mobile.data.BundleOffer
import com.valoid.mobile.data.ItemType

@Composable
fun BundleDetailScreen(
    bundle: BundleOffer,
    endsAtMillis: Long,
    onBack: () -> Unit,
    onSkinClick: (BundleItem) -> Unit,
) {
    BackHandler(onBack = onBack)
    val secondsLeft = rememberSecondsLeft(endsAtMillis)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text(bundle.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                BundleCard(bundle, endsAtMillis, onClick = null, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        VpIcon(22)
                        Text(
                            " ${formatAmount(bundle.price)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (bundle.discountPercent > 0) {
                            Text(
                                "  ${formatAmount(bundle.basePrice)}",
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Text("  -${bundle.discountPercent}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Keluar dari toko dalam ${formatCountdown(secondsLeft)}", modifier = Modifier.padding(top = 4.dp))
                    Text(
                        "ISI BUNDLE (${bundle.items.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(bundle.items, key = { it.type.name + it.id }) { item ->
                BundleItemRow(item, onClick = if (item.type == ItemType.SKIN) ({ onSkinClick(item) }) else null)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BundleItemRow(item: BundleItem, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(item.iconUrl, item.name, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 88.dp, height = 52.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text(
                if (item.amount > 1) "${item.type.label} ×${item.amount}" else item.type.label,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.price == 0) {
                Text("Gratis", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VpIcon(14)
                    Text(" ${formatAmount(item.price)}", fontWeight = FontWeight.SemiBold)
                }
            }
            if (item.basePrice > item.price) {
                Text(
                    formatAmount(item.basePrice),
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = TextDecoration.LineThrough,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}
