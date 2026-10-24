package com.valoid.mobile.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.fragment.app.FragmentActivity
import com.valoid.mobile.security.AppLock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valoid.mobile.BuildConfig
import com.valoid.mobile.ads.AdsManager
import com.valoid.mobile.ads.StoreUnlock
import com.valoid.mobile.billing.ProManager
import com.valoid.mobile.data.SkinOffer
import com.valoid.mobile.notify.StoreNotifications

private enum class Tab(val label: String, val icon: ImageVector) {
    SHOP("Toko", Icons.Filled.ShoppingCart),
    PROFILE("Profil", Icons.Filled.Person),
    SETTINGS("Pengaturan", Icons.Filled.Settings),
}

@Composable
fun ValoidApp(authVm: AuthViewModel = viewModel(), shopVm: ShopViewModel = viewModel()) {
    val context = LocalContext.current
    val user by authVm.user.collectAsStateWithLifecycle()
    val shop by shopVm.state.collectAsStateWithLifecycle()
    val detail by shopVm.detail.collectAsStateWithLifecycle()
    val bundle by shopVm.bundle.collectAsStateWithLifecycle()
    val favorites by shopVm.favorites.collectAsStateWithLifecycle()
    val skinCatalog by shopVm.skinCatalog.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var tab by rememberSaveable { mutableStateOf(Tab.SHOP) }
    var showRiotLogin by rememberSaveable { mutableStateOf(false) }
    var showFaq by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showNight by rememberSaveable { mutableStateOf(false) }
    var showPaywall by rememberSaveable { mutableStateOf(false) }
    val isPro by ProManager.isPro.collectAsStateWithLifecycle()
    val riotAccounts by shopVm.accounts.collectAsStateWithLifecycle()
    val activeAccountId by shopVm.activeAccountId.collectAsStateWithLifecycle()
    var debugPro by remember { mutableStateOf(ProManager.debugOverride) }
    var adWatching by remember { mutableStateOf(false) }
    var adError by remember { mutableStateOf<String?>(null) }
    var adFailures by remember { mutableStateOf(0) }
    var unlockTick by remember { mutableStateOf(0) }
    var showFavorites by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showMatches by rememberSaveable { mutableStateOf(false) }
    var showContracts by rememberSaveable { mutableStateOf(false) }
    var showLoadout by rememberSaveable { mutableStateOf(false) }
    var showCareer by rememberSaveable { mutableStateOf(false) }
    val openMatch by shopVm.openMatch.collectAsStateWithLifecycle()
    var lockEnabled by remember { mutableStateOf(AppLock.isEnabled(context)) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }

    val uid = user?.uid
    if (uid == null) {
        AuthScreen(authVm)
        return
    }

    if (showRiotLogin) {
        RiotLoginScreen(
            onToken = { token, idToken ->
                showRiotLogin = false
                shopVm.onRiotToken(token, idToken, uid)
            },
            onCancel = { showRiotLogin = false },
        )
        return
    }

    LaunchedEffect(uid) {
        shopVm.autoConnect(uid)
        shopVm.watchFavorites(uid)
    }

    val favoriteIds = favorites.map { it.id }.toSet()
    val front = shop.storefront
    val storeOffers = front?.let { f -> f.daily.map { it to f.dailyEndsAtMillis } + f.nightMarket.map { it to f.nightMarketEndsAtMillis } }.orEmpty()
    val inStoreIds = storeOffers.map { it.first.id.lowercase() }.toSet()

    fun toggleFavorite(id: String, name: String, iconUrl: String?) {
        // Skin favorit adalah fitur Valoid Pro.
        if (!isPro) {
            showPaywall = true
            return
        }
        val added = shopVm.toggleFavorite(uid, id, name, iconUrl)
        // Minta izin notifikasi saat pertama kali memfavoritkan skin.
        if (added && !StoreNotifications.canNotify(context) && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Membuka detail; bila skin sedang di toko, harga dan sisa waktunya ikut ditampilkan. */
    fun openSkin(id: String, name: String, iconUrl: String?) {
        val inStore = storeOffers.firstOrNull { it.first.id.equals(id, ignoreCase = true) }
        if (inStore != null) shopVm.openDetail(inStore.first, inStore.second) else shopVm.openDetail(SkinOffer(id, name, iconUrl, 0), 0)
    }

    if (showPaywall) {
        PaywallScreen(onClose = { showPaywall = false })
        return
    }
    if (showFaq) {
        FaqScreen(onBack = { showFaq = false })
        return
    }
    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    openMatch?.let {
        MatchDetailScreen(it, onBack = { shopVm.openMatch(null) })
        return
    }

    if (showContracts) {
        LaunchedEffect(Unit) { shopVm.loadContracts() }
        ContractsScreen(
            overview = shop.contracts,
            loading = shop.contractsLoading,
            error = shop.contractsError,
            onRefresh = shopVm::loadContracts,
            onBack = { showContracts = false },
        )
        return
    }

    if (showMatches) {
        LaunchedEffect(Unit) { if (shop.matches.isEmpty()) shopVm.loadMatches() }
        MatchHistoryScreen(
            matches = shop.matches,
            loading = shop.matchesLoading,
            error = shop.matchesError,
            canLoadMore = !shop.matchesEnd,
            onLoadMore = { shopVm.loadMatches() },
            onOpen = { shopVm.openMatch(it) },
            onBack = { showMatches = false },
        )
        return
    }

    detail?.let {
        SkinDetailScreen(
            it,
            onBack = shopVm::closeDetail,
            favorite = it.offer.id.lowercase() in favoriteIds,
            onToggleFavorite = { toggleFavorite(it.offer.id, it.detail?.name ?: it.offer.name, it.offer.iconUrl) },
            isPro = isPro,
            onOpenPaywall = { showPaywall = true },
        )
        return
    }

    bundle?.let { b ->
        val endsAt = front?.endsAtMillis(b) ?: 0
        BundleDetailScreen(
            bundle = b,
            endsAtMillis = endsAt,
            onBack = shopVm::closeBundle,
            onSkinClick = { item -> shopVm.openDetail(SkinOffer(item.id, item.name, item.iconUrl, item.price), endsAt) },
        )
        return
    }

    if (showCareer) {
        LaunchedEffect(Unit) { shopVm.loadCareer() }
        CareerScreen(
            career = shop.career,
            loading = shop.careerLoading,
            error = shop.careerError,
            onRefresh = shopVm::loadCareer,
            onBack = { showCareer = false },
        )
        return
    }

    if (showLoadout) {
        LaunchedEffect(Unit) { if (shop.collection == null) shopVm.loadCollection() }
        LoadoutFlow(
            collection = shop.collection,
            loading = shop.collectionLoading,
            error = shop.collectionError,
            onRefresh = shopVm::loadCollection,
            onOpenSkin = { w -> w.skinLevelId?.let { shopVm.openDetail(SkinOffer(it, w.skinName, w.imageUrl, 0), 0) } },
            onBack = { showLoadout = false },
        )
        return
    }

    if (showNight) {
        SubPage(null, onBack = { showNight = false }) {
            OfferListScreen(
                title = "Night Market",
                emptyText = "Night Market sedang tidak aktif.",
                offers = front?.nightMarket.orEmpty(),
                endsAtMillis = front?.nightMarketEndsAtMillis ?: 0,
                loading = shop.loading,
                error = shop.error,
                onRefresh = { shopVm.refresh(uid) },
                onOfferClick = { shopVm.openDetail(it, front?.nightMarketEndsAtMillis ?: 0) },
                favoriteIds = favoriteIds,
                onToggleFavorite = { toggleFavorite(it.id, it.name, it.iconUrl) },
            )
        }
        return
    }
    if (showFavorites && isPro) {
        LaunchedEffect(Unit) { shopVm.loadCatalog() }
        SubPage("Favorit", onBack = { showFavorites = false }) {
            FavoritesScreen(favorites = favorites, catalog = skinCatalog, inStoreIds = inStoreIds, onToggle = ::toggleFavorite, onOpen = ::openSkin)
        }
        return
    }
    if (showHistory && isPro) {
        LaunchedEffect(Unit) { shopVm.loadHistory(uid) }
        SubPage(null, onBack = { showHistory = false }) {
            HistoryScreen(days = shop.history, loading = shop.historyLoading, error = shop.historyError, onRefresh = { shopVm.loadHistory(uid) })
        }
        return
    }

    val nightAvailable = front?.nightMarket?.isNotEmpty() == true
    // Pengguna gratis membuka toko harian dengan iklan untuk satu sesi (1 jam, selama rotasi yang sama).
    val dailyRotation = front?.rotationDate
    val sessionEndsAt = if (isPro || dailyRotation == null) {
        null
    } else {
        remember(unlockTick, dailyRotation) { StoreUnlock.expiresAt(context, dailyRotation) }
    }
    val dailyUnlocked = isPro || dailyRotation == null || sessionEndsAt != null
    // Kunci lagi tepat saat sesi berakhir, walau aplikasi sedang terbuka.
    LaunchedEffect(sessionEndsAt) {
        val end = sessionEndsAt ?: return@LaunchedEffect
        kotlinx.coroutines.delay((end - System.currentTimeMillis()).coerceAtLeast(0) + 500)
        unlockTick++
    }
    Box(Modifier.fillMaxSize()) {
        // Ruang bawah dikosongkan untuk navigasi mengambang.
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp)) {
            when {
                tab == Tab.SETTINGS -> SettingsTab(
                    email = user?.email,
                    riotConnected = shop.connected,
                    riotConnecting = shop.loading,
                    lockEnabled = lockEnabled,
                    lockAvailable = AppLock.isAvailable(context),
                    message = settingsMessage,
                    onToggleLock = { enable ->
                        val activity = context as? FragmentActivity ?: return@SettingsTab
                        // Verifikasi dulu agar kunci tidak bisa dinyalakan/dimatikan tanpa pemilik HP.
                        AppLock.authenticate(
                            activity,
                            title = if (enable) "Aktifkan kunci aplikasi" else "Matikan kunci aplikasi",
                            onSuccess = {
                                AppLock.setEnabled(context, enable)
                                lockEnabled = enable
                                settingsMessage = if (enable) "Kunci aplikasi aktif." else "Kunci aplikasi dimatikan."
                            },
                            onFailure = { settingsMessage = it },
                        )
                    },
                    // Pengguna gratis hanya dapat menautkan satu akun Riot.
                    onConnectRiot = { if (riotAccounts.isEmpty() || isPro) showRiotLogin = true else showPaywall = true },
                    onDisconnectRiot = {
                        shopVm.disconnectRiot(uid)
                        settingsMessage = "Akun Riot diputuskan dan sesinya dihapus."
                    },
                    accounts = riotAccounts,
                    activeAccountId = activeAccountId,
                    onSwitchAccount = { shopVm.switchAccount(it, uid) },
                    onRemoveAccount = {
                        shopVm.removeAccount(it, uid)
                        settingsMessage = "Akun Riot dilepas dan sesinya dihapus."
                    },
                    onSignOut = {
                        shopVm.onSignOut()
                        authVm.signOut()
                    },
                    onOpenFaq = { showFaq = true },
                    onOpenAbout = { showAbout = true },
                    isPro = isPro,
                    onOpenPaywall = { showPaywall = true },
                    debugPro = if (BuildConfig.DEBUG) debugPro else null,
                    onDebugPro = {
                        ProManager.setDebugOverride(it)
                        debugPro = it
                    },
                )
                !shop.connected -> ConnectRiotPrompt(
                    state = shop,
                    onConnect = { showRiotLogin = true },
                    onRetry = { shopVm.retryConnect(uid) },
                )
                tab == Tab.PROFILE -> ProfileScreen(
                    profile = shop.profile,
                    loading = shop.profileLoading,
                    error = shop.profileError,
                    onRefresh = shopVm::loadProfile,
                    onOpenMatches = { showMatches = true },
                    onOpenContracts = { showContracts = true },
                    onOpenLoadout = { showLoadout = true },
                    onOpenCareer = { showCareer = true },
                )
                else -> OfferListScreen(
                    title = "Toko harian",
                    emptyText = "Toko kosong.",
                    offers = front?.daily.orEmpty(),
                    endsAtMillis = front?.dailyEndsAtMillis ?: 0,
                    loading = shop.loading,
                    error = shop.error,
                    onRefresh = { shopVm.refresh(uid) },
                    onOfferClick = { shopVm.openDetail(it, front?.dailyEndsAtMillis ?: 0) },
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { toggleFavorite(it.id, it.name, it.iconUrl) },
                    locked = if (dailyUnlocked || dailyRotation == null) {
                        null
                    } else {
                        {
                            val rotation = dailyRotation
                            run {
                                StoreLockedCard(
                                    watching = adWatching,
                                    error = adError,
                                    allowSkip = adFailures >= 2,
                                    onWatch = {
                                        val activity = context as? android.app.Activity ?: return@StoreLockedCard
                                        adWatching = true
                                        adError = null
                                        AdsManager.showRewarded(
                                            activity,
                                            onRewarded = {
                                                adWatching = false
                                                adFailures = 0
                                                StoreUnlock.unlock(context, rotation)
                                                unlockTick++
                                            },
                                            onFailed = {
                                                adWatching = false
                                                adFailures++
                                                adError = it
                                            },
                                        )
                                    },
                                    onSkip = {
                                        StoreUnlock.unlock(context, rotation)
                                        unlockTick++
                                    },
                                    onPro = { showPaywall = true },
                                )
                            }
                        }
                    },
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StoreShortcuts(
                                nightAvailable = nightAvailable,
                                isPro = isPro,
                                onNight = { showNight = true },
                                onFavorites = { if (isPro) showFavorites = true else showPaywall = true },
                                onHistory = { if (isPro) showHistory = true else showPaywall = true },
                            )
                            sessionEndsAt?.let { StoreSessionBanner(it, onPro = { showPaywall = true }) }
                            shop.wallet?.let { WalletBar(it) }
                            if (front != null && front.bundles.isNotEmpty()) {
                                Text("Bundle", style = MaterialTheme.typography.titleLarge)
                                BundleRow(front.bundles, front::endsAtMillis, shopVm::openBundle)
                            }
                        }
                    },
                    footer = {
                        if (front != null && front.accessories.isNotEmpty()) {
                            AccessoryGrid(front.accessories, front.accessoryEndsAtMillis)
                        }
                    },
                )
            }
        }
        FloatingNav(tab, onSelect = { tab = it }, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** Halaman di dalam Toko (Night Market, Favorit, Riwayat) dengan tombol kembali. */
@Composable
private fun SubPage(title: String?, onBack: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali") }
            title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun StoreShortcuts(nightAvailable: Boolean, isPro: Boolean, onNight: () -> Unit, onFavorites: () -> Unit, onHistory: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShortcutTile("Night Market", Icons.Filled.Star, Modifier.weight(1f), enabled = nightAvailable, highlight = nightAvailable, onClick = onNight)
        ShortcutTile("Favorit", Icons.Filled.Favorite, Modifier.weight(1f), pro = !isPro, onClick = onFavorites)
        ShortcutTile("Riwayat", Icons.Filled.DateRange, Modifier.weight(1f), pro = !isPro, onClick = onHistory)
    }
}

@Composable
private fun ShortcutTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    enabled: Boolean = true,
    highlight: Boolean = false,
    pro: Boolean = false,
    onClick: () -> Unit,
) {
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        highlight -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (highlight) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.4f else 0.2f)),
        modifier = modifier.fillMaxHeight(),
    ) {
        Box {
            Column(
                Modifier.fillMaxSize().padding(vertical = 12.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.primary else content, modifier = Modifier.size(22.dp))
                Text(
                    label,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (pro) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { ProBadge() }
        }
    }
}

/** Navigasi bawah berbentuk pil yang melayang di atas konten. */
@Composable
private fun FloatingNav(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 12.dp,
        modifier = modifier.navigationBarsPadding().padding(bottom = 12.dp),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        t.icon,
                        t.label,
                        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
