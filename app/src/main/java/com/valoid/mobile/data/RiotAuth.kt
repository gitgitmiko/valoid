package com.valoid.mobile.data

import android.net.Uri
import android.webkit.CookieManager

/**
 * Alur login Riot (RSO) lewat halaman resmi. Cookie sesi Riot disimpan oleh WebView di perangkat
 * dan dipakai ulang untuk meminta token baru tanpa menampilkan halaman login lagi.
 */
object RiotAuth {
    const val REDIRECT = "https://playvalorant.com/opt_in"
    const val AUTH_URL = "https://auth.riotgames.com/authorize" +
        "?redirect_uri=https%3A%2F%2Fplayvalorant.com%2Fopt_in" +
        "&client_id=play-valorant-web-prod" +
        "&response_type=token%20id_token" +
        "&nonce=1" +
        "&scope=account%20openid"
    private const val AUTH_ORIGIN = "https://auth.riotgames.com"

    data class Tokens(val accessToken: String, val idToken: String?)

    /** Mengambil token dari URL redirect, atau null bila URL bukan redirect yang berisi token. */
    fun parseRedirect(url: String?): Tokens? {
        if (url == null || !url.startsWith(REDIRECT)) return null
        val params = Uri.parse(url.replace('#', '?'))
        val access = params.getQueryParameter("access_token") ?: return null
        return Tokens(access, params.getQueryParameter("id_token"))
    }

    fun cookieHeader(): String? = CookieManager.getInstance().getCookie(AUTH_ORIGIN)

    fun storeCookies(setCookieHeaders: List<String>) {
        val cookies = CookieManager.getInstance()
        setCookieHeaders.forEach { cookies.setCookie(AUTH_ORIGIN, it) }
        cookies.flush()
    }

    fun persist() = CookieManager.getInstance().flush()

    fun clear() {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
    }
}
