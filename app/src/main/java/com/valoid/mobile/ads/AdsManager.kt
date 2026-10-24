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

/** Toko harian yang sudah dibuka dengan iklan berlaku sampai rotasi toko berganti. */
object StoreUnlock {
    private const val PREFS = "store_unlock"
    private const val KEY_ROTATION = "rotation"

    fun isUnlocked(context: Context, rotationDate: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ROTATION, null) == rotationDate

    fun unlock(context: Context, rotationDate: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ROTATION, rotationDate).apply()
    }
}
