package com.valoid.mobile.data

enum class RiotRegion(val shard: String, val label: String) {
    NA("na", "Amerika"),
    EU("eu", "Eropa"),
    AP("ap", "Asia Pasifik"),
    KR("kr", "Korea"),
    ;

    companion object {
        /** Nama wilayah untuk kode shard ("ap" -> "Asia Pasifik"); kode tak dikenal ditampilkan apa adanya. */
        fun labelOf(shard: String): String = entries.firstOrNull { it.shard == shard }?.label ?: shard.uppercase()
    }
}

data class RiotSession(
    val accessToken: String,
    val entitlementsToken: String,
    val puuid: String,
    val region: RiotRegion,
)

data class SkinOffer(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val cost: Int,
    val discountedCost: Int? = null,
    val discountPercent: Int = 0,
)

data class Storefront(
    val daily: List<SkinOffer>,
    val dailyRemainingSeconds: Long,
    val nightMarket: List<SkinOffer>,
    val nightMarketRemainingSeconds: Long,
    val bundles: List<BundleOffer> = emptyList(),
    val accessories: List<AccessoryOffer> = emptyList(),
    val accessoryRemainingSeconds: Long = 0,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
) {
    val accessoryEndsAtMillis: Long get() = fetchedAtMillis + accessoryRemainingSeconds * 1000

    /** Tanggal (UTC) mulainya rotasi toko harian ini; toko berganti tiap 00:00 UTC. */
    val rotationDate: String
        get() = java.time.Instant.ofEpochMilli(dailyEndsAtMillis - 86_400_000 + 60_000)
            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

    fun endsAtMillis(bundle: BundleOffer): Long = fetchedAtMillis + bundle.remainingSeconds * 1000

    val dailyEndsAtMillis: Long get() = fetchedAtMillis + dailyRemainingSeconds * 1000
    val nightMarketEndsAtMillis: Long get() = fetchedAtMillis + nightMarketRemainingSeconds * 1000
}

data class SkinTier(val name: String, val iconUrl: String?, val color: Long?)

data class SkinChroma(val name: String, val swatchUrl: String?, val imageUrl: String?, val videoUrl: String?)

data class SkinLevel(val number: Int, val feature: String?, val videoUrl: String?)

data class SkinDetail(
    val levelId: String,
    val name: String,
    val imageUrl: String?,
    val tier: SkinTier?,
    val chromas: List<SkinChroma>,
    val levels: List<SkinLevel>,
)

data class HistoryDay(val date: String, val offers: List<SkinOffer>)

class RiotException(message: String, val sessionExpired: Boolean = false) : Exception(message)

/** Saldo akun Riot. */
data class Wallet(val valorantPoints: Int, val kingdomCredits: Int, val radianite: Int)

enum class ItemType(val label: String) {
    SKIN("Skin"),
    BUDDY("Gantungan"),
    SPRAY("Spray"),
    CARD("Kartu"),
    TITLE("Title"),
    FLEX("Flex"),
    OTHER("Item"),
}

data class BundleItem(
    val type: ItemType,
    val id: String,
    val name: String,
    val iconUrl: String?,
    val amount: Int,
    val price: Int,
    val basePrice: Int,
)

data class BundleOffer(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val price: Int,
    val basePrice: Int,
    val remainingSeconds: Long,
    val items: List<BundleItem>,
) {
    val discountPercent: Int get() = if (basePrice > price && basePrice > 0) (basePrice - price) * 100 / basePrice else 0
}

/** Item di toko aksesori, dibayar dengan Kingdom Credits. */
data class AccessoryOffer(
    val id: String,
    val type: ItemType,
    val name: String,
    val iconUrl: String?,
    val amount: Int,
    val cost: Int,
)

/** Skin favorit; [id] adalah UUID level pertama skin (sama dengan OfferID toko), huruf kecil. */
data class FavoriteSkin(val id: String, val name: String, val iconUrl: String?)

/** Ringkasan skin dari katalog untuk pencarian. */
data class SkinSummary(val id: String, val name: String, val iconUrl: String?)

/** Statistik kemunculan skin di toko pengguna, dihitung dari riwayatnya sendiri. */
data class SkinStats(
    val personalCount: Int,
    val personalDays: Int,
    val lastSeenByYou: String?,
)

/** Rank kompetitif pada satu act. [tier] 0 berarti belum ber-rank. */
data class RankInfo(
    val tier: Int,
    val tierName: String,
    val iconUrl: String?,
    val color: Long?,
    val rankedRating: Int?,
    val seasonLabel: String?,
)

data class PlayerProfile(
    val gameName: String?,
    val tagLine: String?,
    val level: Int?,
    val title: String?,
    val cardWideArt: String?,
    val cardSmallArt: String?,
    val currentRank: RankInfo?,
    val peakRank: RankInfo?,
)

enum class MatchResult { WIN, LOSS, DRAW, UNKNOWN }

data class MatchPlayer(
    val puuid: String,
    val name: String,
    val tag: String?,
    val teamId: String,
    val agentName: String?,
    val agentIcon: String?,
    val tierIcon: String?,
    val tierName: String?,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    /** Skor tempur rata-rata per ronde. */
    val acs: Int,
    val isMe: Boolean,
    val level: Int? = null,
    /** Persentase tembakan kena yang mengenai kepala; null bila tidak ada data damage. */
    val headshotPercent: Int? = null,
) {
    val kd: Double get() = if (deaths == 0) kills.toDouble() else kills.toDouble() / deaths
}

data class TeamScore(val teamId: String, val won: Boolean, val roundsWon: Int)

data class Match(
    val id: String,
    val queue: String,
    val mapName: String,
    val mapImage: String?,
    val startedAtMillis: Long,
    val lengthMillis: Long,
    val result: MatchResult,
    /** Peringkat pemain berdasarkan skor, untuk mode tanpa tim (Deathmatch). */
    val placement: Int?,
    val myRounds: Int?,
    val enemyRounds: Int?,
    val me: MatchPlayer?,
    /** Perubahan RR; hanya untuk match kompetitif. */
    val rrChange: Int?,
    val teams: List<TeamScore>,
    val players: List<MatchPlayer>,
    val mapSplash: String? = null,
    val rounds: List<RoundInfo> = emptyList(),
    /** Jumlah kill per pasangan "pembunuh|korban" (PUUID huruf kecil). */
    val killCounts: Map<String, Int> = emptyMap(),
) {
    fun kills(killer: MatchPlayer, victim: MatchPlayer): Int =
        killCounts["${killer.puuid.lowercase()}|${victim.puuid.lowercase()}"] ?: 0
}

enum class RoundOutcome(val label: String) {
    ELIMINATION("Eliminasi"),
    DEFUSE("Spike dijinakkan"),
    DETONATE("Spike meledak"),
    TIME("Waktu habis"),
    SURRENDER("Menyerah"),
    OTHER("Selesai"),
}

data class RoundInfo(
    val number: Int,
    val winningTeam: String,
    val outcome: RoundOutcome,
    val planter: String?,
    val defuser: String?,
    val myKills: Int,
    val mySpent: Int?,
    val myLoadoutValue: Int?,
    /** Kill di ronde ini sesuai urutan, untuk kill feed. */
    val kills: List<RoundKill> = emptyList(),
)

data class RoundKill(val killerTeam: String, val killerAgentIcon: String?, val killerName: String)

data class ContractReward(val name: String, val iconUrl: String?, val amount: Int)

/** Progres satu kontrak (Battle Pass atau kontrak agent). [levelReached] = jumlah level yang sudah dibuka. */
data class ContractProgress(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val levelReached: Int,
    val totalLevels: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int?,
    val nextReward: ContractReward?,
)

enum class MissionType(val label: String) { DAILY("Harian"), WEEKLY("Mingguan"), OTHER("Lainnya") }

data class Mission(
    val id: String,
    val title: String,
    val type: MissionType,
    val progress: Int,
    val target: Int,
    val xp: Int,
    val complete: Boolean,
    val expiresAtMillis: Long?,
)

data class ContractsOverview(
    val battlePass: ContractProgress?,
    val agentContract: ContractProgress?,
    val missions: List<Mission>,
    val weeklyRefillMillis: Long?,
    /** Kontrak agent yang sedang berjalan (yang aktif lebih dulu). */
    val agentContracts: List<ContractProgress> = emptyList(),
    /** Misi mingguan yang sudah rilis tapi belum diterima karena misi minggu ini belum selesai. */
    val queuedWeeklies: List<MissionPreview> = emptyList(),
    val futureWeeklies: List<MissionPreview> = emptyList(),
    val account: AccountXp? = null,
)

data class MissionPreview(val title: String, val target: Int, val xp: Int, val activatesAtMillis: Long?)

data class XpSource(val id: String, val amount: Int) {
    val label: String
        get() = when (id) {
            "time-played" -> "Waktu bermain"
            "match-win" -> "Menang match"
            "first-win-of-the-day" -> "Kemenangan pertama"
            else -> id.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
}

data class XpMatch(val startedAtMillis: Long, val total: Int, val sources: List<XpSource>)

data class AccountXp(
    val level: Int,
    val xp: Int,
    val xpPerLevel: Int,
    /** True bila bonus kemenangan pertama hari ini sudah didapat. */
    val firstWinClaimed: Boolean,
    val firstWinAvailableAtMillis: Long?,
    val recent: List<XpMatch>,
)

/** Item koleksi (kartu, spray, title, charm). */
data class CollectionItem(val id: String, val name: String, val imageUrl: String?, val equipped: Boolean)

data class EquippedWeapon(
    val weaponName: String,
    /** UUID level skin yang dipakai, untuk membuka detail skin. */
    val skinLevelId: String?,
    val skinName: String,
    val imageUrl: String?,
    val buddyName: String?,
    val buddyIcon: String?,
)

data class PlayerCollection(
    val cardName: String?,
    val cardWideArt: String?,
    val title: String?,
    /** Spray terpasang sesuai slot; null bila slot kosong. */
    val equippedSprays: List<CollectionItem?>,
    val weapons: List<EquippedWeapon>,
    val ownedSkins: Int,
    val cards: List<CollectionItem>,
    val sprays: List<CollectionItem>,
    val titles: List<CollectionItem>,
    val charms: List<CollectionItem>,
    val charmsTotal: Int,
)

data class ActStats(
    val seasonId: String,
    val label: String,
    val startMillis: Long,
    val games: Int,
    val wins: Int,
    /** Rank akhir di act ini; hanya untuk mode kompetitif. */
    val rank: RankInfo?,
) {
    val winRate: Double get() = if (games > 0) wins * 100.0 / games else 0.0
}

data class QueueCareer(val queueId: String, val name: String, val acts: List<ActStats>) {
    val games: Int get() = acts.sumOf { it.games }
    val wins: Int get() = acts.sumOf { it.wins }
}

data class CareerSummary(val currentActId: String?, val currentActLabel: String?, val queues: List<QueueCareer>)
