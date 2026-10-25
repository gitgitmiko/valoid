package com.valoid.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Mode Demo untuk peninjau Google Play: akun [EMAIL] memakai sesi Riot palsu yang datanya dilayani
 * [DemoInterceptor] dari assets/demo (dibuat oleh tools/make_demo_data.py), dan Valoid Pro terbuka.
 * Data konten (nama, gambar skin, map, agent) tetap diambil dari valorant-api.com.
 */
object DemoMode {
    const val EMAIL = "playreview@gitgitdev.my.id"
    const val PUUID = "6f1d2c4a-8b3e-4d5f-9a7c-0d3e0a1d0001"
    const val ACCESS_TOKEN = "valoid-demo"

    val account = RiotAccount(puuid = PUUID, cookies = "", name = "ValoidDemo", tag = "PLAY")

    val session = RiotSession(accessToken = ACCESS_TOKEN, entitlementsToken = ACCESS_TOKEN, puuid = PUUID, region = RiotRegion.AP)

    fun isDemo(email: String?) = email.equals(EMAIL, ignoreCase = true)

    internal lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

/** Menjawab request pd.*.a.pvp.net bertoken demo dari assets/demo; request lain diteruskan apa adanya. */
class DemoInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != "Bearer ${DemoMode.ACCESS_TOKEN}" || !request.url.host.endsWith(".a.pvp.net")) {
            return chain.proceed(request)
        }
        val path = request.url.encodedPath
        val body = when {
            path.contains("/storefront/") -> asset("storefront.json")
            path.contains("/wallet/") -> asset("wallet.json")
            path.contains("/entitlements/") -> asset("entitlements-${path.substringAfterLast('/')}.json")
            path.contains("/playerloadout") -> asset("loadout.json")
            path.contains("account-xp/") -> asset("account-xp.json")
            path.contains("/competitiveupdates") -> asset("competitiveupdates.json")
            path.startsWith("/mmr/") -> asset("mmr.json")
            path.startsWith("/match-history/") ->
                if (request.url.queryParameter("startIndex") == "0") asset("match-history.json") else """{"History":[]}"""
            path.startsWith("/match-details/") -> asset("match-${path.substringAfterLast('/')}.json")
            path.startsWith("/contracts/") -> asset("contracts.json")
            path.startsWith("/name-service/") -> names(request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() })
            else -> null
        }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (body != null) 200 else 404)
            .message(if (body != null) "OK" else "Not Found")
            .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun asset(name: String): String? =
        runCatching { DemoMode.appContext.assets.open("demo/$name").bufferedReader().use { it.readText() } }.getOrNull()?.let(::fillTimes)

    private fun names(body: String?): String {
        val known = Json.parseToJsonElement(asset("names.json") ?: "{}").jsonObject
        val ids = body?.let { runCatching { Json.parseToJsonElement(it).jsonArray.map { e -> e.jsonPrimitive.content } }.getOrNull() }.orEmpty()
        return ids.joinToString(",", "[", "]") { id ->
            val pair = known[id.lowercase()]?.jsonArray
            """{"Subject":"$id","GameName":"${pair?.get(0)?.jsonPrimitive?.content.orEmpty()}","TagLine":"${pair?.get(1)?.jsonPrimitive?.content.orEmpty()}"}"""
        }
    }

    /** Mengisi penanda waktu relatif dari tools/make_demo_data.py agar data selalu terlihat baru. */
    private fun fillTimes(text: String): String {
        val now = Instant.now()
        val midnight = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return PLACEHOLDER.replace(text) { m ->
            val n = m.groupValues[2].toLong()
            when (m.groupValues[1]) {
                "ms" -> now.plusSeconds(n).toEpochMilli().toString()
                "iso" -> "\"${now.plusSeconds(n)}\""
                "left" -> (midnight.epochSecond - now.epochSecond + n * 86_400).toString()
                else -> "\"${midnight.plusSeconds(n * 86_400)}\""
            }
        }
    }

    private companion object {
        val PLACEHOLDER = Regex("\"\\{\\{(ms|iso|left|isoday):(-?\\d+)\\}\\}\"")
    }
}
