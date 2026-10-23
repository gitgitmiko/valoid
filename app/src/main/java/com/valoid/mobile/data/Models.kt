package com.valoid.mobile.data

enum class RiotRegion(val shard: String, val label: String) {
    NA("na", "North America"),
    EU("eu", "Europe"),
    AP("ap", "Asia Pacific"),
    KR("kr", "Korea"),
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
    val fetchedAtMillis: Long = System.currentTimeMillis(),
) {
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
