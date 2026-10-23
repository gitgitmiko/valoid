package com.valoid.mobile.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.valoid.mobile.data.RiotAuth

/**
 * Di sebagian versi WebView form login tidak tergambar: pada versi lama gambar latar menutupi kartu,
 * pada versi baru pembungkus kartu setinggi 0 memotong isinya. Kartu yang berisi kolom username
 * diangkat ke lapisannya sendiri, pembungkusnya dibuka, dan latar disembunyikan bila tidak
 * membungkus kartu. Dijalankan ulang setiap kali halaman (SPA) merender ulang.
 */
private const val FIX_LAYOUT =
    "(function(){if(window.__valoidFix)return;window.__valoidFix=true;" +
        "function set(e,k,v){if(e.style.getPropertyValue(k)!==v||e.style.getPropertyPriority(k)!=='important')" +
        "e.style.setProperty(k,v,'important');}" +
        "function fix(){var u=document.querySelector('input[name=username],input[type=password]');if(!u)return;" +
        "var card=u.closest('[class*=main-background]')||u.closest('form')||u.parentElement;" +
        "set(card,'position','relative');set(card,'z-index','10');set(card,'will-change','transform');" +
        "for(var e=card.parentElement;e;e=e.parentElement){set(e,'overflow','visible');set(e,'contain','none');" +
        "set(e,'content-visibility','visible');set(e,'clip-path','none');}" +
        "document.querySelectorAll('[class*=page-background]').forEach(function(e){" +
        "if(!e.contains(card))set(e,'display','none');});}" +
        "fix();new MutationObserver(fix).observe(document.documentElement,{childList:true,subtree:true});})();"

private const val DISMISS_COOKIE_BANNER =
    "(function(){var b=document.getElementById('onetrust-accept-btn-handler')||" +
        "document.querySelector('.onetrust-close-btn-handler');if(b)b.click();})();"

/** UA bawaan WebView memuat "; wv" dan "Version/x.y" yang membuat halaman login/captcha menolak render. */
private fun browserUserAgent(default: String): String =
    default.replace("; wv", "").replace(Regex("Version/\\d+\\.\\d+\\s"), "")

/**
 * Login Riot ditampilkan di WebView resmi auth.riotgames.com. Aplikasi tidak pernah melihat
 * username/password, hanya access token pada redirect.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RiotLoginScreen(
    onToken: (accessToken: String, idToken: String?) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Tema aplikasi gelap; tanpa ini WebView menggelapkan halaman Riot sampai form tak terbaca.
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        settings.isAlgorithmicDarkeningAllowed = false
                    } else if (android.os.Build.VERSION.SDK_INT >= 29) {
                        @Suppress("DEPRECATION")
                        settings.forceDark = WebSettings.FORCE_DARK_OFF
                    }
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(true)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString = browserUserAgent(WebSettings.getDefaultUserAgent(context))
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)

                    var delivered = false
                    fun intercept(url: String?): Boolean {
                        if (url == null || !url.startsWith(RiotAuth.REDIRECT)) return false
                        if (!delivered) {
                            val tokens = RiotAuth.parseRedirect(url)
                            if (tokens != null) {
                                delivered = true
                                RiotAuth.persist()
                                onToken(tokens.accessToken, tokens.idToken)
                            } else if (url.contains("error=")) {
                                error = "Riot menolak login: ${Uri.parse(url.replace('#', '?')).getQueryParameter("error")}"
                            }
                        }
                        return true
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            progress = newProgress / 100f
                        }


                        // Captcha dan beberapa langkah login membuka jendela baru; tampung di WebView yang sama.
                        override fun onCreateWindow(
                            view: WebView,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message,
                        ): Boolean {
                            val popup = WebView(view.context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = view.settings.userAgentString
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                                        view.loadUrl(r.url.toString())
                                        return true
                                    }
                                }
                            }
                            (resultMsg.obj as WebView.WebViewTransport).webView = popup
                            resultMsg.sendToTarget()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            intercept(request.url.toString())

                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            intercept(url)
                        }

                        override fun onPageCommitVisible(view: WebView, url: String?) {
                            view.evaluateJavascript(FIX_LAYOUT, null)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            intercept(url)
                            // Tutup banner cookie bila ada agar tidak menutupi form login.
                            view.evaluateJavascript(DISMISS_COOKIE_BANNER, null)
                            view.evaluateJavascript(FIX_LAYOUT, null)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                            intercept(url)
                        }

                        override fun onRenderProcessGone(
                            view: WebView,
                            detail: android.webkit.RenderProcessGoneDetail,
                        ): Boolean {
                            error = "Proses WebView berhenti (crash=${detail.didCrash()})"
                            return true
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                            if (request.isForMainFrame) error = "Gagal memuat halaman login: ${err.description}"
                        }
                    }
                    loadUrl(RiotAuth.AUTH_URL)
                }
            },
        )
        if (progress in 0.01f..0.99f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}
