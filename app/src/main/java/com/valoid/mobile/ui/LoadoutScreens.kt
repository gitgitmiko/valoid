package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.CollectionItem
import com.valoid.mobile.data.EquippedWeapon
import com.valoid.mobile.data.PlayerCollection

private enum class LoadoutPage { HOME, ARSENAL, CARDS, SPRAYS, TITLES, CHARMS }

/** Loadout dan koleksi pemain; navigasi antarhalamannya ditangani di sini. */
@Composable
fun LoadoutFlow(
    collection: PlayerCollection?,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onOpenSkin: (EquippedWeapon) -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(LoadoutPage.HOME) }
    BackHandler { if (page == LoadoutPage.HOME) onBack() else page = LoadoutPage.HOME }
    val back = { if (page == LoadoutPage.HOME) onBack() else page = LoadoutPage.HOME }

    if (collection == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PageBar("Loadout", null, back)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "Loadout belum dimuat.")
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Muat ulang") }
                    }
                }
            }
        }
        return
    }

    when (page) {
        LoadoutPage.HOME -> LoadoutHome(collection, back) { page = it }
        LoadoutPage.ARSENAL -> ArsenalScreen(collection.weapons, onOpenSkin, back)
        LoadoutPage.CARDS -> CollectionGrid("Kartu", "${collection.cards.size}", collection.cards, columns = 2, tall = true, back)
        LoadoutPage.SPRAYS -> CollectionGrid("Spray", "${collection.sprays.size}", collection.sprays, columns = 3, tall = false, back)
        LoadoutPage.CHARMS -> CollectionGrid("Charm", "${collection.charms.size} / ${collection.charmsTotal}", collection.charms, columns = 3, tall = false, back)
        LoadoutPage.TITLES -> TitleList(collection.titles, back)
    }
}

@Composable
private fun PageBar(title: String, trailing: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(end = 16.dp)) }
    }
}

@Composable
private fun LoadoutHome(c: PlayerCollection, onBack: () -> Unit, open: (LoadoutPage) -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Loadout", null, onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(452f / 128f * 0.62f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                    AsyncImage(c.cardWideArt, c.cardName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                        Text(c.cardName ?: "Kartu pemain", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        c.title?.let { Text(it, color = Color.White.copy(alpha = 0.8f)) }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, onClick = { open(LoadoutPage.SPRAYS) }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EKSPRESI TERPASANG", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                            Text("${c.equippedSprays.count { it != null }}/${c.equippedSprays.size.coerceAtLeast(4)}", style = MaterialTheme.typography.labelLarge)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (0 until 4).forEach { i ->
                                val spray = c.equippedSprays.getOrNull(i)
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (spray != null) AsyncImage(spray.imageUrl, spray.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
            item {
                val preview = c.weapons.firstOrNull { it.skinName != it.weaponName }
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, onClick = { open(LoadoutPage.ARSENAL) }, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.height(130.dp)) {
                        AsyncImage(
                            preview?.imageUrl,
                            null,
                            contentScale = ContentScale.Fit,
                            alpha = 0.35f,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxSize().padding(start = 120.dp, top = 12.dp, bottom = 12.dp),
                        )
                        Column(Modifier.align(Alignment.CenterStart).padding(16.dp)) {
                            Text("KOLEKSI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Arsenal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("${c.ownedSkins} skin dimiliki", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item { MenuEntry("Kartu", "${c.cards.size} kartu", c.cards.take(4).map { it.imageUrl }) { open(LoadoutPage.CARDS) } }
            item { MenuEntry("Title terpasang: ${c.title ?: "-"}", "${c.titles.size} title", emptyList()) { open(LoadoutPage.TITLES) } }
            item { MenuEntry("Charm", "${c.charms.size} dari ${c.charmsTotal}", c.charms.take(4).map { it.imageUrl }) { open(LoadoutPage.CHARMS) } }
        }
    }
}

@Composable
private fun MenuEntry(title: String, subtitle: String, previews: List<String?>, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            previews.forEach { url ->
                AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.padding(start = 4.dp).size(36.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.background))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ArsenalScreen(weapons: List<EquippedWeapon>, onOpenSkin: (EquippedWeapon) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Arsenal", "${weapons.size}", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(weapons, key = { it.weaponName }) { w ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    onClick = { onOpenSkin(w) },
                    enabled = w.skinLevelId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(120.dp)) {
                            AsyncImage(w.imageUrl, w.skinName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp))
                            w.buddyIcon?.let {
                                AsyncImage(
                                    it,
                                    w.buddyName,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.background),
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.weaponName.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(w.skinName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            }
                            if (w.skinLevelId != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        placeholder = { Text("Cari...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CollectionGrid(title: String, count: String, items: List<CollectionItem>, columns: Int, tall: Boolean, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, items) { items.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar(title, count, onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { SearchBox(query) { query = it } }
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("Tidak ada item.", Modifier.padding(16.dp)) }
            items(shown, key = { it.id }) { item ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (item.equipped) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                ) {
                    Column {
                        Box(Modifier.fillMaxWidth().aspectRatio(if (tall) 0.62f else 1f)) {
                            AsyncImage(
                                item.imageUrl,
                                item.name,
                                contentScale = if (tall) ContentScale.Crop else ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(if (tall) 0.dp else 10.dp),
                            )
                            if (item.equipped) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    "Terpasang",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color.White),
                                )
                            }
                        }
                        Text(
                            item.name,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleList(titles: List<CollectionItem>, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(query, titles) { titles.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Title", "${titles.size}", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SearchBox(query) { query = it } }
            items(shown, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (t.equipped) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (t.equipped) Icon(Icons.Filled.CheckCircle, "Terpasang", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
