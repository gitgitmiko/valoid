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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AssistChip
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
    var debugPro by remember { mutableStateOf(ProManager.debugOverride) }
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
    if (showFavorites) {
        LaunchedEffect(Unit) { shopVm.loadCatalog() }
        SubPage("Favorit", onBack = { showFavorites = false }) {
            FavoritesScreen(favorites = favorites, catalog = skinCatalog, inStoreIds = inStoreIds, onToggle = ::toggleFavorite, onOpen = ::openSkin)
        }
        return
    }
    if (showHistory) {
        LaunchedEffect(Unit) { shopVm.loadHistory(uid) }
        SubPage(null, onBack = { showHistory = false }) {
            HistoryScreen(days = shop.history, loading = shop.historyLoading, error = shop.historyError, onRefresh = { shopVm.loadHistory(uid) })
        }
        return
    }

    val nightAvailable = front?.nightMarket?.isNotEmpty() == true
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
                    onConnectRiot = { showRiotLogin = true },
                    onDisconnectRiot = {
                        shopVm.disconnectRiot()
                        settingsMessage = "Akun Riot diputuskan dan cookie login dihapus."
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
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ShopShortcut("Night Market", Icons.Filled.Star, enabled = nightAvailable, highlight = nightAvailable) { showNight = true }
                                ShopShortcut("Favorit", Icons.Filled.Favorite) { showFavorites = true }
                                ShopShortcut("Riwayat", Icons.Filled.DateRange) { showHistory = true }
                            }
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
private fun ShopShortcut(label: String, icon: ImageVector, enabled: Boolean = true, highlight: Boolean = false, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(18.dp)) },
        colors = if (highlight) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                labelColor = MaterialTheme.colorScheme.primary,
                leadingIconContentColor = MaterialTheme.colorScheme.primary,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
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
