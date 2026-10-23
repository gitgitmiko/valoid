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

    suspend fun loadStorefront(session: RiotSession): Storefront = withContext(Dispatchers.IO) {
        val version = get(Request.Builder().url("https://valorant-api.com/v1/version").build())
            .jsonObject["data"]?.jsonObject?.get("riotClientVersion")?.jsonPrimitive?.contentOrNull
            ?: throw RiotException("Versi client Valorant tidak ditemukan")

        fun storefront(v3: Boolean) = Request.Builder()
            .url("https://pd.${session.region.shard}.a.pvp.net/store/${if (v3) "v3" else "v2"}/storefront/${session.puuid}")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("X-Riot-Entitlements-JWT", session.entitlementsToken)
            .header("X-Riot-ClientPlatform", CLIENT_PLATFORM)
            .header("X-Riot-ClientVersion", version)
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

        Storefront(
            daily = resolve(dailyRaw),
            dailyRemainingSeconds = panel["SingleItemOffersRemainingDurationInSeconds"]?.jsonPrimitive?.long ?: 0,
            nightMarket = resolve(nightRaw),
            nightMarketRemainingSeconds = bonus?.get("BonusStoreRemainingDurationInSeconds")?.jsonPrimitive?.long ?: 0,
        )
    }

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
        val CLIENT_PLATFORM: String = Base64.encodeToString(
            """{"platformType":"PC","platformOS":"Windows","platformOSVersion":"10.0.19042.1.256.64bit","platformChipset":"Unknown"}"""
                .toByteArray(),
            Base64.NO_WRAP,
        )
    }
}
