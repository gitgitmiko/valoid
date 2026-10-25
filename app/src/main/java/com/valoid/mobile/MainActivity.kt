package com.valoid.mobile

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.valoid.mobile.ads.AdsManager
import com.valoid.mobile.billing.ProManager
import com.valoid.mobile.data.DemoMode
import com.valoid.mobile.data.RiotAccounts
import com.valoid.mobile.notify.StoreCheckWorker
import com.valoid.mobile.security.AppLock
import com.valoid.mobile.ui.LockScreen
import com.valoid.mobile.ui.Onboarding
import com.valoid.mobile.ui.OnboardingScreen
import com.valoid.mobile.ui.SplashScreen
import kotlinx.coroutines.delay
import com.valoid.mobile.ui.ValoidApp
import com.valoid.mobile.ui.ValoidTheme

// FragmentActivity diperlukan oleh BiometricPrompt untuk kunci aplikasi.
class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lockError by mutableStateOf<String?>(null)
    private var authenticating = false
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StoreCheckWorker.schedule(this)
        ProManager.init(this)
        RiotAccounts.init(this)
        DemoMode.init(this)
        locked = AppLock.isEnabled(this)
        setContent {
            ValoidTheme {
                // Aplikasi tetap dirender di bawah layar kunci agar keadaannya tidak hilang.
                var splash by remember { mutableStateOf(true) }
                var onboarded by remember { mutableStateOf(Onboarding.isDone(this)) }
                LaunchedEffect(Unit) {
                    delay(1200)
                    splash = false
                }
                when {
                    splash -> SplashScreen()
                    !onboarded -> OnboardingScreen(onFinish = { onboarded = true })
                    else -> Box {
                        ValoidApp()
                        if (locked) LockScreen(error = lockError, onUnlock = ::unlock, onLeave = { moveTaskToBack(true) })
                    }
                }
            }
        }
        // Formulir persetujuan iklan (UMP) butuh tampilan yang sudah terpasang; jalankan setelah setContent.
        window.decorView.post { AdsManager.initialize(this) }
    }

    override fun onStart() {
        super.onStart()
        ProManager.refresh()
        val away = SystemClock.elapsedRealtime() - backgroundedAt
        if (AppLock.isEnabled(this) && !authenticating && backgroundedAt > 0 && away > AppLock.RELOCK_AFTER_MILLIS) {
            locked = true
        }
    }

    override fun onStop() {
        super.onStop()
        // Layar kunci perangkat (PIN) di Android lama membuka activity lain; jangan dihitung keluar.
        if (!authenticating) backgroundedAt = SystemClock.elapsedRealtime()
    }

    private fun unlock() {
        if (authenticating) return
        authenticating = true
        lockError = null
        AppLock.authenticate(
            this,
            title = "Buka Valoid",
            onSuccess = {
                authenticating = false
                locked = false
            },
            onFailure = {
                authenticating = false
                lockError = it
            },
        )
    }
}
