package com.valoid.mobile.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

enum class ReportReason(val id: String, val label: String) {
    SPAM("spam", "Spam atau iklan"),
    HARASSMENT("harassment", "Pelecehan atau ujaran kebencian"),
    SEXUAL("sexual", "Konten seksual"),
    SCAM("scam", "Penipuan atau jual-beli akun"),
    CHEAT("cheat", "Cheat atau joki"),
    OTHER("other", "Lainnya"),
}

data class BlockedUser(val uid: String, val name: String, val tag: String)

data class Report(
    val id: String,
    val room: String,
    val messageId: String,
    val targetUid: String,
    val targetName: String,
    val text: String,
    val reason: ReportReason,
    val detail: String,
    val createdAtMillis: Long,
)

/**
 * Laporan (reports), blokir per pengguna (users/{uid}/blocked), ban dari chat (bans/{uid}), dan peran admin
 * (admins/{uid}, hanya diisi lewat konsol). Aturan Firestore menegakkan siapa boleh melakukan apa.
 */
class ModerationRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    suspend fun report(room: String, message: ChatMessage, reason: ReportReason, detail: String) {
        val me = uid ?: return
        db.collection("reports").add(
            mapOf(
                "reporterUid" to me,
                "room" to room,
                "messageId" to message.id,
                "targetUid" to message.uid,
                "targetName" to message.riotId,
                "text" to message.text.take(500),
                "reason" to reason.id,
                "detail" to detail.take(300),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    fun blocked(uid: String): Flow<List<BlockedUser>> = callbackFlow {
        val registration = db.collection("users").document(uid).collection("blocked").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(snapshot.documents.map { BlockedUser(it.id, it.getString("name").orEmpty(), it.getString("tag").orEmpty()) })
            }
        }
        awaitClose { registration.remove() }
    }

    suspend fun block(message: ChatMessage) {
        val me = uid ?: return
        db.collection("users").document(me).collection("blocked").document(message.uid)
            .set(mapOf("name" to message.name, "tag" to message.tag, "createdAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun unblock(target: String) {
        val me = uid ?: return
        db.collection("users").document(me).collection("blocked").document(target).delete().await()
    }

    /** True bila akun [uid] adalah admin. */
    fun isAdmin(uid: String): Flow<Boolean> = exists(db.collection("admins").document(uid))

    /** True bila akun [uid] di-ban dari chat. */
    fun isBanned(uid: String): Flow<Boolean> = exists(db.collection("bans").document(uid))

    private fun exists(ref: com.google.firebase.firestore.DocumentReference): Flow<Boolean> = callbackFlow {
        val registration = ref.addSnapshotListener { snapshot, _ -> trySend(snapshot?.exists() == true) }
        awaitClose { registration.remove() }
    }

    // Tindakan admin.

    fun reports(): Flow<List<Report>> = callbackFlow {
        val registration = db.collection("reports").orderBy("createdAt", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.map { d ->
                        Report(
                            id = d.id,
                            room = d.getString("room").orEmpty(),
                            messageId = d.getString("messageId").orEmpty(),
                            targetUid = d.getString("targetUid").orEmpty(),
                            targetName = d.getString("targetName").orEmpty(),
                            text = d.getString("text").orEmpty(),
                            reason = ReportReason.entries.firstOrNull { it.id == d.getString("reason") } ?: ReportReason.OTHER,
                            detail = d.getString("detail").orEmpty(),
                            createdAtMillis = d.getTimestamp("createdAt")?.toDate()?.time ?: 0,
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    /** Menghapus pesan beserta semua laporannya. */
    suspend fun deleteMessage(room: String, messageId: String) {
        val reports = db.collection("reports").whereEqualTo("messageId", messageId).get().await().documents
        db.batch().apply {
            delete(db.collection("rooms").document(room).collection("messages").document(messageId))
            reports.forEach { delete(it.reference) }
        }.commit().await()
    }

    suspend fun dismissReports(ids: List<String>) {
        db.batch().apply { ids.forEach { delete(db.collection("reports").document(it)) } }.commit().await()
    }

    suspend fun ban(targetUid: String, name: String, reason: String) {
        db.collection("bans").document(targetUid)
            .set(mapOf("name" to name, "reason" to reason.take(200), "by" to uid, "createdAt" to FieldValue.serverTimestamp()))
            .await()
    }
}
