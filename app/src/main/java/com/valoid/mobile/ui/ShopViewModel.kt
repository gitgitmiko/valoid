package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.BundleOffer
import com.valoid.mobile.data.HistoryDay
import com.valoid.mobile.data.RiotAuth
import com.valoid.mobile.data.HistoryRepository
import com.valoid.mobile.data.RiotException
import com.valoid.mobile.data.RiotRegion
import com.valoid.mobile.data.RiotRepository
import com.valoid.mobile.data.RiotSession
import com.valoid.mobile.data.SkinCatalogRepository
import com.valoid.mobile.data.SkinDetail
import com.valoid.mobile.data.SkinOffer
import com.valoid.mobile.data.Storefront
import com.valoid.mobile.data.Wallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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
)

data class DetailUiState(
    val offer: SkinOffer,
    val endsAtMillis: Long,
    val detail: SkinDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Token Riot hanya disimpan di memori. Saat aplikasi dibuka lagi atau token kedaluwarsa, token baru
 * diminta memakai cookie sesi Riot di perangkat (lihat [RiotRepository.reauthenticate]).
 */
class ShopViewModel(
    private val riot: RiotRepository = RiotRepository(),
    private val history: HistoryRepository = HistoryRepository(),
    private val catalog: SkinCatalogRepository = SkinCatalogRepository(),
) : ViewModel() {

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
                runCatching { history.saveDay(uid, LocalDate.now().toString(), front.daily) }
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

    fun disconnectRiot() {
        session = null
        RiotAuth.clear()
        _state.update { ShopUiState(region = it.region, history = it.history) }
    }
}
