package com.valoid.mobile.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/** Membaca statistik global kemunculan skin (stats/global, skinStats/{id}). */
class StatsRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    suspend fun totalShops(): Int =
        db.collection("stats").document("global").get().await().getLong("shops")?.toInt() ?: 0

    /** Jumlah kemunculan per skin (ID huruf kecil); skin yang belum pernah tercatat tidak ada di peta. */
    suspend fun counts(ids: Collection<String>): Map<String, Int> = coroutineScope {
        ids.map { it.lowercase() }.distinct().map { id ->
            async {
                id to (db.collection("skinStats").document(id).get().await().getLong("count")?.toInt() ?: 0)
            }
        }.awaitAll().filter { it.second > 0 }.toMap()
    }
}
