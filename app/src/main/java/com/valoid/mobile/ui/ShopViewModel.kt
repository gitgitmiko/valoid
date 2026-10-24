package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.BundleOffer
import com.valoid.mobile.data.CareerSummary
import com.valoid.mobile.data.ContractsOverview
import com.valoid.mobile.data.FavoriteSkin
import com.valoid.mobile.data.FavoritesRepository
import com.valoid.mobile.data.HistoryDay
import com.valoid.mobile.data.Match
import com.valoid.mobile.data.PlayerCollection
import com.valoid.mobile.data.PlayerProfile
import com.valoid.mobile.data.RiotAccount
import com.valoid.mobile.data.RiotAccounts
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
    val matches: List<Match> = emptyList(),
    val matchesLoading: Boolean = false,
    val matchesError: String? = null,
    val matchesEnd: Boolean = false,
    val contracts: ContractsOverview? = null,
    val contractsLoading: Boolean = false,
    val contractsError: String? = null,
    val collection: PlayerCollection? = null,
    val collectionLoading: Boolean = false,
    val collectionError: String? = null,
    val career: CareerSummary? = null,
    val careerLoading: Boolean = false,
    val careerError: String? = null,
)

data class DetailUiState(
    val offer: SkinOffer,
    val endsAtMillis: Long,
    val detail: SkinDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val stats: SkinStats? = null,
)

private const val MATCH_PAGE = 10

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

    val accounts: StateFlow<List<RiotAccount>> = RiotAccounts.accounts
    val activeAccountId: StateFlow<String?> = RiotAccounts.activeId

    fun setRegion(region: RiotRegion) = _state.update { it.copy(region = region) }

    /** Mencoba lagi login otomatis memakai sesi akun aktif (tombol "Coba lagi"). */
    fun retryConnect(uid: String) {
        autoConnectTried = false
        autoConnect(uid)
    }

    /**
     * Login Riot otomatis sekali per sesi aplikasi memakai cookie akun aktif. Cookie lama yang masih
     * tersimpan di WebView (versi sebelum banyak akun) dipindahkan ke daftar akun terenkripsi.
     */
    fun autoConnect(uid: String) {
        if (autoConnectTried || session != null) return
        autoConnectTried = true
        val account = RiotAccounts.active()
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val cookies = account?.cookies ?: riot.legacyWebViewCookies()
            val reauth = cookies?.let { runCatching { riot.reauthenticate(it) }.getOrNull() }
            if (reauth != null) {
                connect(reauth.tokens, reauth.cookies, uid)
                if (account == null) RiotAuth.clear()
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (account != null) "Sesi ${account.label} sudah berakhir. Hubungkan ulang akun ini." else null,
                    )
                }
            }
        }
    }

    /** Dipanggil setelah login di halaman Riot: simpan cookie sesi akun ini lalu hubungkan. */
    fun onRiotToken(accessToken: String, idToken: String?, uid: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val cookies = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { RiotAuth.takeWebViewCookies() }
            connect(RiotAuth.Tokens(accessToken, idToken), cookies, uid)
        }
    }

    private suspend fun connect(tokens: RiotAuth.Tokens, cookies: String?, uid: String) {
        try {
            val created = riot.createSession(tokens.accessToken, tokens.idToken, _state.value.region)
            if (cookies != null) RiotAccounts.upsert(RiotAccount(puuid = created.puuid, cookies = cookies))
            if (RiotAccounts.activeId.value != created.puuid) resetAccountState()
            RiotAccounts.setActive(created.puuid)
            session = created
            _state.update { it.copy(connected = true, region = created.region) }
            loadProfile()
            refresh(uid)
            riot.riotId(created)?.let { (name, tag) -> RiotAccounts.update(created.puuid) { it.copy(name = name, tag = tag) } }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e.message ?: "Gagal terhubung ke Riot") }
        }
    }

    /** Pindah ke akun Riot lain yang sudah ditautkan. */
    fun switchAccount(puuid: String, uid: String) {
        if (puuid == RiotAccounts.activeId.value && session != null) return
        RiotAccounts.setActive(puuid)
        session = null
        resetAccountState()
        retryConnect(uid)
    }

    /** Melepas akun Riot dari perangkat; bila itu akun aktif, pindah ke akun lain bila ada. */
    fun removeAccount(puuid: String, uid: String) {
        val wasActive = puuid == RiotAccounts.activeId.value
        RiotAccounts.remove(puuid)
        if (!wasActive) return
        session = null
        resetAccountState()
        if (RiotAccounts.active() != null) retryConnect(uid) else autoConnectTried = true
    }

    /** Data toko, profil, dan statistik milik akun sebelumnya dibuang saat berganti akun. */
    private fun resetAccountState() {
        fullHistory = emptyList()
        _openMatch.value = null
        _detail.value = null
        _bundle.value = null
        _state.update { ShopUiState(region = it.region) }
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
                runCatching { history.saveDay(uid, s.puuid, front.rotationDate, front.daily) }
                fullHistory = runCatching { history.loadHistory(uid, s.puuid, includeLegacy(s.puuid), limit = 400) }
                    .getOrDefault(fullHistory)
            } catch (e: RiotException) {
                if (e.sessionExpired) {
                    session = null
                    val reauth = RiotAccounts.active()?.let { runCatching { riot.reauthenticate(it.cookies) }.getOrNull() }
                    if (reauth != null) {
                        connect(reauth.tokens, reauth.cookies, uid)
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

    private fun includeLegacy(puuid: String) = RiotAccounts.oldest()?.puuid == puuid

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

    private val _openMatch = MutableStateFlow<Match?>(null)
    val openMatch: StateFlow<Match?> = _openMatch

    fun openMatch(match: Match?) {
        _openMatch.value = match
    }

    /** Memuat halaman match berikutnya; [reset] memulai lagi dari match terbaru. */
    fun loadMatches(reset: Boolean = false) {
        val s = session ?: return
        if (_state.value.matchesLoading) return
        val start = if (reset) 0 else _state.value.matches.size
        _state.update { it.copy(matchesLoading = true, matchesError = null) }
        viewModelScope.launch {
            val result = runCatching { riot.loadMatches(s, start, MATCH_PAGE) }
            _state.update { st ->
                val page = result.getOrNull()
                if (page == null) {
                    st.copy(matchesLoading = false, matchesError = result.exceptionOrNull()?.message ?: "Gagal memuat match")
                } else {
                    val merged = (if (reset) page else st.matches + page).distinctBy { it.id }.sortedByDescending { it.startedAtMillis }
                    st.copy(matchesLoading = false, matches = merged, matchesEnd = page.size < MATCH_PAGE)
                }
            }
        }
    }

    fun loadContracts() {
        val s = session ?: return
        _state.update { it.copy(contractsLoading = true, contractsError = null) }
        viewModelScope.launch {
            val result = runCatching { riot.loadContracts(s) }
            _state.update {
                it.copy(
                    contractsLoading = false,
                    contracts = result.getOrNull() ?: it.contracts,
                    contractsError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadCollection() {
        val s = session ?: return
        _state.update { it.copy(collectionLoading = true, collectionError = null) }
        viewModelScope.launch {
            val result = runCatching { riot.loadCollection(s) }
            _state.update {
                it.copy(
                    collectionLoading = false,
                    collection = result.getOrNull() ?: it.collection,
                    collectionError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun loadCareer() {
        val s = session ?: return
        _state.update { it.copy(careerLoading = true, careerError = null) }
        viewModelScope.launch {
            val result = runCatching { riot.loadCareer(s) }
            _state.update {
                it.copy(careerLoading = false, career = result.getOrNull() ?: it.career, careerError = result.exceptionOrNull()?.message)
            }
        }
    }

    fun loadHistory(uid: String) {
        val puuid = session?.puuid ?: RiotAccounts.activeId.value ?: return
        _state.update { it.copy(historyLoading = true, historyError = null) }
        viewModelScope.launch {
            try {
                val days = history.loadHistory(uid, puuid, includeLegacy(puuid))
                _state.update { it.copy(historyLoading = false, history = days) }
            } catch (e: Exception) {
                _state.update { it.copy(historyLoading = false, historyError = e.message ?: "Gagal memuat riwayat") }
            }
        }
    }

    /** Melepas akun Riot yang aktif dari perangkat tanpa keluar dari akun Valoid. */
    fun disconnectRiot(uid: String) {
        RiotAccounts.activeId.value?.let { removeAccount(it, uid) }
        RiotAuth.clear()
    }

    /** Dipanggil saat keluar dari akun Valoid: hapus semua akun Riot dan data pengguna sebelumnya. */
    fun onSignOut() {
        RiotAccounts.clear()
        RiotAuth.clear()
        session = null
        autoConnectTried = false
        favoritesJob?.cancel()
        favoritesJob = null
        _favorites.value = emptyList()
        resetAccountState()
    }
}
