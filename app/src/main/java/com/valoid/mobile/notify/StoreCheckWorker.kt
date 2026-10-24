package com.valoid.mobile.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.valoid.mobile.billing.ProManager
import com.valoid.mobile.data.FavoritesRepository
import com.valoid.mobile.data.HistoryRepository
import com.valoid.mobile.data.RiotException
import com.valoid.mobile.data.RiotRegion
import com.valoid.mobile.data.RiotRepository
import java.util.concurrent.TimeUnit

/**
 * Mengecek toko di latar belakang: login Riot ulang memakai cookie sesi di perangkat, menyimpan
 * riwayat, dan memberi notifikasi bila ada skin favorit di toko harian atau Night Market.
 */
class StoreCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val riot = RiotRepository()
        val tokens = runCatching { riot.reauthenticate() }.getOrNull() ?: return Result.success()
        return try {
            val session = riot.createSession(tokens.accessToken, tokens.idToken, RiotRegion.AP)
            val front = riot.loadStorefront(session)
            runCatching { HistoryRepository().saveDay(uid, front.rotationDate, front.daily) }

            // Notifikasi skin favorit hanya untuk pengguna Valoid Pro.
            if (!ProManager.isProCached(applicationContext)) return Result.success()
            val favoriteIds = FavoritesRepository().load(uid).map { it.id }.toSet()
            val matches = (front.daily + front.nightMarket)
                .filter { it.id.lowercase() in favoriteIds }
                .distinctBy { it.id.lowercase() }
            StoreNotifications.notifyFavorites(applicationContext, matches, rotationKey = front.dailyEndsAtMillis / 3_600_000)
            Result.success()
        } catch (e: RiotException) {
            if (e.sessionExpired) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "store-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StoreCheckWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
