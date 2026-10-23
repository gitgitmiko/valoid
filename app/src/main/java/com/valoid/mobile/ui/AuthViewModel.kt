package com.valoid.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.valoid.mobile.data.AuthRepository
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

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {

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
            else -> "Autentikasi gagal (${e.errorCode})."
        }
        e is IllegalArgumentException -> "Email tidak boleh kosong."
        e.javaClass.simpleName.contains("GetCredential") -> "Login Google dibatalkan atau gagal."
        else -> e.message ?: "Terjadi kesalahan."
    }
}
