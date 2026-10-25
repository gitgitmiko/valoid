package com.valoid.mobile.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Identitas pengirim di chat, dari akun Riot yang aktif. */
data class ChatIdentity(
    val name: String,
    val tag: String,
    val tier: Int,
    val tierName: String?,
    val rankIcon: String?,
    val cardArt: String?,
    val region: String,
)

data class ChatMessage(
    val id: String,
    val uid: String,
    val name: String,
    val tag: String,
    val tierName: String?,
    val rankIcon: String?,
    val cardArt: String?,
    val region: String?,
    val text: String,
    val createdAtMillis: Long,
) {
    val riotId: String get() = "$name#$tag"
}

class ChatException(message: String) : Exception(message)

/**
 * Pesan room di Firestore rooms/{room}/messages. Setiap pesan ditulis bersama chatUsers/{uid}.lastMessageAt
 * dalam satu batch; aturan Firestore memakai waktu itu untuk membatasi kecepatan kirim.
 */
class ChatRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun messages(room: String, limit: Long = 80): Flow<List<ChatMessage>> = callbackFlow {
        val registration = db.collection("rooms").document(room).collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        ChatMessage(
                            id = doc.id,
                            uid = doc.getString("uid") ?: return@mapNotNull null,
                            name = doc.getString("name") ?: return@mapNotNull null,
                            tag = doc.getString("tag").orEmpty(),
                            tierName = doc.getString("tierName"),
                            rankIcon = doc.getString("rankIcon"),
                            cardArt = doc.getString("cardArt"),
                            region = doc.getString("region"),
                            text = doc.getString("text") ?: return@mapNotNull null,
                            // Pesan sendiri yang belum dikonfirmasi server belum punya waktu.
                            createdAtMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    suspend fun send(room: String, identity: ChatIdentity, text: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw ChatException("Belum masuk")
        val profile = mapOf(
            "name" to identity.name,
            "tag" to identity.tag,
            "tier" to identity.tier,
            "tierName" to identity.tierName,
            "rankIcon" to identity.rankIcon,
            "cardArt" to identity.cardArt,
            "region" to identity.region,
        )
        val message = profile.filterKeys { it != "tier" } + mapOf(
            "uid" to uid,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        try {
            db.batch()
                .set(db.collection("chatUsers").document(uid), profile + ("lastMessageAt" to FieldValue.serverTimestamp()), SetOptions.merge())
                .set(db.collection("rooms").document(room).collection("messages").document(), message)
                .commit()
                .await()
        } catch (e: FirebaseFirestoreException) {
            throw ChatException(
                if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "Terlalu cepat. Tunggu beberapa detik lalu kirim lagi." else "Pesan gagal terkirim.",
            )
        }
    }
}

/**
 * Status online di Realtime Database: online/{uid} selama aplikasi terbuka dan roomOnline/{room}/{uid}
 * selama room dibuka. Keduanya dihapus server saat koneksi terputus.
 */
object Presence {
    private const val URL = "https://valoid-d04fc-default-rtdb.asia-southeast1.firebasedatabase.app"
    private val db by lazy { FirebaseDatabase.getInstance(URL) }

    fun goOnline(uid: String) {
        db.goOnline()
        val ref = db.getReference("online").child(uid)
        ref.onDisconnect().removeValue()
        ref.setValue(ServerValue.TIMESTAMP)
    }

    fun goOffline(uid: String) {
        db.getReference("online").child(uid).removeValue()
    }

    fun joinRoom(room: String, uid: String) {
        val ref = db.getReference("roomOnline").child(room).child(uid)
        ref.onDisconnect().removeValue()
        ref.setValue(ServerValue.TIMESTAMP)
    }

    fun leaveRoom(room: String, uid: String) {
        db.getReference("roomOnline").child(room).child(uid).removeValue()
    }

    /** Jumlah user yang sedang membuka aplikasi. */
    fun onlineCount(): Flow<Int> = count(db.getReference("online"))

    /** Jumlah user yang sedang membuka [room]. */
    fun roomCount(room: String): Flow<Int> = count(db.getReference("roomOnline").child(room))

    private fun count(ref: com.google.firebase.database.DatabaseReference): Flow<Int> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.childrenCount.toInt())
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
