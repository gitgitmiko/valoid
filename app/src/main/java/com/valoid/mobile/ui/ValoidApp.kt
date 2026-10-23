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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valoid.mobile.data.SkinOffer
import com.valoid.mobile.notify.StoreNotifications

private enum class Tab(val label: String) { PROFILE("Profil"), SHOP("Toko"), NIGHT("Night"), FAVORITES("Favorit"), HISTORY("Riwayat") }

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
    var showSettings by rememberSaveable { mutableStateOf(false) }
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

    if (showSettings) {
        SettingsScreen(
            email = user?.email,
            riotConnected = shop.connected,
            lockEnabled = lockEnabled,
            lockAvailable = AppLock.isAvailable(context),
            message = settingsMessage,
            onToggleLock = { enable ->
                val activity = context as? FragmentActivity ?: return@SettingsScreen
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
            onDisconnectRiot = {
                shopVm.disconnectRiot()
                settingsMessage = "Akun Riot diputuskan dan cookie login dihapus."
            },
            onSignOut = {
                showSettings = false
                shopVm.onSignOut()
                authVm.signOut()
            },
            onBack = {
                showSettings = false
                settingsMessage = null
            },
        )
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

    LaunchedEffect(tab, uid) {
        if (tab == Tab.HISTORY) shopVm.loadHistory(uid)
        if (tab == Tab.FAVORITES) shopVm.loadCatalog()
    }

    Scaffold(
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(user?.email ?: "Valoid", Modifier.padding(top = 14.dp))
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Pengaturan") }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = {}, label = { Text(t.label, maxLines = 1) })
                }
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
            when {
                tab == Tab.HISTORY -> HistoryScreen(
                    days = shop.history,
                    loading = shop.historyLoading,
                    error = shop.historyError,
                    onRefresh = { shopVm.loadHistory(uid) },
                )
                tab == Tab.FAVORITES -> FavoritesScreen(
                    favorites = favorites,
                    catalog = skinCatalog,
                    inStoreIds = inStoreIds,
                    onToggle = ::toggleFavorite,
                    onOpen = ::openSkin,
                )
                !shop.connected -> ConnectRiotPrompt(
                    state = shop,
                    onRegion = shopVm::setRegion,
                    onConnect = { showRiotLogin = true },
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
                tab == Tab.SHOP -> OfferListScreen(
                    title = "Toko harian",
                    emptyText = "Toko kosong.",
                    offers = shop.storefront?.daily.orEmpty(),
                    endsAtMillis = shop.storefront?.dailyEndsAtMillis ?: 0,
                    loading = shop.loading,
                    error = shop.error,
                    onRefresh = { shopVm.refresh(uid) },
                    onOfferClick = { shopVm.openDetail(it, shop.storefront?.dailyEndsAtMillis ?: 0) },
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { toggleFavorite(it.id, it.name, it.iconUrl) },
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                else -> OfferListScreen(
                    title = "Night Market",
                    emptyText = "Night Market sedang tidak aktif.",
                    offers = shop.storefront?.nightMarket.orEmpty(),
                    endsAtMillis = shop.storefront?.nightMarketEndsAtMillis ?: 0,
                    loading = shop.loading,
                    error = shop.error,
                    onRefresh = { shopVm.refresh(uid) },
                    onOfferClick = { shopVm.openDetail(it, shop.storefront?.nightMarketEndsAtMillis ?: 0) },
                    favoriteIds = favoriteIds,
                    onToggleFavorite = { toggleFavorite(it.id, it.name, it.iconUrl) },
                )
            }
        }
    }
}
