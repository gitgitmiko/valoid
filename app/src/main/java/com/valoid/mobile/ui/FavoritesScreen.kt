package com.valoid.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.FavoriteSkin
import com.valoid.mobile.data.SkinSummary

/**
 * Daftar skin favorit dan pencarian katalog untuk menambah favorit.
 * [inStoreIds] berisi ID skin (huruf kecil) yang sedang ada di toko harian/Night Market.
 */
@Composable
fun FavoritesScreen(
    favorites: List<FavoriteSkin>,
    catalog: List<SkinSummary>,
    inStoreIds: Set<String>,
    onToggle: (id: String, name: String, iconUrl: String?) -> Unit,
    onOpen: (id: String, name: String, iconUrl: String?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }
    val results = remember(query, catalog) {
        if (query.isBlank()) emptyList() else catalog.filter { it.name.contains(query.trim(), ignoreCase = true) }.take(60)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Cari skin untuk difavoritkan") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotBlank()) {
            if (catalog.isEmpty()) item { Text("Memuat katalog skin...") }
            items(results, key = { "r" + it.id }) { skin ->
                SkinRow(skin.id, skin.name, skin.iconUrl, skin.id in favoriteIds, skin.id in inStoreIds, onToggle, onOpen)
            }
            if (catalog.isNotEmpty() && results.isEmpty()) item { Text("Tidak ada skin yang cocok.") }
        } else {
            item {
                Text(
                    "Kamu akan mendapat notifikasi saat skin favorit muncul di toko harian atau Night Market.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (favorites.isEmpty()) {
                item { Text("Belum ada favorit. Cari skin di atas atau tekan ❤ di toko.", Modifier.padding(vertical = 24.dp)) }
            }
            items(favorites.sortedByDescending { it.id in inStoreIds }, key = { "f" + it.id }) { fav ->
                SkinRow(fav.id, fav.name, fav.iconUrl, true, fav.id in inStoreIds, onToggle, onOpen)
            }
        }
    }
}

@Composable
private fun SkinRow(
    id: String,
    name: String,
    iconUrl: String?,
    favorite: Boolean,
    inStore: Boolean,
    onToggle: (String, String, String?) -> Unit,
    onOpen: (String, String, String?) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (inStore) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
            )
            .clickable { onOpen(id, name, iconUrl) }
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(iconUrl, name, contentScale = ContentScale.Fit, modifier = Modifier.size(width = 88.dp, height = 48.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 2)
            if (inStore) Text("Ada di tokomu sekarang!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        FavoriteButton(favorite, onClick = { onToggle(id, name, iconUrl) })
    }
}
