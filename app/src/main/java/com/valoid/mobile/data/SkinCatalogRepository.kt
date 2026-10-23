package com.valoid.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** Katalog skin publik dari valorant-api.com, dimuat sekali lalu disimpan di memori. */
class SkinCatalogRepository(private val client: OkHttpClient = OkHttpClient()) {

    @Serializable
    private data class SkinsResponse(val data: List<ApiSkin>)

    @Serializable
    private data class TiersResponse(val data: List<ApiTier>)

    @Serializable
    private data class ApiSkin(
        val uuid: String,
        val displayName: String,
        val contentTierUuid: String? = null,
        val displayIcon: String? = null,
        val chromas: List<ApiChroma> = emptyList(),
        val levels: List<ApiLevel> = emptyList(),
    )

    @Serializable
    private data class ApiChroma(
        val displayName: String,
        val displayIcon: String? = null,
        val fullRender: String? = null,
        val swatch: String? = null,
        val streamedVideo: String? = null,
    )

    @Serializable
    private data class ApiLevel(
        val uuid: String,
        val levelItem: String? = null,
        val displayIcon: String? = null,
        val streamedVideo: String? = null,
    )

    @Serializable
    private data class ApiTier(
        val uuid: String,
        val displayName: String? = null,
        val highlightColor: String? = null,
        val displayIcon: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var skinsByLevel: Map<String, ApiSkin>? = null
    private var tiers: Map<String, ApiTier>? = null

    suspend fun detail(levelId: String): SkinDetail = withContext(Dispatchers.IO) {
        val (byLevel, tierMap) = load()
        val skin = byLevel[levelId.lowercase()] ?: error("Detail skin tidak ditemukan")
        val tier = skin.contentTierUuid?.let { tierMap[it] }
        val chromas = skin.chromas.mapIndexed { i, c ->
            SkinChroma(
                name = if (i == 0) "Standar" else chromaName(c.displayName),
                swatchUrl = c.swatch,
                imageUrl = c.fullRender ?: c.displayIcon,
                videoUrl = c.streamedVideo,
            )
        }
        SkinDetail(
            levelId = levelId,
            name = skin.displayName,
            imageUrl = chromas.firstOrNull()?.imageUrl ?: skin.displayIcon ?: skin.levels.firstOrNull()?.displayIcon,
            tier = tier?.let { SkinTier(it.displayName ?: "", it.displayIcon, parseColor(it.highlightColor)) },
            chromas = chromas,
            levels = skin.levels.mapIndexed { i, l -> SkinLevel(i + 1, featureName(l.levelItem), l.streamedVideo) },
        )
    }

    private suspend fun load(): Pair<Map<String, ApiSkin>, Map<String, ApiTier>> = mutex.withLock {
        val skins = skinsByLevel ?: json.decodeFromString<SkinsResponse>(fetch("https://valorant-api.com/v1/weapons/skins"))
            .data
            .flatMap { skin -> skin.levels.map { it.uuid.lowercase() to skin } }
            .toMap()
            .also { skinsByLevel = it }
        val tierMap = tiers ?: json.decodeFromString<TiersResponse>(fetch("https://valorant-api.com/v1/contenttiers"))
            .data
            .associateBy { it.uuid }
            .also { tiers = it }
        skins to tierMap
    }

    private fun fetch(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) error("Gagal memuat katalog skin (${it.code})")
        it.body?.string().orEmpty()
    }

    private companion object {
        /** "Power Fist Level 2\n(Variant 1 Dance Fever)" -> "Dance Fever" */
        fun chromaName(raw: String): String {
            val inner = raw.substringAfter('(', "").substringBeforeLast(')', "").ifBlank { raw }
            return inner.replace(Regex("^Variant \\d+\\s*"), "").trim().ifBlank { raw.trim() }
        }

        /** "EEquippableSkinLevelItem::KillCounter" -> "Kill Counter" */
        fun featureName(levelItem: String?): String? = levelItem
            ?.substringAfter("::")
            ?.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")

        /** valorant-api memakai RRGGBBAA; alpha diabaikan agar warnanya tegas. */
        fun parseColor(hex: String?): Long? =
            hex?.take(6)?.toLongOrNull(16)?.let { 0xFF000000 or it }
    }
}
