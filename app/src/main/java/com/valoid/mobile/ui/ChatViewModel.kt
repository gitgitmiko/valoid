package com.valoid.mobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.valoid.mobile.data.ChatException
import com.valoid.mobile.data.ChatIdentity
import com.valoid.mobile.data.ChatMessage
import com.valoid.mobile.data.ChatRepository
import com.valoid.mobile.data.PartyPost
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
class ChatViewModel(private val repo: ChatRepository = ChatRepository()) : ViewModel() {

    val onlineCount: StateFlow<Int?> = Presence.onlineCount().catch { }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _room = MutableStateFlow<String?>(null)
    val room: StateFlow<String?> = _room

    val messages: StateFlow<List<ChatMessage>> = _room
        .flatMapLatest { room -> room?.let { repo.messages(it).catch { emit(emptyList()) } } ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
