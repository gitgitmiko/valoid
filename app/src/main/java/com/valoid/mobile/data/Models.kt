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
)

data class HistoryDay(val date: String, val offers: List<SkinOffer>)

class RiotException(message: String, val sessionExpired: Boolean = false) : Exception(message)
