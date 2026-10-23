package com.valoid.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.valoid.mobile.data.SkinOffer

private enum class Tab(val label: String) { SHOP("Toko"), NIGHT("Night Market"), HISTORY("Riwayat") }

@Composable
fun ValoidApp(authVm: AuthViewModel = viewModel(), shopVm: ShopViewModel = viewModel()) {
    val user by authVm.user.collectAsStateWithLifecycle()
    val shop by shopVm.state.collectAsStateWithLifecycle()
    val detail by shopVm.detail.collectAsStateWithLifecycle()
    val bundle by shopVm.bundle.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SHOP) }
    var showRiotLogin by rememberSaveable { mutableStateOf(false) }

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

    LaunchedEffect(uid) { shopVm.autoConnect(uid) }

    detail?.let {
        SkinDetailScreen(it, onBack = shopVm::closeDetail)
        return
    }

    val front = shop.storefront
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

    LaunchedEffect(tab, uid) {
        if (tab == Tab.HISTORY) shopVm.loadHistory(uid)
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
                TextButton(onClick = {
                    shopVm.disconnectRiot()
                    authVm.signOut()
                }) { Text("Keluar") }
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = {}, label = { Text(t.label) })
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
                !shop.connected -> ConnectRiotPrompt(
                    state = shop,
                    onRegion = shopVm::setRegion,
                    onConnect = { showRiotLogin = true },
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
                )
            }
        }
    }
}
