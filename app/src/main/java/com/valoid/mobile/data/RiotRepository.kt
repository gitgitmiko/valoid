package com.valoid.mobile.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/**
 * Klien untuk endpoint Riot. Catatan: endpoint toko tidak didokumentasikan secara resmi
 * oleh Riot dan dapat berubah sewaktu-waktu.
 */
class RiotRepository(private val client: OkHttpClient = OkHttpClient()) {

    private val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /**
     * Meminta token baru memakai cookie sesi Riot yang tersimpan. Mengembalikan null bila cookie
     * tidak ada atau sudah tidak berlaku (Riot mengarahkan ke halaman login).
     */
    suspend fun reauthenticate(): RiotAuth.Tokens? = withContext(Dispatchers.IO) {
        val cookies = RiotAuth.cookieHeader()?.takeIf { it.contains("ssid=") } ?: return@withContext null
        noRedirects.newCall(
            Request.Builder().url(RiotAuth.AUTH_URL).header("Cookie", cookies).build(),
        ).execute().use { response ->
            RiotAuth.storeCookies(response.headers("Set-Cookie"))
            RiotAuth.parseRedirect(response.header("Location"))
        }
    }

    private data class SkinInfo(val name: String, val iconUrl: String?)

    private val skinCache = ConcurrentHashMap<String, SkinInfo>()

    /**
     * Region akun dibaca dari layanan geo Riot memakai id_token; pilihan manual hanya dipakai
     * bila id_token tidak tersedia atau layanan geo gagal.
     */
    suspend fun createSession(accessToken: String, idToken: String?, fallback: RiotRegion): RiotSession =
        withContext(Dispatchers.IO) {
            val entitlements = get(
                Request.Builder()
                    .url("https://entitlements.auth.riotgames.com/api/token/v1")
                    .header("Authorization", "Bearer $accessToken")
                    .post("{}".toRequestBody(JSON))
                    .build(),
            ).jsonObject["entitlements_token"]?.jsonPrimitive?.contentOrNull
                ?: throw RiotException("Entitlements token tidak ditemukan")

            val puuid = get(
                Request.Builder()
                    .url("https://auth.riotgames.com/userinfo")
                    .header("Authorization", "Bearer $accessToken")
                    .build(),
            ).jsonObject["sub"]?.jsonPrimitive?.contentOrNull
                ?: throw RiotException("PUUID tidak ditemukan")

            val region = idToken?.let { runCatching { detectRegion(accessToken, it) }.getOrNull() } ?: fallback
            RiotSession(accessToken, entitlements, puuid, region)
        }

    private fun detectRegion(accessToken: String, idToken: String): RiotRegion? {
        val body = """{"id_token":"$idToken"}""".toRequestBody(JSON)
        val live = get(
            Request.Builder()
                .url("https://riot-geo.pas.si.riotgames.com/pas/v1/product/valorant")
                .header("Authorization", "Bearer $accessToken")
                .put(body)
                .build(),
        ).jsonObject["affinities"]?.jsonObject?.get("live")?.jsonPrimitive?.contentOrNull ?: return null
        val shard = when (live) {
            "latam", "br" -> "na"
            else -> live
        }
        return RiotRegion.entries.firstOrNull { it.shard == shard }
    }

    @Volatile
    private var cachedClientVersion: String? = null

    private fun clientVersion(): String = cachedClientVersion
        ?: (
            get(Request.Builder().url("https://valorant-api.com/v1/version").build())
                .jsonObject["data"]?.jsonObject?.get("riotClientVersion")?.jsonPrimitive?.contentOrNull
                ?: throw RiotException("Versi client Valorant tidak ditemukan")
            ).also { cachedClientVersion = it }

    /** Request ke server data pemain (pd.<shard>.a.pvp.net) dengan header autentikasi Riot. */
    private fun pd(session: RiotSession, path: String) = Request.Builder()
        .url("https://pd.${session.region.shard}.a.pvp.net/$path")
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("X-Riot-Entitlements-JWT", session.entitlementsToken)
        .header("X-Riot-ClientPlatform", CLIENT_PLATFORM)
        .header("X-Riot-ClientVersion", clientVersion())

    suspend fun loadWallet(session: RiotSession): Wallet = withContext(Dispatchers.IO) {
        val balances = get(pd(session, "store/v1/wallet/${session.puuid}").build())
            .jsonObject["Balances"]?.jsonObject
            ?: throw RiotException("Data dompet tidak ditemukan")
        fun balance(currency: String) = balances[currency]?.jsonPrimitive?.intOrNull ?: 0
        Wallet(
            valorantPoints = balance(CURRENCY_VP),
            kingdomCredits = balance(CURRENCY_KINGDOM),
            radianite = balance(CURRENCY_RADIANITE),
        )
    }

    suspend fun loadStorefront(session: RiotSession): Storefront = withContext(Dispatchers.IO) {
        fun storefront(v3: Boolean) = pd(session, "store/${if (v3) "v3" else "v2"}/storefront/${session.puuid}")
            .apply { if (v3) post("{}".toRequestBody(JSON)) }
            .build()

        // v3 (POST) dipakai klien Riot saat ini; v2 (GET) sebagai cadangan.
        val root = try {
            get(storefront(v3 = true))
        } catch (e: RiotException) {
            if (e.sessionExpired) throw e
            get(storefront(v3 = false))
        }.jsonObject

        val panel = root["SkinsPanelLayout"]?.jsonObject
            ?: throw RiotException("Data toko tidak ditemukan")
        val dailyRaw = panel["SingleItemStoreOffers"]?.jsonArray?.map { it.jsonObject }
            ?.map { RawOffer(it["OfferID"]!!.jsonPrimitive.content, firstCost(it["Cost"]), null, 0) }
            ?: panel["SingleItemOffers"]?.jsonArray?.map { RawOffer(it.jsonPrimitive.content, 0, null, 0) }
            ?: emptyList()

        val bonus = root["BonusStore"]?.jsonObject
        val nightRaw = bonus?.get("BonusStoreOffers")?.jsonArray?.map { entry ->
            val o = entry.jsonObject
            val offer = o["Offer"]!!.jsonObject
            RawOffer(
                id = offer["OfferID"]!!.jsonPrimitive.content,
                cost = firstCost(offer["Cost"]),
                discounted = firstCostOrNull(o["DiscountCosts"]),
                percent = o["DiscountPercent"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        } ?: emptyList()

        val featured = root["FeaturedBundle"]?.jsonObject
        val featuredRemaining = featured?.get("BundleRemainingDurationInSeconds")?.jsonPrimitive?.longOrNull ?: 0
        val bundlesJson = featured?.get("Bundles")?.jsonArray?.map { it.jsonObject }
            ?: listOfNotNull(featured?.get("Bundle")?.jsonObject)

        val accessoryStore = root["AccessoryStore"]?.jsonObject

        Storefront(
            daily = resolve(dailyRaw),
            dailyRemainingSeconds = panel["SingleItemOffersRemainingDurationInSeconds"]?.jsonPrimitive?.long ?: 0,
            nightMarket = resolve(nightRaw),
            nightMarketRemainingSeconds = bonus?.get("BonusStoreRemainingDurationInSeconds")?.jsonPrimitive?.long ?: 0,
            bundles = coroutineScope {
                bundlesJson.map { async { runCatching { parseBundle(it, featuredRemaining) }.getOrNull() } }.awaitAll().filterNotNull()
            },
            accessories = accessoryStore?.let { runCatching { parseAccessories(it) }.getOrNull() }.orEmpty(),
            accessoryRemainingSeconds = accessoryStore?.get("AccessoryStoreRemainingDurationInSeconds")?.jsonPrimitive?.longOrNull ?: 0,
        )
    }

    /** Mengubah satu entri `FeaturedBundle.Bundles` dari Riot menjadi [BundleOffer]. */
    internal suspend fun parseBundle(b: JsonObject, fallbackRemaining: Long): BundleOffer = withContext(Dispatchers.IO) {
        val id = (b["DataAssetID"] ?: b["ID"])!!.jsonPrimitive.content
        val items = b["Items"]?.jsonArray.orEmpty().map { entry ->
            val o = entry.jsonObject
            val item = o["Item"]!!.jsonObject
            async {
                val type = ITEM_TYPES[item["ItemTypeID"]?.jsonPrimitive?.contentOrNull] ?: ItemType.OTHER
                val itemId = item["ItemID"]!!.jsonPrimitive.content
                val (name, icon) = itemInfo(type, itemId)
                BundleItem(
                    type = type,
                    id = itemId,
                    name = name,
                    iconUrl = icon,
                    amount = item["Amount"]?.jsonPrimitive?.intOrNull ?: 1,
                    price = o["DiscountedPrice"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                    basePrice = o["BasePrice"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                )
            }
        }.awaitAll()
        val info = apiData("bundles/$id")
        BundleOffer(
            id = id,
            name = info?.str("displayName") ?: "Bundle",
            imageUrl = info?.str("displayIcon"),
            price = firstCostOrNull(b["TotalDiscountedCost"]) ?: items.sumOf { it.price },
            basePrice = firstCostOrNull(b["TotalBaseCost"]) ?: items.sumOf { it.basePrice },
            remainingSeconds = b["DurationRemainingInSeconds"]?.jsonPrimitive?.longOrNull ?: fallbackRemaining,
            items = items,
        )
    }

    /** Mengubah `AccessoryStore` dari Riot menjadi daftar [AccessoryOffer]. */
    internal suspend fun parseAccessories(store: JsonObject): List<AccessoryOffer> = withContext(Dispatchers.IO) {
        store["AccessoryStoreOffers"]?.jsonArray.orEmpty().map { entry ->
            async {
                val offer = entry.jsonObject["Offer"]!!.jsonObject
                val reward = offer["Rewards"]?.jsonArray?.firstOrNull()?.jsonObject
                val type = ITEM_TYPES[reward?.str("ItemTypeID")] ?: ItemType.OTHER
                val itemId = reward?.str("ItemID") ?: offer.str("OfferID")!!
                val (name, icon) = itemInfo(type, itemId)
                AccessoryOffer(
                    id = offer.str("OfferID") ?: itemId,
                    type = type,
                    name = name,
                    iconUrl = icon,
                    amount = reward?.get("Quantity")?.jsonPrimitive?.intOrNull ?: 1,
                    cost = firstCost(offer["Cost"]),
                )
            }
        }.awaitAll()
    }

    private fun itemInfo(type: ItemType, id: String): Pair<String, String?> {
        val data = when (type) {
            ItemType.SKIN -> apiData("weapons/skinlevels/$id")
            ItemType.BUDDY -> apiData("buddies/levels/$id")
            ItemType.SPRAY -> apiData("sprays/$id")
            ItemType.CARD -> apiData("playercards/$id")
            ItemType.TITLE -> apiData("playertitles/$id")
            ItemType.FLEX -> apiData("flex/$id")
            ItemType.OTHER -> null
        }
        val name = (if (type == ItemType.TITLE) data?.str("titleText") else null) ?: data?.str("displayName") ?: type.label
        val icon = when (type) {
            ItemType.SPRAY -> data?.str("fullTransparentIcon") ?: data?.str("displayIcon")
            ItemType.CARD -> data?.str("largeArt") ?: data?.str("displayIcon")
            else -> data?.str("displayIcon")
        }
        return name to icon
    }

    private val apiCache = ConcurrentHashMap<String, JsonObject>()

    /** Objek `data` dari valorant-api.com/v1/<path>, di-cache; null bila tidak ditemukan. */
    private fun apiData(path: String): JsonObject? = apiCache[path] ?: runCatching {
        get(Request.Builder().url("https://valorant-api.com/v1/$path").build()).jsonObject["data"]?.jsonObject
    }.getOrNull()?.also { apiCache[path] = it }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private class RawOffer(val id: String, val cost: Int, val discounted: Int?, val percent: Int)

    private fun firstCostOrNull(costs: kotlinx.serialization.json.JsonElement?): Int? =
        (costs as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.int

    private fun firstCost(costs: kotlinx.serialization.json.JsonElement?): Int = firstCostOrNull(costs) ?: 0

    private suspend fun resolve(raw: List<RawOffer>): List<SkinOffer> = coroutineScope {
        raw.map { r ->
            async {
                val info = skinInfo(r.id)
                SkinOffer(r.id, info.name, info.iconUrl, r.cost, r.discounted, r.percent)
            }
        }.awaitAll()
    }

    private fun skinInfo(levelId: String): SkinInfo = skinCache.getOrPut(levelId) {
        runCatching {
            val data = get(Request.Builder().url("https://valorant-api.com/v1/weapons/skinlevels/$levelId").build())
                .jsonObject["data"]?.jsonObject
            SkinInfo(
                name = data?.get("displayName")?.jsonPrimitive?.contentOrNull ?: "Skin",
                iconUrl = data?.get("displayIcon")?.jsonPrimitive?.contentOrNull,
            )
        }.getOrElse { SkinInfo("Skin", null) }
    }

    private fun get(request: Request) = client.newCall(request).execute().use { response ->
        if (response.code == 401 || response.code == 403) {
            throw RiotException("Sesi Riot kedaluwarsa, silakan login lagi", sessionExpired = true)
        }
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val code = runCatching {
                Json.parseToJsonElement(text).jsonObject["errorCode"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            throw RiotException("Permintaan gagal (${response.code}${code?.let { " $it" } ?: ""}) ${request.url.encodedPath}")
        }
        Json.parseToJsonElement(text)
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val CURRENCY_VP = "85ad13f7-3d1b-5128-9eb2-7cd8ee0b5741"
        const val CURRENCY_KINGDOM = "85ca954a-41f2-ce94-9b45-8ca3dd39a00d"
        const val CURRENCY_RADIANITE = "e59aa87c-4cbf-517a-5983-6e81511be9b7"
        val ITEM_TYPES = mapOf(
            "e7c63390-eda7-46e0-bb7a-a6abdacd2433" to ItemType.SKIN,
            "dd3bf334-87f3-40bd-b043-682a57a8dc3a" to ItemType.BUDDY,
            "d5f120f8-ff8c-4aac-92ea-f2b5acbe9475" to ItemType.SPRAY,
            "3f296c07-64c3-494c-923b-fe692a4fa1bd" to ItemType.CARD,
            "de7caa6b-adf7-4588-bbd1-143831e786c6" to ItemType.TITLE,
            "03a572de-4234-31ed-d344-ababa488f981" to ItemType.FLEX,
        )
        val CLIENT_PLATFORM: String = Base64.encodeToString(
            """{"platformType":"PC","platformOS":"Windows","platformOSVersion":"10.0.19042.1.256.64bit","platformChipset":"Unknown"}"""
                .toByteArray(),
            Base64.NO_WRAP,
        )
    }
}
