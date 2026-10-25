package com.valoid.mobile.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Menyimpan hasil toko harian ke Firestore: users/{uid}/accounts/{puuid}/shopHistory/{tanggal rotasi, yyyy-MM-dd UTC}.
 * Token Riot tidak pernah disimpan di sini, hanya daftar skin yang tampil.
 */
class HistoryRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun legacy(uid: String) =
        db.collection("users").document(uid).collection("shopHistory")

    private fun accounts(uid: String) = db.collection("users").document(uid).collection("accounts")

    private fun collection(uid: String, puuid: String) =
        accounts(uid).document(puuid.lowercase()).collection("shopHistory")

    /**
     * Mencatat toko harian untuk rotasi [date] sekali saja; riwayat tidak bisa diubah setelah dibuat.
     * Mengembalikan true bila rotasi baru tercatat.
     */
    suspend fun saveDay(uid: String, puuid: String, date: String, offers: List<SkinOffer>): Boolean {
        val accountRef = accounts(uid).document(puuid.lowercase())
        val dayRef = accountRef.collection("shopHistory").document(date)
        return db.runTransaction { tx ->
            if (tx.get(dayRef).exists()) return@runTransaction false
            // Penanda akun agar riwayatnya bisa ditemukan saat akun Valoid dihapus (klien tidak bisa mendaftar subkoleksi).
            if (!tx.get(accountRef).exists()) tx.set(accountRef, mapOf("puuid" to puuid.lowercase()))
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
            true
        }.await()
    }

    /** Riwayat akun [puuid]; [includeLegacy] menambahkan riwayat versi lama (sebelum dukungan banyak akun). */
    suspend fun loadHistory(uid: String, puuid: String, includeLegacy: Boolean, limit: Long = 60): List<HistoryDay> {
        val current = read(collection(uid, puuid), limit)
        val old = if (includeLegacy) runCatching { read(legacy(uid), limit) }.getOrDefault(emptyList()) else emptyList()
        return (current + old).distinctBy { it.date }.sortedByDescending { it.date }.take(limit.toInt())
    }

    /** Menghapus semua riwayat pengguna: akun bertanda di server, [knownPuuids] dari perangkat, dan riwayat versi lama. */
    suspend fun deleteAll(uid: String, knownPuuids: Collection<String>) {
        val markers = accounts(uid).get().await().documents
        val puuids = markers.map { it.id } + knownPuuids.map { it.lowercase() }
        puuids.distinct().forEach { deleteCollection(collection(uid, it)) }
        deleteCollection(legacy(uid))
        markers.forEach { it.reference.delete().await() }
    }

    private suspend fun deleteCollection(ref: com.google.firebase.firestore.CollectionReference) {
        while (true) {
            val page = ref.limit(400).get().await().documents
            if (page.isEmpty()) return
            db.batch().apply { page.forEach { delete(it.reference) } }.commit().await()
        }
    }

    private suspend fun read(ref: com.google.firebase.firestore.CollectionReference, limit: Long): List<HistoryDay> {
        val snapshot = ref
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
