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
class RiotRepository(private val client: OkHttpClient = OkHttpClient()) {

    private val noRedirects = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /**
     * Meminta token baru memakai cookie sesi Riot yang tersimpan. Mengembalikan null bila cookie
     * tidak ada atau sudah tidak berlaku (Riot mengarahkan ke halaman login).
     */
    suspend fun reauthenticate(): RiotAuth.Tokens? {
        // CookieManager diakses di main thread agar aman juga saat dipanggil dari worker latar.
        val cookies = withContext(Dispatchers.Main) { RiotAuth.cookieHeader() }
            ?.takeIf { it.contains("ssid=") } ?: return null
        val (setCookies, location) = withContext(Dispatchers.IO) {
            noRedirects.newCall(
                Request.Builder().url(RiotAuth.AUTH_URL).header("Cookie", cookies).build(),
            ).execute().use { it.headers("Set-Cookie") to it.header("Location") }
        }
        withContext(Dispatchers.Main) { RiotAuth.storeCookies(setCookies) }
        return RiotAuth.parseRedirect(location)
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
        coroutineScope {
            history.map { id ->
                async {
                    matchCache[id] ?: gate.withPermit {
                        runCatching {
                            parseMatch(get(pd(session, "match-details/v1/matches/$id").build()).jsonObject, session.puuid, rrByMatch[id])
                        }.getOrNull()
                    }?.also { matchCache[id] = it }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** Mengubah respons `match-details` menjadi [Match] dari sudut pandang pemain [puuid]. */
    internal fun parseMatch(json: JsonObject, puuid: String, rrChange: Int?): Match {
        val info = json["matchInfo"]!!.jsonObject
        val seasonId = info.str("seasonId")
        val map = apiList("maps").map { it.jsonObject }.firstOrNull { it.str("mapUrl").equals(info.str("mapId"), ignoreCase = true) }
        val agents = apiList("agents?isPlayableCharacter=true").map { it.jsonObject }.associateBy { it.str("uuid")?.lowercase() }

        val players = json["players"]?.jsonArray.orEmpty().map { it.jsonObject }.map { p ->
            val stats = p["stats"] as? JsonObject
            val rounds = stats?.get("roundsPlayed")?.jsonPrimitive?.intOrNull ?: 0
            val score = stats?.get("score")?.jsonPrimitive?.intOrNull ?: 0
            val agent = agents[p.str("characterId")?.lowercase()]
            val tier = p["competitiveTier"]?.jsonPrimitive?.intOrNull ?: 0
            val rank = if (tier > 2) rankInfo(RawRank(tier, null, seasonId)) else null
            MatchPlayer(
                puuid = p.str("subject").orEmpty(),
                name = p.str("gameName") ?: "Pemain",
                tag = p.str("tagLine"),
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
        )
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
        parseContracts(get(pd(session, "contracts/v1/contracts/${session.puuid}").build()).jsonObject)
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

        return ContractsOverview(
            battlePass = progress(battlePassId),
            agentContract = progress(json.str("ActiveSpecialContract")),
            missions = missions,
            weeklyRefillMillis = parseInstant((json["MissionMetadata"] as? JsonObject)?.str("WeeklyRefillTime"))?.takeIf { it > now },
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
