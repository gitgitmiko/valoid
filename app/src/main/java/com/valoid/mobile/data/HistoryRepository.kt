package com.valoid.mobile.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Menyimpan hasil toko harian ke Firestore: users/{uid}/shopHistory/{yyyy-MM-dd}.
 * Token Riot tidak pernah disimpan di sini, hanya daftar skin yang tampil.
 */
class HistoryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun collection(uid: String) =
        db.collection("users").document(uid).collection("shopHistory")

    suspend fun saveDay(uid: String, date: String, offers: List<SkinOffer>) {
        val data = mapOf(
            "date" to date,
            "savedAt" to Timestamp.now(),
            "offers" to offers.map {
                mapOf("id" to it.id, "name" to it.name, "iconUrl" to it.iconUrl, "cost" to it.cost)
            },
        )
        collection(uid).document(date).set(data).await()
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
