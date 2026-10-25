package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.valoid.mobile.data.Announcement
import com.valoid.mobile.data.AnnouncementType
import com.valoid.mobile.data.PlayerProfile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tab Hub: info developer, room chat semua user, dan room chat sesuai rank. Profil dibuka dari avatar. */
@Composable
fun HubScreen(
    profile: PlayerProfile?,
    announcements: List<Announcement>,
    onOpenProfile: () -> Unit,
    onOpenAnnouncements: () -> Unit,
    onlineCount: Int?,
    onOpenGlobal: () -> Unit,
    rankRoomTitle: String?,
    rankRoomOnline: Int?,
    onOpenRankRoom: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Valoid Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Cari party duo, trio, atau full 5",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                ProfileAvatar(profile, onOpenProfile)
            }
        }
        item { AnnouncementCard(announcements.firstOrNull(), onOpenAnnouncements) }
        item {
            RoomCard("Room semua user", "Ngobrol dan cari full party 5 orang.", null, online = onlineCount, onClick = onOpenGlobal)
        }
        item {
            val rank = profile?.currentRank
            RoomCard(
                title = rankRoomTitle?.let { "Room $it" } ?: "Room rank",
                subtitle = rank?.let { "Cari duo atau trio se-rank ${it.tierName.lowercase().replaceFirstChar { c -> c.uppercase() }}." }
                    ?: "Cari duo atau trio dengan rank yang sejajar.",
                iconUrl = rank?.iconUrl,
                online = rankRoomOnline,
                onClick = onOpenRankRoom,
            )
        }
    }
}

@Composable
private fun ProfileAvatar(profile: PlayerProfile?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val art = profile?.cardSmallArt
        if (art != null) {
            AsyncImage(art, "Profil", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Filled.Person, "Profil", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnnouncementCard(latest: Announcement?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "INFO DEVELOPER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                latest?.let { TypeChip(it.type) }
            }
            if (latest == null) {
                Text("Belum ada info terbaru.")
            } else {
                Text(latest.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(latest.body, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(latest.version?.let { "v$it" }, formatDay(latest.publishedAtMillis)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                    )
                    Text("Lihat semua ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun RoomCard(title: String, subtitle: String, iconUrl: String?, online: Int? = null, onClick: (() -> Unit)? = null) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (iconUrl != null) {
                AsyncImage(iconUrl, null, modifier = Modifier.size(44.dp).padding(end = 12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Box(Modifier.padding(top = 6.dp)) {
                    when {
                        onClick == null -> Text("Segera hadir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        online != null -> OnlineLabel(online)
                    }
                }
            }
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun TypeChip(type: AnnouncementType) {
    val color = when (type) {
        AnnouncementType.UPDATE -> MaterialTheme.colorScheme.primary
        AnnouncementType.BUGFIX -> Color(0xFF4ADE80)
        AnnouncementType.INFO -> MaterialTheme.colorScheme.secondary
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.18f)) {
        Text(type.label, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

/** Semua info developer, terbaru di atas, dengan isi lengkap. */
@Composable
fun AnnouncementsScreen(announcements: List<Announcement>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            Text("Info developer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (announcements.isEmpty()) item { Text("Belum ada info terbaru.") }
            items(announcements, key = { it.id }) { a ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOfNotNull(a.version?.let { "v$it" }, formatDay(a.publishedAtMillis)).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.weight(1f),
                            )
                            TypeChip(a.type)
                        }
                        Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(a.body)
                    }
                }
            }
        }
    }
}

private val DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("id", "ID"))

private fun formatDay(millis: Long): String? =
    millis.takeIf { it > 0 }?.let { DAY.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
