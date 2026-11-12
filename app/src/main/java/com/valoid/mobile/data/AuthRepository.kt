package com.valoid.mobile.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Langkah login Google yang tidak selesai dalam batas waktu; [stage] membedakan pemilih akun dan Firebase. */
class GoogleSignInTimeoutException(val stage: String) : Exception("Login Google tidak merespons ($stage)")

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun userChanges(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** True bila akun masuk dengan email dan password (verifikasi ulang memakai password, bukan Google). */
    val usesPassword: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    suspend fun signInWithGoogle(activityContext: Context, webClientId: String) {
        val credential = googleCredential(activityContext, webClientId)
        withTimeoutOrNull(FIREBASE_TIMEOUT_MS) { auth.signInWithCredential(credential).await() }
            ?: throw GoogleSignInTimeoutException("firebase")
    }

    /** Verifikasi ulang sebelum operasi sensitif; Firebase menolak hapus akun jika login sudah lama. */
    suspend fun reauthenticate(activityContext: Context, webClientId: String, password: String?) {
        val user = auth.currentUser ?: error("Belum masuk")
        val credential = if (usesPassword) {
            EmailAuthProvider.getCredential(user.email.orEmpty(), password.orEmpty())
        } else {
            googleCredential(activityContext, webClientId)
        }
        user.reauthenticate(credential).await()
    }

    suspend fun deleteUser() {
        auth.currentUser?.delete()?.await()
    }

    private suspend fun googleCredential(activityContext: Context, webClientId: String): AuthCredential {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        // Pemilih akun bisa menggantung tanpa error di sebagian perangkat; batasi agar tombol tidak loading selamanya.
        val credential = withTimeoutOrNull(PICKER_TIMEOUT_MS) {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        }?.credential ?: throw GoogleSignInTimeoutException("pemilih akun")
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            error("Kredensial Google tidak dikenali")
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }

    fun signOut() = auth.signOut()

    private companion object {
        const val PICKER_TIMEOUT_MS = 90_000L
        const val FIREBASE_TIMEOUT_MS = 30_000L
    }
}
