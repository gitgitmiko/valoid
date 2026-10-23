package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.BundleOffer
import com.valoid.mobile.data.FavoriteSkin
import com.valoid.mobile.data.FavoritesRepository
import com.valoid.mobile.data.HistoryDay
import com.valoid.mobile.data.PlayerProfile
import com.valoid.mobile.data.RiotAuth
import com.valoid.mobile.data.HistoryRepository
import com.valoid.mobile.data.RiotException
import com.valoid.mobile.data.RiotRegion
import com.valoid.mobile.data.RiotRepository
import com.valoid.mobile.data.RiotSession
import com.valoid.mobile.data.SkinCatalogRepository
import com.valoid.mobile.data.SkinDetail
import com.valoid.mobile.data.SkinOffer
import com.valoid.mobile.data.SkinStats
import com.valoid.mobile.data.SkinSummary
import com.valoid.mobile.data.Storefront
import com.valoid.mobile.data.Wallet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    val region: RiotRegion = RiotRegion.AP,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val storefront: Storefront? = null,
    val wallet: Wallet? = null,
    val error: String? = null,
    val history: List<HistoryDay> = emptyList(),
    val historyLoading: Boolean = false,
    val historyError: String? = null,
    val profile: PlayerProfile? = null,
    val profileLoading: Boolean = false,
    val profileError: String? = null,
)

data class DetailUiState(
    val offer: SkinOffer,
    val endsAtMillis: Long,
    val detail: SkinDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val stats: SkinStats? = null,
)

/**
 * Token Riot hanya disimpan di memori. Saat aplikasi dibuka lagi atau token kedaluwarsa, token baru
 * diminta memakai cookie sesi Riot di perangkat (lihat [RiotRepository.reauthenticate]).
 */
class ShopViewModel(
    private val riot: RiotRepository = RiotRepository(),
    private val history: HistoryRepository = HistoryRepository(),
    private val catalog: SkinCatalogRepository = SkinCatalogRepository(),
    private val favoritesRepo: FavoritesRepository = FavoritesRepository(),
) : ViewModel() {

    private val _favorites = MutableStateFlow<List<FavoriteSkin>>(emptyList())
    val favorites: StateFlow<List<FavoriteSkin>> = _favorites
    private var favoritesJob: Job? = null

    private val _catalog = MutableStateFlow<List<SkinSummary>>(emptyList())
    val skinCatalog: StateFlow<List<SkinSummary>> = _catalog

    fun watchFavorites(uid: String) {
        if (favoritesJob != null) return
        favoritesJob = viewModelScope.launch {
            favoritesRepo.favorites(uid).catch { }.collect { _favorites.value = it }
        }
    }

    /** Menambah atau menghapus favorit; mengembalikan true bila skin baru saja difavoritkan. */
    fun toggleFavorite(uid: String, id: String, name: String, iconUrl: String?): Boolean {
        val key = id.lowercase()
        val wasFavorite = _favorites.value.any { it.id == key }
        _favorites.update { list ->
            if (wasFavorite) list.filterNot { it.id == key } else list + FavoriteSkin(key, name, iconUrl)
        }
        viewModelScope.launch {
            runCatching {
                if (wasFavorite) favoritesRepo.remove(uid, key) else favoritesRepo.add(uid, FavoriteSkin(key, name, iconUrl))
            }
        }
        return !wasFavorite
    }

    fun loadCatalog() {
        if (_catalog.value.isNotEmpty()) return
        viewModelScope.launch { runCatching { catalog.allSkins() }.onSuccess { _catalog.value = it } }
    }

    private val _detail = MutableStateFlow<DetailUiState?>(null)
    val detail: StateFlow<DetailUiState?> = _detail

    private val _bundle = MutableStateFlow<BundleOffer?>(null)
    val bundle: StateFlow<BundleOffer?> = _bundle

    fun openBundle(bundle: BundleOffer) {
        _bundle.value = bundle
    }

    fun closeBundle() {
        _bundle.value = null
    }

    fun openDetail(offer: SkinOffer, endsAtMillis: Long) {
        _detail.value = DetailUiState(offer, endsAtMillis)
        viewModelScope.launch {
            val stats = runCatching { skinStats(offer.id) }.getOrNull()
            _detail.update { current -> if (current?.offer?.id == offer.id) current.copy(stats = stats) else current }
        }
        viewModelScope.launch {
            val result = runCatching { catalog.detail(offer.id) }
            _detail.update { current ->
                if (current?.offer?.id != offer.id) return@update current
                current.copy(loading = false, detail = result.getOrNull(), error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    private val _state = MutableStateFlow(ShopUiState())
    val state: StateFlow<ShopUiState> = _state

    private var session: RiotSession? = null

    private var autoConnectTried = false

    fun setRegion(region: RiotRegion) = _state.update { it.copy(region = region) }

    /** Login Riot otomatis sekali per sesi aplikasi bila cookie sesi Riot masih berlaku. */
    fun autoConnect(uid: String) {
        if (autoConnectTried || session != null) return
        autoConnectTried = true
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val tokens = runCatching { riot.reauthenticate() }.getOrNull()
            if (tokens != null) {
                onRiotToken(tokens.accessToken, tokens.idToken, uid)
            } else {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun onRiotToken(accessToken: String, idToken: String?, uid: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val created = riot.createSession(accessToken, idToken, _state.value.region)
                session = created
                _state.update { it.copy(connected = true, region = created.region) }
                loadProfile()
                refresh(uid)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Gagal terhubung ke Riot") }
            }
        }
    }

    fun refresh(uid: String) {
        val s = session ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val front = riot.loadStorefront(s)
                _state.update { it.copy(loading = false, storefront = front) }
                // Saldo tidak wajib: kegagalan dompet tidak menggagalkan toko.
                runCatching { riot.loadWallet(s) }.onSuccess { w -> _state.update { it.copy(wallet = w) } }
                runCatching { history.saveDay(uid, front.rotationDate, front.daily) }
                fullHistory = runCatching { history.loadHistory(uid, limit = 400) }.getOrDefault(fullHistory)
            } catch (e: RiotException) {
                if (e.sessionExpired) {
                    session = null
                    val tokens = runCatching { riot.reauthenticate() }.getOrNull()
                    if (tokens != null) {
                        onRiotToken(tokens.accessToken, tokens.idToken, uid)
                        return@launch
                    }
                }
                _state.update {
                    it.copy(loading = false, error = e.message, connected = !e.sessionExpired && it.connected)
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Gagal memuat toko") }
            }
        }
    }

    /** Riwayat lengkap untuk statistik pribadi; dimuat setelah toko tercatat. */
    private var fullHistory: List<HistoryDay> = emptyList()

    private fun skinStats(id: String): SkinStats {
        val key = id.lowercase()
        val seenDays = fullHistory.filter { day -> day.offers.any { it.id.equals(key, ignoreCase = true) } }
        return SkinStats(
            personalCount = seenDays.size,
            personalDays = fullHistory.size,
            lastSeenByYou = seenDays.maxOfOrNull { it.date },
        )
    }

    fun loadProfile() {
        val s = session ?: return
        _state.update { it.copy(profileLoading = true, profileError = null) }
        viewModelScope.launch {
            val result = runCatching { riot.loadProfile(s) }
            _state.update {
                it.copy(
                    profileLoading = false,
                    profile = result.getOrNull() ?: it.profile,
                    profileError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadHistory(uid: String) {
        _state.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            try {
                val days = history.loadHistory(uid)
                _state.update { it.copy(historyLoading = false, history = days) }
            } catch (e: Exception) {
                _state.update { it.copy(historyLoading = false, historyError = e.message ?: "Gagal memuat riwayat") }
            }
        }
    }

    /** Menghapus sesi dan cookie Riot dari perangkat tanpa keluar dari akun Valoid. */
    fun disconnectRiot() {
        session = null
        autoConnectTried = true
        RiotAuth.clear()
        _state.update { ShopUiState(region = it.region, history = it.history) }
    }

    /** Dipanggil saat keluar dari akun Valoid: hapus sesi Riot dan data milik pengguna sebelumnya. */
    fun onSignOut() {
        disconnectRiot()
        autoConnectTried = false
        favoritesJob?.cancel()
        favoritesJob = null
        _favorites.value = emptyList()
        _state.update { ShopUiState(region = it.region) }
    }
}
