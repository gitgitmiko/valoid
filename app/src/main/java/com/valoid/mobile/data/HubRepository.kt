package com.valoid.mobile.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AnnouncementType(val label: String) {
    UPDATE("Update"),
    BUGFIX("Perbaikan"),
    INFO("Info"),
}

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val type: AnnouncementType,
    val version: String?,
    val publishedAtMillis: Long,
)

/** Info dari developer di announcements/{id}; hanya bisa ditulis admin (tools/post_announcement.py). */
class HubRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    fun announcements(limit: Long = 30): Flow<List<Announcement>> = callbackFlow {
        val registration = db.collection("announcements")
            .orderBy("publishedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        Announcement(
                            id = doc.id,
                            title = doc.getString("title") ?: return@mapNotNull null,
                            body = doc.getString("body").orEmpty(),
                            type = when (doc.getString("type")) {
                                "update" -> AnnouncementType.UPDATE
                                "bugfix" -> AnnouncementType.BUGFIX
                                else -> AnnouncementType.INFO
                            },
                            version = doc.getString("version"),
                            publishedAtMillis = doc.getTimestamp("publishedAt")?.toDate()?.time ?: 0,
                        )
                    },
                )
            }
        awaitClose { registration.remove() }
    }
}
