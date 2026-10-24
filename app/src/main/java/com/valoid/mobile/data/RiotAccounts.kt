package com.valoid.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Akun Riot yang ditautkan; [cookies] adalah header Cookie sesi Riot untuk login ulang otomatis. */
@Serializable
data class RiotAccount(
    val puuid: String,
    val cookies: String,
    val name: String? = null,
    val tag: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val label: String get() = name?.let { n -> tag?.let { "$n #$it" } ?: n } ?: "Akun Riot"
}

/**
 * Daftar akun Riot di perangkat, disimpan terenkripsi (kunci di Android Keystore).
 * Cookie sesi tidak pernah dikirim ke server Valoid.
 */
object RiotAccounts {
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_ACTIVE = "active"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var prefs: SharedPreferences

    private val _accounts = MutableStateFlow<List<RiotAccount>>(emptyList())
    val accounts: StateFlow<List<RiotAccount>> = _accounts

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = runCatching {
            EncryptedSharedPreferences.create(
                context,
                "riot_accounts",
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Keystore rusak di sebagian kecil perangkat: tetap di penyimpanan privat aplikasi.
            context.getSharedPreferences("riot_accounts_plain", Context.MODE_PRIVATE)
        }
        _accounts.value = prefs.getString(KEY_ACCOUNTS, null)
            ?.let { runCatching { json.decodeFromString<List<RiotAccount>>(it) }.getOrNull() }
            .orEmpty()
        _activeId.value = prefs.getString(KEY_ACTIVE, null)?.takeIf { id -> _accounts.value.any { it.puuid == id } }
            ?: _accounts.value.firstOrNull()?.puuid
    }

    fun active(): RiotAccount? = _accounts.value.firstOrNull { it.puuid == _activeId.value }

    /** Akun yang paling awal ditautkan; riwayat toko versi lama dianggap miliknya. */
    fun oldest(): RiotAccount? = _accounts.value.minByOrNull { it.addedAt }

    fun upsert(account: RiotAccount) {
        val existing = _accounts.value.firstOrNull { it.puuid == account.puuid }
        val merged = account.copy(
            name = account.name ?: existing?.name,
            tag = account.tag ?: existing?.tag,
            addedAt = existing?.addedAt ?: account.addedAt,
        )
        save(_accounts.value.filterNot { it.puuid == account.puuid } + merged)
    }

    fun update(puuid: String, change: (RiotAccount) -> RiotAccount) {
        save(_accounts.value.map { if (it.puuid == puuid) change(it) else it })
    }

    fun setActive(puuid: String?) {
        _activeId.value = puuid
        prefs.edit().putString(KEY_ACTIVE, puuid).apply()
    }

    fun remove(puuid: String) {
        save(_accounts.value.filterNot { it.puuid == puuid })
        if (_activeId.value == puuid) setActive(_accounts.value.firstOrNull()?.puuid)
    }

    fun clear() {
        save(emptyList())
        setActive(null)
    }

    private fun save(list: List<RiotAccount>) {
        _accounts.value = list.sortedBy { it.addedAt }
        prefs.edit().putString(KEY_ACCOUNTS, json.encodeToString(_accounts.value)).apply()
    }
}
