package com.valoid.mobile.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Penanda bahwa perkenalan fitur sudah pernah ditampilkan. */
object Onboarding {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        ValoidLogo(132.dp)
        Spacer(Modifier.weight(1f))
        Text("VALOID", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary, strokeWidth = 3.dp, modifier = Modifier.padding(top = 28.dp).size(32.dp))
        Text("Memuat", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.padding(top = 12.dp, bottom = 48.dp))
    }
}

private class Slide(val icon: ImageVector, val title: String, val body: String)

private val SLIDES = listOf(
    Slide(Icons.Filled.ShoppingCart, "Cek tokomu", "Lihat toko harian, bundle, aksesori, dan Night Market kapan saja, lengkap dengan hitung mundur."),
    Slide(Icons.Filled.Favorite, "Skin favorit", "Simpan skin incaranmu dan dapatkan notifikasi saat skin itu muncul di tokomu."),
    Slide(Icons.Filled.Person, "Profil & rank", "Lihat rank, peak rank, ringkasan karier per act, dan level akunmu."),
    Slide(Icons.Filled.DateRange, "Analisis match", "Papan skor, kill feed per ronde, dan duel melawan setiap lawan."),
    Slide(Icons.Filled.CheckCircle, "Kontrak & misi", "Pantau Battle Pass, kontrak agent, serta misi mingguan yang aktif dan mendatang."),
    Slide(Icons.Filled.Star, "Koleksi kamu", "Jelajahi arsenal, kartu, spray, title, dan charm milikmu."),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    // Di akhir, minta izin notifikasi (Android 13+) lalu masuk ke aplikasi apa pun jawabannya.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish(context, onFinish) }
    val last = pager.currentPage == SLIDES.lastIndex

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = { finish(context, onFinish) }) { Text("Lewati") }
            else Spacer(Modifier.height(48.dp))
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            val slide = SLIDES[page]
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Illustration(slide.icon) }
                Text(slide.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    slide.body,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            repeat(SLIDES.size) { i ->
                val width by animateDpAsState(if (i == pager.currentPage) 28.dp else 10.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 4.dp).height(10.dp).width(width).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        Button(
            onClick = {
                if (!last) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else if (Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    finish(context, onFinish)
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).height(56.dp),
        ) {
            Text(if (last) "Mulai" else "Lanjut", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun finish(context: Context, onFinish: () -> Unit) {
    Onboarding.markDone(context)
    onFinish()
}

/** Ilustrasi sederhana: ikon fitur dalam lingkaran bercahaya dengan pecahan berlian di sekitarnya. */
@Composable
private fun Illustration(icon: ImageVector) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        listOf(
            Triple(-110, -100, 14), Triple(100, -120, 10), Triple(-120, 70, 12),
            Triple(118, 40, 16), Triple(-40, 125, 9), Triple(60, 120, 11), Triple(10, -135, 8),
        ).forEachIndexed { i, (x, y, size) ->
            Box(
                Modifier.offset(x.dp, y.dp).size(size.dp).rotate(45f)
                    .background((if (i % 2 == 0) secondary else primary).copy(alpha = 0.45f)),
            )
        }
        Box(
            Modifier.size(200.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(secondary.copy(alpha = 0.35f), Color.Transparent))),
        )
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = primary, modifier = Modifier.size(64.dp))
        }
    }
}
