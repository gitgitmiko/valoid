package com.valoid.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.valoid.mobile.data.AuthRepository
import com.valoid.mobile.data.FavoritesRepository
import com.valoid.mobile.data.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

data class DeleteAccountState(val running: Boolean = false, val error: String? = null)

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository(),
    private val favorites: FavoritesRepository = FavoritesRepository(),
    private val history: HistoryRepository = HistoryRepository(),
) : ViewModel() {

    val user: StateFlow<FirebaseUser?> =
        repo.userChanges().stateIn(viewModelScope, SharingStarted.Eagerly, repo.currentUser)

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    fun signIn(email: String, password: String) = launchAuth {
        repo.signIn(email, password)
    }

    fun register(email: String, password: String) = launchAuth {
        repo.register(email, password)
    }

    fun sendReset(email: String) = launchAuth {
        repo.sendPasswordReset(email)
        _state.update { it.copy(info = "Jika email terdaftar, tautan reset password sudah dikirim.") }
    }

    fun signInWithGoogle(activityContext: Context, webClientId: String) = launchAuth {
        repo.signInWithGoogle(activityContext, webClientId)
    }

    fun signOut() = repo.signOut()

    val usesPassword: Boolean get() = repo.usesPassword

    private val _deleteState = MutableStateFlow(DeleteAccountState())
    val deleteState: StateFlow<DeleteAccountState> = _deleteState

    fun clearDeleteError() = _deleteState.update { it.copy(error = null) }

    /**
     * Verifikasi ulang, hapus favorit dan riwayat toko di Firestore, lalu hapus pengguna Firebase.
     * [onDeleted] dipanggil setelah akun terhapus untuk membersihkan data di perangkat.
     */
    fun deleteAccount(
        activityContext: Context,
        webClientId: String,
        password: String?,
        knownPuuids: Collection<String>,
        onDeleted: () -> Unit,
    ) {
        val uid = repo.currentUser?.uid ?: return
        if (_deleteState.value.running) return
        _deleteState.value = DeleteAccountState(running = true)
        viewModelScope.launch {
            try {
                repo.reauthenticate(activityContext, webClientId, password)
                favorites.deleteAll(uid)
                history.deleteAll(uid, knownPuuids)
                repo.deleteUser()
                _deleteState.value = DeleteAccountState()
                onDeleted()
            } catch (e: Exception) {
                _deleteState.value = DeleteAccountState(error = friendly(e))
            }
        }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    private fun friendly(e: Exception): String = when {
        e is FirebaseNetworkException -> "Tidak ada koneksi internet."
        e is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Format email tidak valid."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Email atau password salah."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Email sudah terdaftar."
            "ERROR_WEAK_PASSWORD" -> "Password terlalu lemah (minimal 6 karakter)."
            "ERROR_USER_DISABLED" -> "Akun ini dinonaktifkan."
            "ERROR_USER_MISMATCH" -> "Pilih akun Google yang sama dengan akun Valoid ini."
            else -> "Autentikasi gagal (${e.errorCode})."
        }
        e is IllegalArgumentException -> "Email atau password tidak boleh kosong."
        e.javaClass.simpleName.contains("GetCredential") -> "Login Google dibatalkan atau gagal."
        else -> e.message ?: "Terjadi kesalahan."
    }
}
