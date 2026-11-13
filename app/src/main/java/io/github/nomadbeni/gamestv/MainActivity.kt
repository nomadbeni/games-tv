package io.github.nomadbeni.gamestv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var lastBackAt = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        }
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true // a játékok localStorage-ba mentenek
            mediaPlaybackRequiresUserGesture = false // Web Audio gombnyomás nélkül
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false) // target=_blank is ebben a nézetben nyílik (és a korláton megy át)
            javaScriptCanOpenWindowsAutomatically = false
            textZoom = 100
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        webView.setDownloadListener { _, _, _, _, _ -> } // letöltés nincs
        webView.webViewClient = Client()

        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        hideSystemUi()
        registerBackCallback()

        if (savedInstanceState == null || !restoreWebView(savedInstanceState)) webView.loadUrl(HOME_URL)
        webView.requestFocus()
    }

    private fun restoreWebView(state: Bundle): Boolean =
        webView.restoreState(state)?.let { it.size > 0 } ?: false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        hideSystemUi()
    }

    override fun onPause() {
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    /** A távirányító Vissza gombja: Android 13 alatt ez hívódik. */
    // TV-n nincs vissza-gesztus, csak a távirányító gombja; 13 felett a callback kezeli.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Android 13+ alatt az OnBackInvokedCallback kezeli")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        goBack()
    }

    private fun registerBackCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
    }

    /** Vissza: ha van előzmény, egyet vissza; a startlapon a második megnyomás kilép. */
    fun goBack() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        if (!isStartPage(webView.url)) {
            webView.loadUrl(HOME_URL)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < EXIT_WINDOW_MS) {
            finish()
        } else {
            lastBackAt = now
            Toast.makeText(this, R.string.press_again_to_exit, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateKeepScreenOn(url: String?) {
        val playing = url != null && isAllowed(url) && !isStartPage(url) && !url.startsWith(ASSET_ORIGIN)
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowed(request.url.toString()) // ami nem a minigames alá esik, csendben elnyelődik

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            updateKeepScreenOn(url)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // A böngészőmotor leállt (pl. memóriahiány): új nézettel indul újra, nem omlik össze az app.
            recreate()
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val failed = request.url.toString()
            if (failed.startsWith(ERROR_PAGE)) return
            view.loadUrl(ERROR_PAGE + "?u=" + Uri.encode(if (isAllowed(failed)) failed else HOME_URL))
        }
    }

    companion object {
        const val HOME_URL = "https://nomadbeni.github.io/minigames/"
        const val ASSET_ORIGIN = "https://" + WebViewAssetLoader.DEFAULT_DOMAIN
        const val ERROR_PAGE = "$ASSET_ORIGIN/assets/error.html"
        private const val EXIT_WINDOW_MS = 2000L

        /** Csak a minigames oldalai és az app saját (beágyazott) oldalai nyílhatnak meg. */
        fun isAllowed(url: String): Boolean =
            url.startsWith(HOME_URL) || url == HOME_URL.trimEnd('/') || url.startsWith("$ASSET_ORIGIN/assets/")

        fun isStartPage(url: String?): Boolean {
            if (url == null) return false
            val u = url.substringBefore('#').substringBefore('?')
            return u == HOME_URL || u == HOME_URL + "index.html" || u == HOME_URL.trimEnd('/')
        }
    }
}
