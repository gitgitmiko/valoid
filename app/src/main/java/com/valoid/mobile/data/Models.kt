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
    val bundles: List<BundleOffer> = emptyList(),
    val fetchedAtMillis: Long = System.currentTimeMillis(),
) {
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
