package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.ChatException
import com.valoid.mobile.data.ChatIdentity
import com.valoid.mobile.data.ChatMessage
import com.valoid.mobile.data.ChatRepository
import com.valoid.mobile.data.BlockedUser
import com.valoid.mobile.data.ModerationRepository
import com.valoid.mobile.data.PartyPost
import com.valoid.mobile.data.Report
import com.valoid.mobile.data.ReportReason
import kotlinx.coroutines.flow.combine
import com.valoid.mobile.data.Presence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(val sending: Boolean = false, val error: String? = null)

const val ROOM_GLOBAL = "global"

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repo: ChatRepository = ChatRepository(),
    private val moderation: ModerationRepository = ModerationRepository(),
) : ViewModel() {

    /** Pengguna Valoid yang masuk; sumber daftar blokir, status admin, dan status ban. */
    private val _uid = MutableStateFlow<String?>(null)

    fun setUser(uid: String?) {
        _uid.value = uid
    }

    val blocked: StateFlow<List<BlockedUser>> = _uid
        .flatMapLatest { uid -> uid?.let { moderation.blocked(it).catch { emit(emptyList()) } } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isAdmin: StateFlow<Boolean> = _uid
        .flatMapLatest { uid -> uid?.let { moderation.isAdmin(it).catch { emit(false) } } ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isBanned: StateFlow<Boolean> = _uid
        .flatMapLatest { uid -> uid?.let { moderation.isBanned(it).catch { emit(false) } } ?: flowOf(false) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val onlineCount: StateFlow<Int?> = Presence.onlineCount().catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _room = MutableStateFlow<String?>(null)
    val room: StateFlow<String?> = _room

    /** Pesan room yang dibuka, tanpa pesan dari pengguna yang diblokir. */
    val messages: StateFlow<List<ChatMessage>> = _room
        .flatMapLatest { room -> room?.let { repo.messages(it).catch { emit(emptyList()) } } ?: flowOf(emptyList()) }
        .combine(blocked) { list, blockedUsers ->
            val hidden = blockedUsers.map { it.uid }.toSet()
            list.filterNot { it.uid in hidden }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reports: StateFlow<List<Report>> = isAdmin
        .flatMapLatest { admin -> if (admin) moderation.reports().catch { emit(emptyList()) } else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<String?>(null)

    /** Pesan singkat hasil tindakan moderasi (laporan terkirim, pengguna diblokir, dll.). */
    val notice: StateFlow<String?> = _notice

    fun clearNotice() {
        _notice.value = null
    }

    private fun act(success: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _notice.value = runCatching { block() }.fold({ success }, { "Gagal: ${it.message ?: "coba lagi"}" })
        }
    }

    fun report(message: ChatMessage, reason: ReportReason, detail: String) {
        val room = _room.value ?: return
        act("Laporan terkirim. Terima kasih, admin akan meninjaunya.") { moderation.report(room, message, reason, detail) }
    }

    fun block(message: ChatMessage) = act("${message.riotId} diblokir. Pesannya tidak akan tampil lagi.") { moderation.block(message) }

    fun unblock(user: BlockedUser) = act("Blokir ${user.name} dibuka.") { moderation.unblock(user.uid) }

    fun deleteOwn(message: ChatMessage) {
        val room = _room.value ?: return
        act("Pesan dihapus.") { repo.deleteOwn(room, message.id) }
    }

    fun deleteMessage(room: String, messageId: String) = act("Pesan dihapus.") { moderation.deleteMessage(room, messageId) }

    fun dismissReports(ids: List<String>) = act("Laporan diabaikan.") { moderation.dismissReports(ids) }

    fun ban(targetUid: String, name: String, reason: String) = act("$name di-ban dari chat.") { moderation.ban(targetUid, name, reason) }

    val roomOnline: StateFlow<Int?> = _room
        .flatMapLatest { room -> room?.let { Presence.roomCount(it).catch { } } ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    fun openRoom(room: String, uid: String) {
        _room.value = room
        Presence.joinRoom(room, uid)
    }

    fun closeRoom(uid: String) {
        _room.value?.let { Presence.leaveRoom(it, uid) }
        _room.value = null
        _state.value = ChatUiState()
    }

    fun roomCount(room: String): Flow<Int?> = Presence.roomCount(room).catch { }

    /** Mengirim [text] (dan [party] bila ajakan party); [onSent] dipanggil agar kolom input bisa dikosongkan. */
    fun send(identity: ChatIdentity, text: String, party: PartyPost? = null, onSent: () -> Unit) {
        val room = _room.value ?: return
        val clean = text.trim().take(MAX_MESSAGE)
        if (clean.isEmpty() || _state.value.sending) return
        _state.value = ChatUiState(sending = true)
        viewModelScope.launch {
            try {
                repo.send(room, identity, clean, party)
                _state.value = ChatUiState()
                onSent()
            } catch (e: ChatException) {
                _state.value = ChatUiState(error = e.message)
            } catch (e: Exception) {
                _state.value = ChatUiState(error = "Pesan gagal terkirim.")
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        const val MAX_MESSAGE = 500
    }
}
