package com.valoid.mobile.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.valoid.mobile.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/** Persetujuan iklan (UMP), inisialisasi AdMob, dan iklan rewarded untuk membuka toko harian. */
object AdsManager {
    private val started = AtomicBoolean(false)
    private var rewarded: RewardedAd? = null
    private var loading = false

    /** Meminta persetujuan bila diwajibkan (mis. pengguna EEA/UK), lalu menyiapkan AdMob. */
    fun initialize(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { start(activity) }
            },
            { start(activity) },
        )
        if (consent.canRequestAds()) start(activity)
    }

    private fun start(context: Context) {
        val consent = UserMessagingPlatform.getConsentInformation(context)
        if (!consent.canRequestAds() || !started.compareAndSet(false, true)) return
        MobileAds.initialize(context.applicationContext) { preload(context.applicationContext) }
    }

    fun preload(context: Context, onResult: (Boolean) -> Unit = {}) {
        if (rewarded != null) return onResult(true)
        if (loading) return
        loading = true
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_REWARDED_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading = false
                    rewarded = ad
                    onResult(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    onResult(false)
                }
            },
        )
    }

    /**
     * Menampilkan iklan rewarded. [onRewarded] dipanggil hanya bila pengguna menonton sampai selesai;
     * [onFailed] bila iklan tidak tersedia atau gagal ditampilkan.
     */
    fun showRewarded(activity: Activity, onRewarded: () -> Unit, onFailed: (String) -> Unit) {
        val ad = rewarded
        if (ad == null) {
            preload(activity) { ok -> if (ok) showRewarded(activity, onRewarded, onFailed) else onFailed("Iklan belum tersedia.") }
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preload(activity)
                if (earned) onRewarded() else onFailed("Tonton iklan sampai selesai untuk membuka toko.")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload(activity)
                onFailed("Iklan gagal ditampilkan.")
            }
        }
        ad.show(activity) { earned = true }
    }
}

/**
 * Sesi akses toko harian bagi pengguna gratis: terbuka selama [SESSION_MILLIS] setelah menonton
 * iklan, dan hanya untuk rotasi toko saat itu. Penandanya disimpan di folder cache, jadi
 * "Hapus cache" di pengaturan Android juga mengakhiri sesi.
 */
object StoreUnlock {
    const val SESSION_MILLIS = 60 * 60 * 1000L
    private const val FILE = "store_unlock"

    private fun file(context: Context) = java.io.File(context.cacheDir, FILE)

    /** Waktu berakhirnya sesi untuk [rotationDate], atau null bila tidak ada sesi aktif. */
    fun expiresAt(context: Context, rotationDate: String, now: Long = System.currentTimeMillis()): Long? {
        val (rotation, expires) = runCatching { file(context).readText().trim().split("|") }.getOrNull()
            ?.takeIf { it.size == 2 } ?: return null
        val end = expires.toLongOrNull() ?: return null
        // Batas atas melindungi dari jam perangkat yang dimundurkan setelah membuka toko.
        return end.takeIf { rotation == rotationDate && it > now && it - now <= SESSION_MILLIS }
    }

    fun isUnlocked(context: Context, rotationDate: String): Boolean = expiresAt(context, rotationDate) != null

    fun unlock(context: Context, rotationDate: String, durationMillis: Long = SESSION_MILLIS) {
        runCatching { file(context).writeText("$rotationDate|${System.currentTimeMillis() + durationMillis}") }
    }
}
