package com.valoid.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val FAQ = listOf(
    "Login dengan Google dan email" to
        "Login Valoid memakai Firebase Authentication dari Google. Password akun Valoid tidak disimpan di aplikasi; " +
        "semua proses login dan reset password ditangani server Google.",
    "Login Riot Games: apakah aman?" to
        "Login Riot dilakukan di halaman resmi auth.riotgames.com. Valoid tidak membaca username atau password Riot kamu. " +
        "Setelah login, yang disimpan hanyalah cookie sesi Riot di penyimpanan privat aplikasi di HP-mu, " +
        "dipakai untuk login otomatis dan notifikasi. Token akses Riot hanya ada di memori dan tidak dikirim ke server Valoid.",
    "Data apa saja yang disimpan Valoid?" to
        "Di akun Valoid (Firestore) hanya tersimpan riwayat toko harianmu dan daftar skin favorit. " +
        "Data akun Riot seperti password, token, atau cookie tidak pernah dikirim ke server Valoid.",
    "Bagaimana cara memutus akun Riot?" to
        "Buka Pengaturan, lalu pilih Akun Riot dan tekan \"Putuskan akun Riot\". Cookie sesi Riot langsung dihapus dari HP. " +
        "Keluar dari akun Valoid juga menghapusnya.",
    "Apakah sesi Riot bisa berakhir sendiri?" to
        "Ya. Demi keamanan, sesi Riot bisa kedaluwarsa setelah beberapa waktu. Jika itu terjadi, cukup hubungkan lagi lewat halaman login Riot. " +
        "Centang \"Stay signed in\" saat login agar sesi bertahan lebih lama.",
    "Notifikasi skin favorit tidak muncul?" to
        "Pastikan izin notifikasi Valoid aktif dan akun Riot masih terhubung. Di beberapa HP, mode hemat baterai menahan tugas latar; " +
        "atur Penghemat baterai Valoid ke \"Tanpa batasan\".",
    "Apakah Valoid aplikasi resmi Riot Games?" to
        "Bukan. Valoid adalah aplikasi buatan penggemar dan tidak berafiliasi dengan atau didukung oleh Riot Games.",
    "Apakah akun Riot bisa terkena masalah?" to
        "Valoid membaca data toko dan profil lewat layanan Riot yang tidak didokumentasikan secara resmi, seperti aplikasi sejenis. " +
        "Valoid hanya membaca data dan tidak melakukan pembelian atau perubahan apa pun pada akunmu.",
)

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FaqScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader("Pertanyaan umum", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FAQ, key = { it.first }) { (q, a) -> FaqItem(q, a) }
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var open by rememberSaveable(question) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(Modifier.clickable { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(question, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
            }
            if (open) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Text(answer, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageHeader("Tentang Valoid Hub", onBack)
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ValoidLogo(112.dp)
                    Text("VALOID HUB", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                    Text("Versi $version", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            item {
                Text(
                    "Valoid membantu pemain Valorant memantau toko harian, Night Market, bundle, dan koleksi skin, " +
                        "lengkap dengan profil, rank, riwayat match, serta kontrak dan misi — semua di satu tempat.\n\n" +
                        "Simpan skin favoritmu dan dapatkan notifikasi saat skin itu muncul di toko. " +
                        "Valoid terus dikembangkan; masukan dan ide selalu kami sambut.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Column {
                    TextButton(onClick = { uriHandler.openUri(PRIVACY_URL) }) { Text("Kebijakan privasi") }
                    TextButton(onClick = { uriHandler.openUri(RULES_URL) }) { Text("Aturan komunitas") }
                    TextButton(onClick = { uriHandler.openUri(DELETE_ACCOUNT_URL) }) { Text("Hapus akun dan data") }
                    TextButton(onClick = { uriHandler.openUri("mailto:$SUPPORT_EMAIL") }) { Text("Kontak: $SUPPORT_EMAIL") }
                }
            }
            item {
                Text(
                    "Valoid tidak didukung oleh Riot Games dan tidak mencerminkan pandangan atau opini Riot Games atau siapa pun yang " +
                        "terlibat secara resmi dalam produksi atau pengelolaan properti Riot Games. Riot Games dan semua properti terkait " +
                        "adalah merek dagang atau merek dagang terdaftar milik Riot Games, Inc.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private const val PRIVACY_URL = "https://gitgitdev.my.id/valoid/privacy/"
private const val RULES_URL = "https://gitgitdev.my.id/valoid/aturan-komunitas/"
private const val DELETE_ACCOUNT_URL = "https://gitgitdev.my.id/valoid/hapus-akun/"
private const val SUPPORT_EMAIL = "support@gitgitdev.my.id"
