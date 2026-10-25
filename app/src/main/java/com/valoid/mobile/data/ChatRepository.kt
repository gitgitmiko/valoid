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

/** Room rank: per wilayah dan kelompok rank, karena Riot membatasi party lintas wilayah dan selisih rank. */
data class RankRoom(val id: String, val title: String)

object RankRooms {
    fun groupOf(tier: Int): Pair<String, String> = when {
        tier < 3 -> "unranked" to "Unranked"
        tier <= 8 -> "iron-bronze" to "Iron–Bronze"
        tier <= 14 -> "silver-gold" to "Silver–Gold"
        tier <= 20 -> "plat-diamond" to "Platinum–Diamond"
        else -> "ascendant-plus" to "Ascendant+"
    }

    private val groups = listOf("unranked", "iron-bronze", "silver-gold", "plat-diamond", "ascendant-plus")

    /** Nama room untuk tampilan: "global" -> "Room semua user", "ap-plat-diamond" -> "Asia Pasifik · Platinum–Diamond". */
    fun titleOf(roomId: String): String {
        if (roomId == "global") return "Room semua user"
        val group = groups.firstOrNull { roomId.endsWith("-$it") } ?: return roomId
        val tier = when (group) { "unranked" -> 0; "iron-bronze" -> 3; "silver-gold" -> 9; "plat-diamond" -> 15; else -> 21 }
        return "${RiotRegion.labelOf(roomId.removeSuffix("-$group"))} · ${groupOf(tier).second}"
    }

    fun forIdentity(identity: ChatIdentity): RankRoom {
        val (group, label) = groupOf(identity.tier)
        return RankRoom("${identity.region}-$group", "${RiotRegion.labelOf(identity.region)} · $label")
    }
}

enum class PartySize(val players: Int, val label: String) {
    DUO(2, "Duo"),
    TRIO(3, "Trio"),
    FIVE(5, "Full 5"),
}

enum class PartyMode(val id: String, val label: String) {
    COMPETITIVE("competitive", "Kompetitif"),
    UNRATED("unrated", "Unrated"),
    SWIFTPLAY("swiftplay", "Swiftplay"),
    PREMIER("premier", "Premier"),
    CUSTOM("custom", "Custom / scrim"),
}

/** Ajakan party di room: mencari [need] pemain lagi untuk party [size]. */
data class PartyPost(val size: PartySize, val mode: PartyMode, val need: Int, val note: String)

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
    val party: PartyPost? = null,
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
                            party = (doc.get("party") as? Map<*, *>)?.let(::parseParty),
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    private fun parseParty(m: Map<*, *>): PartyPost? {
        val size = PartySize.entries.firstOrNull { it.players == (m["size"] as? Number)?.toInt() } ?: return null
        val mode = PartyMode.entries.firstOrNull { it.id == m["mode"] } ?: return null
        return PartyPost(size, mode, (m["need"] as? Number)?.toInt() ?: 1, m["note"] as? String ?: "")
    }

    suspend fun send(room: String, identity: ChatIdentity, text: String, party: PartyPost? = null) {
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
        ) + (party?.let { mapOf("party" to mapOf("size" to it.size.players, "mode" to it.mode.id, "need" to it.need, "note" to it.note)) } ?: emptyMap())
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
