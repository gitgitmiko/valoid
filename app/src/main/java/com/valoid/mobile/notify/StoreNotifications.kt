package com.valoid.mobile.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.valoid.mobile.MainActivity
import com.valoid.mobile.R
import com.valoid.mobile.data.SkinOffer

object StoreNotifications {
    private const val CHANNEL_FAVORITES = "favorites"
    private const val PREFS = "notifications"
    private const val KEY_LAST = "last_favorites_key"
    private const val ID_FAVORITES = 1001

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Memberi tahu bahwa skin favorit ada di toko. Satu notifikasi per rotasi toko:
     * [rotationKey] yang sama dengan isi yang sama tidak dikirim ulang.
     */
    fun notifyFavorites(context: Context, matches: List<SkinOffer>, rotationKey: Long) {
        if (matches.isEmpty() || !canNotify(context)) return
        val key = "$rotationKey:" + matches.map { it.id }.sorted().joinToString(",")
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST, null) == key) return

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val names = matches.joinToString(", ") { it.name }
        val title = if (matches.size == 1) "Skin favoritmu ada di toko!" else "${matches.size} skin favoritmu ada di toko!"
        val notification = NotificationCompat.Builder(context, CHANNEL_FAVORITES)
            .setSmallIcon(R.drawable.ic_stat_favorite)
            .setContentTitle(title)
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        @Suppress("MissingPermission") // dicek lewat canNotify()
        NotificationManagerCompat.from(context).notify(ID_FAVORITES, notification)
        prefs.edit().putString(KEY_LAST, key).apply()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_FAVORITES) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FAVORITES, "Skin favorit di toko", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pemberitahuan saat skin favoritmu muncul di toko harian atau Night Market"
            },
        )
    }
}
