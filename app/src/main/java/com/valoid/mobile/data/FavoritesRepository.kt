package com.valoid.mobile.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Skin favorit per pengguna: users/{uid}/favorites/{skinLevelId}. */
class FavoritesRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun collection(uid: String) = db.collection("users").document(uid).collection("favorites")

    fun favorites(uid: String): Flow<List<FavoriteSkin>> = callbackFlow {
        val registration = collection(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) trySend(snapshot.documents.mapNotNull { it.toFavorite() })
        }
        awaitClose { registration.remove() }
    }

    suspend fun load(uid: String): List<FavoriteSkin> =
        collection(uid).get().await().documents.mapNotNull { it.toFavorite() }

    suspend fun add(uid: String, skin: FavoriteSkin) {
        collection(uid).document(skin.id)
            .set(mapOf("name" to skin.name, "iconUrl" to skin.iconUrl, "addedAt" to Timestamp.now()))
            .await()
    }

    suspend fun remove(uid: String, id: String) {
        collection(uid).document(id).delete().await()
    }

    private fun DocumentSnapshot.toFavorite() =
        getString("name")?.let { FavoriteSkin(id, it, getString("iconUrl")) }

    suspend fun deleteAll(uid: String) {
        val docs = collection(uid).get().await().documents
        docs.chunked(400).forEach { page ->
            db.batch().apply { page.forEach { delete(it.reference) } }.commit().await()
        }
    }
}
