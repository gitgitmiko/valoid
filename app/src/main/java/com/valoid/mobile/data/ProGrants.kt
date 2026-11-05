package com.valoid.mobile.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Valoid Pro gratis per akun (proGrants/{uid}), diberikan developer lewat tools/grant_pro.py. */
object ProGrants {
    /** True bila akun [uid] mendapat Pro gratis. Saat offline atau gagal dibaca, tidak mengirim apa pun. */
    fun isGranted(uid: String): Flow<Boolean> = callbackFlow {
        val registration = FirebaseFirestore.getInstance().collection("proGrants").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) trySend(snapshot.exists())
            }
        awaitClose { registration.remove() }
    }
}
