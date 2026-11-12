package com.valoid.mobile.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.GetSignInIntentRequest
import com.google.android.gms.auth.api.identity.Identity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Activity transparan yang membuka pemilih akun Google milik Play Services lalu mengembalikan ID token.
 *
 * Credential Manager di Android 14+ memakai pemilih akun sistem, yang di sebagian ROM (mis. HyperOS)
 * tidak pernah tampil sehingga login menggantung. Jalur ini langsung lewat Play Services.
 */
class GoogleSignInActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val deferred = pending ?: return@registerForActivityResult finish()
        runCatching { Identity.getSignInClient(this).getSignInCredentialFromIntent(result.data).googleIdToken }
            .onSuccess { token ->
                if (token != null) deferred.complete(token) else deferred.completeExceptionally(IllegalStateException("ID token Google kosong"))
            }
            .onFailure { deferred.completeExceptionally(it) }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setelah dibuat ulang (mis. rotasi), hasil tetap dikirim ke launcher; jangan buka pemilih dua kali.
        if (savedInstanceState != null) return
        val intent = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_INTENT)
        }
        if (intent == null) {
            pending?.completeExceptionally(IllegalStateException("Intent login Google tidak ada"))
            finish()
            return
        }
        launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) pending?.completeExceptionally(GoogleSignInCancelledException())
    }

    companion object {
        private const val EXTRA_INTENT = "intent"
        private const val PREPARE_TIMEOUT_MS = 30_000L
        private var pending: CompletableDeferred<String>? = null

        /** Menampilkan pemilih akun Google dan mengembalikan ID token untuk [webClientId]. */
        suspend fun requestIdToken(context: Context, webClientId: String): String {
            val request = GetSignInIntentRequest.builder().setServerClientId(webClientId).build()
            // Batas waktu hanya untuk menyiapkan pemilih; setelah tampil, pengguna boleh memilih selama perlu.
            val signInIntent = withTimeoutOrNull(PREPARE_TIMEOUT_MS) {
                Identity.getSignInClient(context).getSignInIntent(request).await()
            } ?: throw GoogleSignInTimeoutException("pemilih akun")
            val deferred = CompletableDeferred<String>()
            pending?.completeExceptionally(GoogleSignInCancelledException())
            pending = deferred
            context.startActivity(
                Intent(context, GoogleSignInActivity::class.java)
                    .putExtra(EXTRA_INTENT, signInIntent)
                    .apply { if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
            return try {
                deferred.await()
            } finally {
                if (pending === deferred) pending = null
            }
        }
    }
}

class GoogleSignInCancelledException : Exception("Login Google dibatalkan")
