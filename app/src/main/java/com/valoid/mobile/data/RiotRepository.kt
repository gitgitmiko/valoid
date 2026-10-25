package com.valoid.mobile.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
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
class RiotRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(DemoInterceptor()).build(),
) {

    private val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    class Reauth(val tokens: RiotAuth.Tokens, val cookies: String)

    /**
     * Meminta token baru memakai cookie sesi Riot [cookies]. Mengembalikan token beserta cookie yang
     * sudah diperbarui, atau null bila sesi tidak berlaku lagi (Riot mengarahkan ke halaman login).
     */
    suspend fun reauthenticate(cookies: String): Reauth? {
        if (!cookies.contains("ssid=")) return null
        val (setCookies, location) = withContext(Dispatchers.IO) {
            noRedirects.newCall(
                Request.Builder().url(RiotAuth.AUTH_URL).header("Cookie", cookies).build(),
            ).execute().use { it.headers("Set-Cookie") to it.header("Location") }
        }
        val tokens = RiotAuth.parseRedirect(location) ?: return null
        return Reauth(tokens, RiotAuth.mergeCookies(cookies, setCookies))
    }

    /** Cookie sesi lama yang masih tersimpan di WebView (Valoid sebelum dukungan banyak akun). */
    suspend fun legacyWebViewCookies(): String? =
        withContext(Dispatchers.Main) { RiotAuth.cookieHeader() }?.takeIf { it.contains("ssid=") }

    /** Riot ID (nama, tag) pemilik sesi. */
    suspend fun riotId(session: RiotSession): Pair<String, String?>? = withContext(Dispatchers.IO) {
        runCatching { playerNames(session, listOf(session.puuid))[session.puuid.lowercase()] }.getOrNull()
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

    /** Profil pemain: nama#tag, level, kartu, title, rank act terakhir, dan peak rank. Bagian yang gagal dibiarkan kosong. */
    suspend fun loadProfile(session: RiotSession): PlayerProfile = withContext(Dispatchers.IO) {
        coroutineScope {
            val name = async {
                runCatching {
                    get(
                        pd(session, "name-service/v2/players")
                            .put("[\"${session.puuid}\"]".toRequestBody(JSON))
                            .build(),
                    ).jsonArray.first().jsonObject
                }.getOrNull()
            }
            val level = async {
                runCatching {
                    get(pd(session, "account-xp/v1/players/${session.puuid}").build())
                        .jsonObject["Progress"]?.jsonObject?.get("Level")?.jsonPrimitive?.intOrNull
                }.getOrNull()
            }
            val identity = async {
                runCatching {
                    val loadout = try {
                        get(pd(session, "personalization/v2/players/${session.puuid}/playerloadout").build())
                    } catch (e: RiotException) {
                        if (e.sessionExpired) throw e
                        get(pd(session, "personalization/v3/players/${session.puuid}/playerloadout").build())
                    }
                    loadout.jsonObject["Identity"]?.jsonObject
                }.getOrNull()
            }
            val ranks = async {
                runCatching { parseRanks(get(pd(session, "mmr/v1/players/${session.puuid}").build()).jsonObject) }.getOrNull()
            }

            val card = identity.await()?.str("PlayerCardID")?.let { apiData("playercards/$it") }
            val title = identity.await()?.str("PlayerTitleID")?.let { apiData("playertitles/$it") }?.str("titleText")
            val (current, peak) = ranks.await() ?: (null to null)
            PlayerProfile(
                gameName = name.await()?.str("GameName"),
                tagLine = name.await()?.str("TagLine"),
                level = level.await() ?: identity.await()?.get("AccountLevel")?.jsonPrimitive?.intOrNull,
                title = title,
                cardWideArt = card?.str("wideArt"),
                cardSmallArt = card?.str("smallArt") ?: card?.str("displayIcon"),
                currentRank = current?.let { rankInfo(it) },
                peakRank = peak?.let { rankInfo(it) },
            )
        }
    }

    private val matchCache = ConcurrentHashMap<String, Match>()

    /** [count] match terbaru mulai dari [start], lengkap dengan detail dan perubahan RR. */
    suspend fun loadMatches(session: RiotSession, start: Int, count: Int): List<Match> = withContext(Dispatchers.IO) {
        val history = get(
            pd(session, "match-history/v1/history/${session.puuid}?startIndex=$start&endIndex=${start + count}").build(),
        ).jsonObject["History"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.str("MatchID") }

        val rrByMatch = runCatching {
            get(
                pd(session, "mmr/v1/players/${session.puuid}/competitiveupdates?startIndex=0&endIndex=${start + count + 10}&queue=competitive").build(),
            ).jsonObject["Matches"]?.jsonArray.orEmpty().associate { m ->
                val o = m.jsonObject
                o.str("MatchID").orEmpty() to o["RankedRatingEarned"]?.jsonPrimitive?.intOrNull
            }
        }.getOrDefault(emptyMap())

        // Dibatasi beberapa sekaligus agar tidak terkena rate limit Riot.
        val gate = kotlinx.coroutines.sync.Semaphore(4)
        val raw = coroutineScope {
            history.filterNot { matchCache.containsKey("${session.puuid}:$it") }.map { id ->
                async {
                    gate.withPermit {
                        runCatching { id to get(pd(session, "match-details/v1/matches/$id").build()).jsonObject }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }

        // match-details sering tidak menyertakan nama; ambil sekaligus lewat name-service.
        val unnamed = raw.values.flatMap { m ->
            m["players"]?.jsonArray.orEmpty().map { it.jsonObject }
                .filter { it.str("gameName").isNullOrBlank() }
                .mapNotNull { it.str("subject") }
        }.distinct()
        val names = runCatching { playerNames(session, unnamed) }.getOrDefault(emptyMap())

        history.mapNotNull { id ->
            matchCache["${session.puuid}:$id"] ?: raw[id]?.let { json ->
                runCatching { parseMatch(json, session.puuid, rrByMatch[id], names) }.getOrNull()
            }?.also {
                // Jangan simpan hasil yang data map-nya gagal dimuat dari valorant-api.
                if (it.mapImage != null) matchCache["${session.puuid}:$id"] = it
            }
        }
    }

    /** Riot ID (nama, tag) untuk daftar PUUID, dikunci PUUID huruf kecil. */
    private fun playerNames(session: RiotSession, puuids: List<String>): Map<String, Pair<String, String?>> =
        puuids.chunked(50).flatMap { chunk ->
            val body = chunk.joinToString(",", "[", "]") { "\"$it\"" }
            get(pd(session, "name-service/v2/players").put(body.toRequestBody(JSON)).build()).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                val id = o.str("Subject") ?: return@mapNotNull null
                val name = o.str("GameName")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                id.lowercase() to (name to o.str("TagLine"))
            }
        }.toMap()

    /** Mengubah respons `match-details` menjadi [Match] dari sudut pandang pemain [puuid]. */
    internal fun parseMatch(
        json: JsonObject,
        puuid: String,
        rrChange: Int?,
        names: Map<String, Pair<String, String?>> = emptyMap(),
    ): Match {
        val info = json["matchInfo"]!!.jsonObject
        val seasonId = info.str("seasonId")
        val map = apiList("maps").map { it.jsonObject }.firstOrNull { it.str("mapUrl").equals(info.str("mapId"), ignoreCase = true) }
        val agents = apiList("agents?isPlayableCharacter=true").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }

        val roundsJson = (json["roundResults"] as? JsonArray).orEmpty().map { it.jsonObject }
        val roundStats = roundsJson.flatMap { (it["playerStats"] as? JsonArray).orEmpty().map { ps -> ps.jsonObject } }

        // Headshot per penembak, dari semua event damage di semua ronde.
        val shots = HashMap<String, IntArray>() // [kepala, badan, kaki]
        roundStats.forEach { ps ->
            val shooter = ps.str("subject")?.lowercase() ?: return@forEach
            (ps["damage"] as? JsonArray).orEmpty().forEach { d ->
                val o = d.jsonObject
                val acc = shots.getOrPut(shooter) { IntArray(3) }
                acc[0] += o["headshots"]?.jsonPrimitive?.intOrNull ?: 0
                acc[1] += o["bodyshots"]?.jsonPrimitive?.intOrNull ?: 0
                acc[2] += o["legshots"]?.jsonPrimitive?.intOrNull ?: 0
            }
        }
        // Semua kill (pembunuh, korban); format lama ada per ronde, format baru di level atas.
        val killPairs = (roundStats.flatMap { (it["kills"] as? JsonArray).orEmpty() }
            .ifEmpty { (json["kills"] as? JsonArray).orEmpty() })
            .map { it.jsonObject }
            .mapNotNull { k -> k.str("killer")?.lowercase()?.let { killer -> killer to k.str("victim")?.lowercase() } }

        val players = json["players"]?.jsonArray.orEmpty().map { it.jsonObject }.map { p ->
            val stats = p["stats"] as? JsonObject
            val rounds = stats?.get("roundsPlayed")?.jsonPrimitive?.intOrNull ?: 0
            val score = stats?.get("score")?.jsonPrimitive?.intOrNull ?: 0
            val agent = agents[p.str("characterId")?.lowercase()]
            val tier = p["competitiveTier"]?.jsonPrimitive?.intOrNull ?: 0
            val rank = if (tier > 2) rankInfo(RawRank(tier, null, seasonId)) else null
            MatchPlayer(
                puuid = p.str("subject").orEmpty(),
                name = p.str("gameName")?.takeIf { it.isNotBlank() } ?: names[p.str("subject")?.lowercase()]?.first ?: "Pemain",
                tag = p.str("tagLine")?.takeIf { it.isNotBlank() } ?: names[p.str("subject")?.lowercase()]?.second,
                teamId = p.str("teamId").orEmpty(),
                agentName = agent?.str("displayName"),
                agentIcon = agent?.str("displayIconSmall") ?: agent?.str("displayIcon"),
                tierIcon = rank?.iconUrl,
                tierName = rank?.tierName,
                kills = stats?.get("kills")?.jsonPrimitive?.intOrNull ?: 0,
                deaths = stats?.get("deaths")?.jsonPrimitive?.intOrNull ?: 0,
                assists = stats?.get("assists")?.jsonPrimitive?.intOrNull ?: 0,
                acs = if (rounds > 0) score / rounds else 0,
                isMe = p.str("subject").equals(puuid, ignoreCase = true),
                level = p["accountLevel"]?.jsonPrimitive?.intOrNull,
                headshotPercent = shots[p.str("subject")?.lowercase()]?.let { (head, body, leg) ->
                    val total = head + body + leg
                    if (total > 0) head * 100 / total else null
                },
            )
        }.sortedByDescending { it.acs }

        val teams = (json["teams"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
            TeamScore(
                teamId = it.str("teamId").orEmpty(),
                won = it["won"]?.jsonPrimitive?.booleanOrNull ?: false,
                roundsWon = it["roundsWon"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        val teamMode = teams.size == 2
        // Tanpa tim (Deathmatch) pemenang ditentukan oleh jumlah kill, bukan ACS.
        val ordered = if (teamMode) players else players.sortedByDescending { it.kills }
        val me = ordered.firstOrNull { it.isMe }
        val myTeam = teams.firstOrNull { it.teamId == me?.teamId }
        val enemy = teams.firstOrNull { it.teamId != me?.teamId }
        val result = when {
            !teamMode || myTeam == null || enemy == null -> MatchResult.UNKNOWN
            myTeam.roundsWon == enemy.roundsWon && !myTeam.won && !enemy.won -> MatchResult.DRAW
            myTeam.won -> MatchResult.WIN
            else -> MatchResult.LOSS
        }
        return Match(
            id = info.str("matchId").orEmpty(),
            queue = queueName(info.str("queueID"), info.str("provisioningFlowID")),
            mapName = map?.str("displayName") ?: "Map",
            mapImage = map?.str("listViewIcon"),
            startedAtMillis = info["gameStartMillis"]?.jsonPrimitive?.longOrNull ?: 0,
            lengthMillis = info["gameLengthMillis"]?.jsonPrimitive?.longOrNull ?: 0,
            result = result,
            placement = if (teamMode || me == null) null else ordered.indexOf(me) + 1,
            myRounds = if (teamMode) myTeam?.roundsWon else null,
            enemyRounds = if (teamMode) enemy?.roundsWon else null,
            me = me,
            rrChange = rrChange,
            teams = teams,
            players = ordered,
            mapSplash = map?.str("splash"),
            rounds = roundsJson.mapIndexed { i, r ->
                val mine = (r["playerStats"] as? JsonArray).orEmpty().map { it.jsonObject }
                    .firstOrNull { it.str("subject").equals(puuid, ignoreCase = true) }
                val economy = mine?.get("economy") as? JsonObject
                fun nameOf(id: String?) = id?.let { pid -> players.firstOrNull { it.puuid.equals(pid, ignoreCase = true) }?.name }
                RoundInfo(
                    number = (r["roundNum"]?.jsonPrimitive?.intOrNull ?: i) + 1,
                    winningTeam = r.str("winningTeam").orEmpty(),
                    outcome = roundOutcome(r.str("roundResultCode"), r.str("roundResult")),
                    planter = nameOf(r.str("bombPlanter")),
                    defuser = nameOf(r.str("bombDefuser")),
                    myKills = (mine?.get("kills") as? JsonArray)?.size ?: 0,
                    mySpent = economy?.get("spent")?.jsonPrimitive?.intOrNull,
                    myLoadoutValue = economy?.get("loadoutValue")?.jsonPrimitive?.intOrNull,
                    kills = roundKills(r, i, json).mapNotNull { killer ->
                        val k = players.firstOrNull { it.puuid.equals(killer, ignoreCase = true) } ?: return@mapNotNull null
                        RoundKill(k.teamId, k.agentIcon, k.name)
                    },
                )
            },
            killCounts = killPairs.groupingBy { (killer, victim) -> "$killer|$victim" }.eachCount(),
        )
    }

    /** PUUID pembunuh di ronde [index], urut waktu; format per ronde atau `kills` di level atas. */
    private fun roundKills(round: JsonObject, index: Int, match: JsonObject): List<String> {
        val perRound = (round["playerStats"] as? JsonArray).orEmpty().flatMap { ps ->
            (ps.jsonObject["kills"] as? JsonArray).orEmpty().map { it.jsonObject }
        }
        val kills = perRound.ifEmpty {
            (match["kills"] as? JsonArray).orEmpty().map { it.jsonObject }
                .filter { it["round"]?.jsonPrimitive?.intOrNull == index }
        }
        return kills.sortedBy { it["roundTime"]?.jsonPrimitive?.longOrNull ?: 0 }.mapNotNull { it.str("killer") }
    }

    private fun roundOutcome(code: String?, text: String?): RoundOutcome = when {
        code == "Elimination" || text == "Eliminated" -> RoundOutcome.ELIMINATION
        code == "Defuse" || text == "Bomb defused" -> RoundOutcome.DEFUSE
        code == "Detonate" || text == "Bomb detonated" -> RoundOutcome.DETONATE
        text == "Round timer expired" -> RoundOutcome.TIME
        code == "Surrendered" || text == "Surrendered" -> RoundOutcome.SURRENDER
        else -> RoundOutcome.OTHER
    }

    private fun queueName(queue: String?, flow: String?): String = when (queue) {
        "competitive" -> "Kompetitif"
        "unrated" -> "Unrated"
        "swiftplay" -> "Swiftplay"
        "spikerush" -> "Spike Rush"
        "deathmatch" -> "Deathmatch"
        "hurm" -> "Team Deathmatch"
        "ggteam" -> "Escalation"
        "onefa" -> "Replication"
        "premier" -> "Premier"
        "newmap" -> "Map baru"
        null, "" -> if (flow == "CustomGame") "Custom" else "Lainnya"
        else -> queue.replaceFirstChar { it.uppercase() }
    }

    suspend fun loadContracts(session: RiotSession): ContractsOverview = withContext(Dispatchers.IO) {
        coroutineScope {
            val xp = async { runCatching { parseAccountXp(get(pd(session, "account-xp/v1/players/${session.puuid}").build()).jsonObject) }.getOrNull() }
            val contracts = parseContracts(get(pd(session, "contracts/v1/contracts/${session.puuid}").build()).jsonObject)
            contracts.copy(account = xp.await())
        }
    }

    /** Level akun, XP, status kemenangan pertama, dan XP 10 match terakhir dari `account-xp`. */
    internal fun parseAccountXp(json: JsonObject, now: Long = System.currentTimeMillis()): AccountXp {
        val progress = json["Progress"] as? JsonObject
        val nextFirstWin = parseInstant(json.str("NextTimeFirstWinAvailable"))
        return AccountXp(
            level = progress?.get("Level")?.jsonPrimitive?.intOrNull ?: 0,
            xp = progress?.get("XP")?.jsonPrimitive?.intOrNull ?: 0,
            xpPerLevel = 5000,
            firstWinClaimed = nextFirstWin != null && nextFirstWin > now,
            firstWinAvailableAtMillis = nextFirstWin?.takeIf { it > now },
            recent = (json["History"] as? JsonArray).orEmpty().map { it.jsonObject }.map { h ->
                val sources = (h["XPSources"] as? JsonArray).orEmpty().mapNotNull { s ->
                    val o = s.jsonObject
                    val amount = o["Amount"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    XpSource(o.str("ID").orEmpty(), amount)
                }.filter { it.amount != 0 }
                XpMatch(
                    startedAtMillis = parseInstant(h.str("MatchStart")) ?: 0,
                    total = h["XPDelta"]?.jsonPrimitive?.intOrNull ?: sources.sumOf { it.amount },
                    sources = sources,
                )
            }.sortedByDescending { it.startedAtMillis }.take(10),
        )
    }

    /**
     * Dari respons `contracts/v1/contracts`: Battle Pass act yang sedang berjalan, kontrak agent yang
     * aktif, dan misi. Definisi kontrak dan misi diambil dari valorant-api.com.
     */
    internal fun parseContracts(json: JsonObject, now: Long = System.currentTimeMillis()): ContractsOverview {
        val defs = apiList("contracts").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        val progressById = json["Contracts"]?.jsonArray.orEmpty().map { it.jsonObject }
            .associateBy { it.str("ContractDefinitionID")?.lowercase() }

        fun progress(defId: String?): ContractProgress? {
            val def = defs[defId?.lowercase()] ?: return null
            val content = def["content"] as? JsonObject
            val levels = content?.get("chapters")?.jsonArray.orEmpty()
                .flatMap { (it.jsonObject["levels"] as? JsonArray).orEmpty() }
                .map { it.jsonObject }
            val p = progressById[defId?.lowercase()]
            val reached = (p?.get("ProgressionLevelReached")?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, levels.size)
            val next = levels.getOrNull(reached)
            val agentIcon = if (content?.str("relationType") == "Agent") {
                content.str("relationUuid")?.let { apiData("agents/$it") }?.str("displayIcon")
            } else {
                null
            }
            return ContractProgress(
                id = def.str("uuid").orEmpty(),
                name = def.str("displayName") ?: "Kontrak",
                iconUrl = agentIcon ?: def.str("displayIcon"),
                levelReached = reached,
                totalLevels = levels.size,
                xpIntoLevel = p?.get("ProgressionTowardsNextLevel")?.jsonPrimitive?.intOrNull ?: 0,
                xpForNextLevel = next?.get("xp")?.jsonPrimitive?.intOrNull,
                nextReward = (next?.get("reward") as? JsonObject)?.let(::reward),
            )
        }

        val currentAct = apiList("seasons").map { it.jsonObject }.firstOrNull { season ->
            season.str("type") == "EAresSeasonType::Act" &&
                (parseInstant(season.str("startTime")) ?: Long.MAX_VALUE) <= now &&
                now < (parseInstant(season.str("endTime")) ?: 0)
        }?.str("uuid")
        val battlePassId = defs.values.firstOrNull { def ->
            val content = def["content"] as? JsonObject
            content?.str("relationType") == "Season" && content.str("relationUuid").equals(currentAct, ignoreCase = true)
        }?.str("uuid")

        val missionDefs = apiList("missions").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        val missions = json["Missions"]?.jsonArray.orEmpty().map { it.jsonObject }.mapNotNull { m ->
            val id = m.str("ID") ?: return@mapNotNull null
            val def = missionDefs[id.lowercase()]
            val target = def?.get("progressToComplete")?.jsonPrimitive?.intOrNull
                ?: (def?.get("objectives") as? JsonArray)?.firstOrNull()?.jsonObject?.get("value")?.jsonPrimitive?.intOrNull
                ?: 1
            val progress = (m["Objectives"] as? JsonObject)?.values?.sumOf { it.jsonPrimitive.intOrNull ?: 0 } ?: 0
            Mission(
                id = id,
                title = def?.str("title") ?: def?.str("displayName") ?: "Misi",
                type = when (def?.str("type")) {
                    "EAresMissionType::Daily" -> MissionType.DAILY
                    "EAresMissionType::Weekly" -> MissionType.WEEKLY
                    else -> MissionType.OTHER
                },
                progress = progress.coerceAtMost(target),
                target = target,
                xp = def?.get("xpGrant")?.jsonPrimitive?.intOrNull ?: 0,
                complete = m["Complete"]?.jsonPrimitive?.booleanOrNull ?: (progress >= target),
                expiresAtMillis = parseInstant(m.str("ExpirationTime"))?.takeIf { it > now },
            )
        }.sortedWith(compareBy<Mission>({ it.complete }, { it.type.ordinal }))

        // Misi mingguan yang sudah dirilis setelah checkpoint terakhir pemain tapi belum diterima,
        // dan misi mingguan yang baru rilis nanti.
        val metadata = json["MissionMetadata"] as? JsonObject
        val checkpoint = parseInstant(metadata?.str("WeeklyCheckpoint"))
        val activeIds = missions.map { it.id.lowercase() }.toSet()
        val weeklyDefs = missionDefs.values.filter { it.str("type") == "EAresMissionType::Weekly" }
            .mapNotNull { d ->
                val start = parseInstant(d.str("activationDate")) ?: return@mapNotNull null
                val end = parseInstant(d.str("expirationDate"))
                if (end != null && end <= now) return@mapNotNull null
                Triple(d, start, d.str("uuid")?.lowercase())
            }
            .filter { it.third !in activeIds }
            .sortedBy { it.second }
        fun preview(d: JsonObject, start: Long) = MissionPreview(
            title = d.str("title") ?: "Misi",
            target = d["progressToComplete"]?.jsonPrimitive?.intOrNull ?: 1,
            xp = d["xpGrant"]?.jsonPrimitive?.intOrNull ?: 0,
            activatesAtMillis = start,
        )
        val queued = if (checkpoint == null) emptyList() else weeklyDefs.filter { it.second in (checkpoint + 1)..now }.map { preview(it.first, it.second) }
        val future = weeklyDefs.filter { it.second > now }.map { preview(it.first, it.second) }

        val activeAgent = json.str("ActiveSpecialContract")?.lowercase()
        val agentContracts = progressById.keys.filterNotNull().filter { id ->
            (defs[id]?.get("content") as? JsonObject)?.str("relationType") == "Agent"
        }.mapNotNull { progress(it) }
            .filter { it.id.lowercase() == activeAgent || ((it.levelReached > 0 || it.xpIntoLevel > 0) && it.levelReached < it.totalLevels) }
            .sortedByDescending { it.id.lowercase() == activeAgent }

        return ContractsOverview(
            battlePass = progress(battlePassId),
            agentContract = progress(activeAgent),
            missions = missions,
            weeklyRefillMillis = parseInstant(metadata?.str("WeeklyRefillTime"))?.takeIf { it > now },
            agentContracts = agentContracts,
            queuedWeeklies = queued,
            futureWeeklies = future,
        )
    }

    private fun reward(r: JsonObject): ContractReward {
        val id = r.str("uuid").orEmpty()
        val amount = r["amount"]?.jsonPrimitive?.intOrNull ?: 1
        val (path, iconKey) = when (r.str("type")) {
            "EquippableSkinLevel" -> "weapons/skinlevels/$id" to "displayIcon"
            "EquippableCharmLevel" -> "buddies/levels/$id" to "displayIcon"
            "Spray" -> "sprays/$id" to "fullTransparentIcon"
            "PlayerCard" -> "playercards/$id" to "largeArt"
            "Title" -> "playertitles/$id" to ""
            "Currency" -> "currencies/$id" to "displayIcon"
            "Character" -> "agents/$id" to "displayIcon"
            "Totem" -> "flex/$id" to "displayIcon"
            else -> null to ""
        }
        val data = path?.let { apiData(it) }
        val name = data?.str("titleText")
            ?: (if (amount == 1) data?.str("displayNameSingular") else null)
            ?: data?.str("displayName") ?: "Hadiah"
        return ContractReward(
            name = if (r.str("type") == "Currency") "${formatThousands(amount)} $name" else name,
            iconUrl = data?.str(iconKey) ?: data?.str("displayIcon"),
            amount = amount,
        )
    }

    private fun formatThousands(n: Int) = java.text.NumberFormat.getIntegerInstance(java.util.Locale("id", "ID")).format(n)

    /** ISO-8601 ke epoch millis; tanggal kosong Riot ("0001-01-01") dianggap tidak ada. */
    private fun parseInstant(text: String?): Long? =
        text?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }?.takeIf { it > 0 }

    /** Loadout yang terpasang dan semua item yang dimiliki pemain. */
    suspend fun loadCollection(session: RiotSession): PlayerCollection = withContext(Dispatchers.IO) {
        coroutineScope {
            val loadout = async {
                try {
                    get(pd(session, "personalization/v2/players/${session.puuid}/playerloadout").build())
                } catch (e: RiotException) {
                    if (e.sessionExpired) throw e
                    get(pd(session, "personalization/v3/players/${session.puuid}/playerloadout").build())
                }.jsonObject
            }
            val owned = listOf(TYPE_SKIN, TYPE_CARD, TYPE_SPRAY, TYPE_TITLE, TYPE_BUDDY).associateWith { type ->
                async { runCatching { entitlements(session, type) }.getOrDefault(emptyList()) }
            }
            parseCollection(loadout.await(), owned.mapValues { it.value.await() })
        }
    }

    private fun entitlements(session: RiotSession, typeId: String): List<String> =
        get(pd(session, "store/v1/entitlements/${session.puuid}/$typeId").build())
            .jsonObject["Entitlements"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.str("ItemID")?.lowercase() }

    /** [owned] berisi ID item yang dimiliki per ItemTypeID (huruf kecil). */
    internal fun parseCollection(loadout: JsonObject, owned: Map<String, List<String>>): PlayerCollection {
        val identity = loadout["Identity"] as? JsonObject
        val cardId = identity?.str("PlayerCardID")?.lowercase()
        val titleId = identity?.str("PlayerTitleID")?.lowercase()
        val card = cardId?.let { apiData("playercards/$it") }

        val sprayDefs = apiList("sprays").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        fun spray(id: String?, equipped: Boolean) = sprayDefs[id?.lowercase()]?.let {
            CollectionItem(id!!.lowercase(), it.str("displayName") ?: "Spray", it.str("fullTransparentIcon") ?: it.str("displayIcon"), equipped)
        }
        val flexDefs = apiList("flex").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        fun expression(id: String) = spray(id, true) ?: flexDefs[id]?.let {
            CollectionItem(id, it.str("displayName") ?: "Flex", it.str("displayIcon"), true)
        }
        // Format lama: "Sprays". Format baru (roda ekspresi): "Expressions.AESSelections" berisi spray dan flex.
        // Bila keduanya tidak ada, pindai seluruh loadout untuk ID spray/flex yang dikenal.
        val legacySprays = (loadout["Sprays"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("SprayID")?.lowercase() }
        val expressionIds = ((loadout["Expressions"] as? JsonObject)?.get("AESSelections") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.str("AssetID")?.lowercase() }
        val equippedExpressionIds = legacySprays.ifEmpty { expressionIds }.ifEmpty {
            val found = LinkedHashSet<String>()
            fun scan(e: JsonElement) {
                when (e) {
                    is JsonObject -> e.forEach { (key, value) -> if (key != "Guns" && key != "Identity") scan(value) }
                    is JsonArray -> e.forEach(::scan)
                    is JsonPrimitive -> e.contentOrNull?.lowercase()?.let { if (it in sprayDefs || it in flexDefs) found += it }
                }
            }
            scan(loadout)
            found.toList()
        }
        val equippedSprayIds = equippedExpressionIds.filter { it in sprayDefs }

        val buddyLevels = apiList("buddies/levels").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        // Nama level buddy sering berupa kode internal ("RankEP4_Iron"); pakai nama buddy induknya.
        val buddyNames = apiList("buddies").map { it.jsonObject }.flatMap { b ->
            (b["levels"] as? JsonArray).orEmpty().mapNotNull { l -> l.jsonObject.str("uuid")?.lowercase()?.let { it to b.str("displayName") } }
        }.toMap()
        fun buddyName(levelId: String?) = levelId?.lowercase()?.let { id -> buddyNames[id] ?: buddyLevels[id]?.str("displayName") }
        val guns = loadout["Guns"]?.jsonArray.orEmpty().map { it.jsonObject }.associateBy { it.str("ID")?.lowercase() }
        val equippedBuddyIds = guns.values.mapNotNull { it.str("CharmLevelID")?.lowercase() }.toSet()

        // Peta level/chroma skin -> data skin, dari daftar senjata valorant-api.
        val weapons = apiList("weapons").map { it.jsonObject }
        val skinByLevel = HashMap<String, JsonObject>()
        weapons.forEach { w ->
            (w["skins"] as? JsonArray).orEmpty().forEach { sk ->
                val skin = sk.jsonObject
                (skin["levels"] as? JsonArray).orEmpty().forEach { lv -> lv.jsonObject.str("uuid")?.let { skinByLevel[it.lowercase()] = skin } }
            }
        }
        val equipped = weapons.mapNotNull { w ->
            val gun = guns[w.str("uuid")?.lowercase()] ?: return@mapNotNull null
            val skin = (w["skins"] as? JsonArray).orEmpty().map { it.jsonObject }
                .firstOrNull { it.str("uuid").equals(gun.str("SkinID"), ignoreCase = true) }
            val chroma = (skin?.get("chromas") as? JsonArray).orEmpty().map { it.jsonObject }
                .firstOrNull { it.str("uuid").equals(gun.str("ChromaID"), ignoreCase = true) }
            val level = (skin?.get("levels") as? JsonArray).orEmpty().map { it.jsonObject }
                .firstOrNull { it.str("uuid").equals(gun.str("SkinLevelID"), ignoreCase = true) }
            val buddy = buddyLevels[gun.str("CharmLevelID")?.lowercase()]
            EquippedWeapon(
                weaponName = w.str("displayName") ?: "Senjata",
                skinLevelId = (skin?.get("levels") as? JsonArray)?.firstOrNull()?.jsonObject?.str("uuid"),
                skinName = skin?.str("displayName") ?: w.str("displayName") ?: "Standard",
                imageUrl = chroma?.str("fullRender") ?: level?.str("displayIcon") ?: skin?.str("displayIcon") ?: w.str("displayIcon"),
                buddyName = buddyName(gun.str("CharmLevelID")),
                buddyIcon = buddy?.str("displayIcon"),
            )
        }

        val cardDefs = apiList("playercards").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        val titleDefs = apiList("playertitles").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }
        val ownedCards = (owned[TYPE_CARD].orEmpty() + listOfNotNull(cardId)).distinct()
        val ownedTitles = (owned[TYPE_TITLE].orEmpty() + listOfNotNull(titleId)).distinct()
        val ownedSprays = (owned[TYPE_SPRAY].orEmpty() + equippedSprayIds).distinct()

        return PlayerCollection(
            cardName = card?.str("displayName"),
            cardWideArt = card?.str("wideArt"),
            title = titleId?.let { titleDefs[it] }?.str("titleText"),
            equippedSprays = equippedExpressionIds.take(4).map { expression(it) },
            weapons = equipped,
            ownedSkins = owned[TYPE_SKIN].orEmpty().mapNotNull { skinByLevel[it]?.str("uuid") }.distinct().size,
            cards = ownedCards.mapNotNull { id ->
                cardDefs[id]?.let { CollectionItem(id, it.str("displayName") ?: "Kartu", it.str("largeArt") ?: it.str("displayIcon"), id == cardId) }
            }.sortedWith(compareByDescending<CollectionItem> { it.equipped }.thenBy { it.name }),
            sprays = ownedSprays.mapNotNull { spray(it, it in equippedSprayIds) }
                .sortedWith(compareByDescending<CollectionItem> { it.equipped }.thenBy { it.name }),
            titles = ownedTitles.mapNotNull { id ->
                titleDefs[id]?.str("titleText")?.let { CollectionItem(id, it, null, id == titleId) }
            }.sortedWith(compareByDescending<CollectionItem> { it.equipped }.thenBy { it.name }),
            charms = owned[TYPE_BUDDY].orEmpty().distinct().mapNotNull { id ->
                buddyLevels[id]?.let { CollectionItem(id, buddyName(id) ?: "Charm", it.str("displayIcon"), id in equippedBuddyIds) }
            }.sortedWith(compareByDescending<CollectionItem> { it.equipped }.thenBy { it.name }),
            charmsTotal = buddyLevels.size,
        )
    }

    suspend fun loadCareer(session: RiotSession): CareerSummary = withContext(Dispatchers.IO) {
        parseCareer(get(pd(session, "mmr/v1/players/${session.puuid}").build()).jsonObject)
    }

    /** Statistik per mode per act dari `QueueSkills` di respons mmr. */
    internal fun parseCareer(mmr: JsonObject, now: Long = System.currentTimeMillis()): CareerSummary {
        val seasons = apiList("seasons").map { it.jsonObject }
        val byId = seasons.associateBy { it.str("uuid")?.lowercase() }
        fun label(id: String): String {
            val act = byId[id.lowercase()] ?: return "Act tidak dikenal"
            val episode = act.str("parentUuid")?.lowercase()?.let { byId[it] }
            return listOfNotNull(episode?.str("displayName"), act.str("displayName")).joinToString(" // ")
        }
        val current = seasons.firstOrNull { s ->
            s.str("type") == "EAresSeasonType::Act" &&
                (parseInstant(s.str("startTime")) ?: Long.MAX_VALUE) <= now && now < (parseInstant(s.str("endTime")) ?: 0)
        }?.str("uuid")?.lowercase()

        val queues = (mmr["QueueSkills"] as? JsonObject).orEmpty().mapNotNull { (queueId, value) ->
            val seasonal = ((value as? JsonObject)?.get("SeasonalInfoBySeasonID") as? JsonObject).orEmpty()
            val acts = seasonal.mapNotNull { (seasonId, info) ->
                val o = info as? JsonObject ?: return@mapNotNull null
                val games = o["NumberOfGames"]?.jsonPrimitive?.intOrNull ?: 0
                if (games <= 0) return@mapNotNull null
                val wins = o["NumberOfWinsWithPlacements"]?.jsonPrimitive?.intOrNull ?: o["NumberOfWins"]?.jsonPrimitive?.intOrNull ?: 0
                val tier = o["CompetitiveTier"]?.jsonPrimitive?.intOrNull ?: 0
                ActStats(
                    seasonId = seasonId.lowercase(),
                    label = label(seasonId),
                    startMillis = parseInstant(byId[seasonId.lowercase()]?.str("startTime")) ?: 0,
                    games = games,
                    wins = wins,
                    rank = if (queueId == "competitive" && tier > 2) {
                        rankInfo(RawRank(tier, o["RankedRating"]?.jsonPrimitive?.intOrNull, seasonId))
                    } else {
                        null
                    },
                )
            }.sortedByDescending { it.startMillis }
            if (acts.isEmpty()) null else QueueCareer(queueId, queueName(queueId, null), acts)
        }.sortedWith(compareByDescending<QueueCareer> { it.queueId == "competitive" }.thenByDescending { it.games })

        return CareerSummary(current, current?.let(::label), queues)
    }

    internal class RawRank(val tier: Int, val rankedRating: Int?, val seasonId: String?)

    /**
     * Dari respons `mmr/v1/players`: rank terakhir (LatestCompetitiveUpdate) dan peak rank, yaitu tier
     * tertinggi yang pernah dimenangkan atau dicapai di semua act.
     */
    internal fun parseRanks(mmr: JsonObject): Pair<RawRank?, RawRank?> {
        val latest = mmr["LatestCompetitiveUpdate"] as? JsonObject
        val current = latest?.let {
            val tier = it["TierAfterUpdate"]?.jsonPrimitive?.intOrNull ?: 0
            if (tier > 0) RawRank(tier, it["RankedRatingAfterUpdate"]?.jsonPrimitive?.intOrNull, it.str("SeasonID")) else null
        }
        val seasonal = ((mmr["QueueSkills"] as? JsonObject)?.get("competitive") as? JsonObject)
            ?.get("SeasonalInfoBySeasonID") as? JsonObject
        val peak = seasonal?.entries?.flatMap { (seasonId, info) ->
            val o = info as? JsonObject ?: return@flatMap emptyList()
            val won = (o["WinsByTier"] as? JsonObject)?.entries
                ?.filter { (_, wins) -> (wins.jsonPrimitive.intOrNull ?: 0) > 0 }
                ?.mapNotNull { (tier, _) -> tier.toIntOrNull() }
                .orEmpty()
            (won + listOfNotNull(o["CompetitiveTier"]?.jsonPrimitive?.intOrNull)).map { it to seasonId }
        }?.filter { it.first > 0 }?.maxByOrNull { it.first }
            ?.let { (tier, seasonId) -> RawRank(tier, null, seasonId) }
        return current to (peak ?: current?.let { RawRank(it.tier, null, it.seasonId) })
    }

    /** Nama, ikon, dan warna tier memakai set tier milik act tersebut (ikon berbeda per episode). */
    internal fun rankInfo(raw: RawRank): RankInfo {
        val seasons = apiList("seasons").map { it.jsonObject }
        val act = seasons.firstOrNull { it.str("uuid").equals(raw.seasonId, ignoreCase = true) }
        val episode = act?.str("parentUuid")?.let { parent -> seasons.firstOrNull { it.str("uuid") == parent } }
        val tierSets = apiList("competitivetiers").map { it.jsonObject }
        val setId = apiList("seasons/competitive").map { it.jsonObject }
            .firstOrNull { it.str("seasonUuid").equals(raw.seasonId, ignoreCase = true) }?.str("competitiveTiersUuid")
        val set = tierSets.firstOrNull { it.str("uuid") == setId } ?: tierSets.lastOrNull()
        val tier = set?.get("tiers")?.jsonArray?.map { it.jsonObject }
            ?.firstOrNull { it["tier"]?.jsonPrimitive?.intOrNull == raw.tier }
        return RankInfo(
            tier = raw.tier,
            tierName = tier?.str("tierName") ?: "TIER ${raw.tier}",
            iconUrl = tier?.str("largeIcon") ?: tier?.str("smallIcon"),
            color = tier?.str("color")?.take(6)?.toLongOrNull(16)?.let { 0xFF000000 or it },
            rankedRating = raw.rankedRating,
            seasonLabel = act?.let { a -> listOfNotNull(episode?.str("displayName"), a.str("displayName")).joinToString(" // ") },
        )
    }

    private val apiListCache = ConcurrentHashMap<String, JsonArray>()

    /** Array `data` dari valorant-api.com/v1/<path>, di-cache. */
    private fun apiList(path: String): JsonArray = apiListCache[path] ?: runCatching {
        get(Request.Builder().url("https://valorant-api.com/v1/$path").build()).jsonObject["data"]!!.jsonArray
    }.getOrElse { JsonArray(emptyList()) }.also { if (it.isNotEmpty()) apiListCache[path] = it }

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
        const val TYPE_SKIN = "e7c63390-eda7-46e0-bb7a-a6abdacd2433"
        const val TYPE_CARD = "3f296c07-64c3-494c-923b-fe692a4fa1bd"
        const val TYPE_SPRAY = "d5f120f8-ff8c-4aac-92ea-f2b5acbe9475"
        const val TYPE_TITLE = "de7caa6b-adf7-4588-bbd1-143831e786c6"
        const val TYPE_BUDDY = "dd3bf334-87f3-40bd-b043-682a57a8dc3a"
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
