package com.valoid.mobile.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Menyimpan hasil toko harian ke Firestore: users/{uid}/shopHistory/{tanggal rotasi, yyyy-MM-dd UTC}.
 * Token Riot tidak pernah disimpan di sini, hanya daftar skin yang tampil.
 */
class HistoryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun collection(uid: String) =
        db.collection("users").document(uid).collection("shopHistory")

    /**
     * Mencatat toko harian untuk rotasi [date] sekali saja. Pencatatan pertama juga menaikkan
     * statistik global (stats/global dan skinStats/{id}); rules Firestore memastikan penghitung
     * hanya naik bersamaan dengan riwayat baru. Mengembalikan true bila rotasi baru tercatat.
     */
    suspend fun saveDay(uid: String, date: String, offers: List<SkinOffer>): Boolean {
        val dayRef = collection(uid).document(date)
        return db.runTransaction { tx ->
            if (tx.get(dayRef).exists()) return@runTransaction false
            tx.set(
                dayRef,
                mapOf(
                    "date" to date,
                    "savedAt" to Timestamp.now(),
                    "offers" to offers.map {
                        mapOf("id" to it.id, "name" to it.name, "iconUrl" to it.iconUrl, "cost" to it.cost)
                    },
                ),
            )
            tx.set(
                db.collection("stats").document("global"),
                mapOf("shops" to FieldValue.increment(1), "lastDate" to date),
                SetOptions.merge(),
            )
            offers.distinctBy { it.id.lowercase() }.forEach {
                tx.set(
                    db.collection("skinStats").document(it.id.lowercase()),
                    mapOf("count" to FieldValue.increment(1), "name" to it.name, "iconUrl" to it.iconUrl, "lastSeen" to date),
                    SetOptions.merge(),
                )
            }
            true
        }.await()
    }

    suspend fun loadHistory(uid: String, limit: Long = 60): List<HistoryDay> {
        val snapshot = collection(uid)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val date = doc.getString("date") ?: return@mapNotNull null
            val raw = doc.get("offers") as? List<*> ?: return@mapNotNull null
            val offers = raw.mapNotNull { item ->
                val m = item as? Map<*, *> ?: return@mapNotNull null
                SkinOffer(
                    id = m["id"] as? String ?: return@mapNotNull null,
                    name = m["name"] as? String ?: "Skin",
                    iconUrl = m["iconUrl"] as? String,
                    cost = (m["cost"] as? Number)?.toInt() ?: 0,
                )
            }
            HistoryDay(date, offers)
        }
    }
}
