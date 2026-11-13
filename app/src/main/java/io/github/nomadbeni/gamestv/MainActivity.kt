package io.github.nomadbeni.gamestv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import kotlin.math.abs

class MainActivity : Activity(), InputManager.InputDeviceListener {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var keyMap: KeyMap
    private lateinit var core: ControllerCore
    private lateinit var bridgeJs: String
    private var documentStartInjection = false
    private var overlay: DebugOverlay? = null
    private var lastBackAt = 0L

    private var sentMask = -1
    private val sentAxes = FloatArray(6)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        keyMap = KeyMap.parse(readAsset("keymap.json"))
        core = ControllerCore(keyMap)
        bridgeJs = readAsset("bridge.js")

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

        // A híd minden oldal elején fut, mielőtt az oldal saját szkriptjei elindulnak.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(webView, bridgeJs, setOf(SITE_ORIGIN, ASSET_ORIGIN))
            documentStartInjection = true
        }

        val root = FrameLayout(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        if (BuildConfig.DEBUG) overlay = DebugOverlay(this).also { root.addView(it.view, it.layoutParams()) }
        setContentView(root)
        hideSystemUi()
        registerBackCallback()
        getSystemService(InputManager::class.java)?.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))

        val start = debugStartUrl(intent)
        if (start != null) webView.loadUrl(start)
        else if (savedInstanceState == null || !restoreWebView(savedInstanceState)) webView.loadUrl(HOME_URL)
        webView.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        debugStartUrl(intent)?.let { webView.loadUrl(it) }
    }

    /** Debug buildben: `adb shell am start -n …/.MainActivity --es page gamepad-test` a próbaoldalt nyitja. */
    private fun debugStartUrl(intent: Intent?): String? {
        if (!BuildConfig.DEBUG) return null
        if (intent?.getBooleanExtra("overlay", false) == true) overlay?.show(true)
        val page = intent?.getStringExtra("page") ?: return null
        return if (page.matches(Regex("[a-z0-9-]+"))) "$ASSET_ORIGIN/assets/$page.html" else null
    }

    private fun readAsset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

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
        send(core.releaseAll()) // ne maradjon beragadt gomb, ha az app háttérbe megy
        webView.onPause()
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onDestroy() {
        getSystemService(InputManager::class.java)?.unregisterInputDeviceListener(this)
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

    // ---------------------------------------------------------------- kontroller

    override fun onInputDeviceAdded(deviceId: Int) {}
    override fun onInputDeviceChanged(deviceId: Int) {}
    override fun onInputDeviceRemoved(deviceId: Int) {
        // Lekapcsolódott kontroller: minden gomb felenged, újracsatlakozás után tiszta lappal indul.
        send(core.releaseAll())
        overlay?.log("Eszköz lekapcsolódott: $deviceId")
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        overlay?.logKey(event, keyMap.androidKeys[KeyEvent.keyCodeToString(event.keyCode)])
        val btn = keyMap.androidKeys[KeyEvent.keyCodeToString(event.keyCode)]
        if (btn == null) {
            // Ismeretlen kontrollergomb: elnyeljük, hogy a WebView ne mozgasson rá fókuszt vagy kurzort.
            // A rendszergombok (Vissza, Kezdőlap, hangerő) a rendszerhez mennek tovább.
            return if (isFromController(event) && !event.isSystem) true else super.dispatchKeyEvent(event)
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) handleButton(btn, true)
            KeyEvent.ACTION_UP -> handleButton(btn, false)
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!joystick || event.actionMasked != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event)
        val x = centered(event, MotionEvent.AXIS_X)
        val y = centered(event, MotionEvent.AXIS_Y)
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val rx = centered(event, MotionEvent.AXIS_Z)
        val ry = centered(event, MotionEvent.AXIS_RZ)
        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        overlay?.logAxes(x, y, hatX, hatY, rx, ry, lt, rt)
        send(core.motion(x, y, hatX, hatY, rx, ry, lt, rt))
        return true
    }

    /** A tengely értéke, a hardver saját holtsávján belül nulla (a kar nyugalmi remegése ne mozgasson). */
    private fun centered(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis, event.source) ?: return event.getAxisValue(axis)
        val v = event.getAxisValue(axis)
        return if (abs(v) > range.flat) v else 0f
    }

    private fun isFromController(e: KeyEvent): Boolean =
        e.isFromSource(InputDevice.SOURCE_GAMEPAD) || e.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            e.isFromSource(InputDevice.SOURCE_DPAD)

    private fun handleButton(btn: PadButton, down: Boolean) {
        val url = webView.url
        val testPage = url?.startsWith(TEST_PAGE) == true
        // Debug gyorsgombok (a próbaoldalon nem: ott minden gomb az oldalé, hogy látszódjon).
        if (BuildConfig.DEBUG && !testPage && (btn == PadButton.L3 || btn == PadButton.R3)) {
            if (down && btn == PadButton.L3) overlay?.toggle()
            if (down && btn == PadButton.R3) webView.loadUrl(TEST_PAGE)
            return
        }
        // Select: mindig az app „vissza”. B: a startlapon és a hibaoldalon is vissza, játékban a játéké.
        if (btn in keyMap.appBack || (btn == PadButton.B && (isStartPage(url) || url?.startsWith(ERROR_PAGE) == true))) {
            if (!down) goBack()
            return
        }
        send(core.button(btn, down))
    }

    /** Állapot és billentyűk küldése az oldalnak. A tengelyek apró változását nem küldi el újra. */
    private fun send(keys: List<KeyOut>) {
        val mask = core.buttonMask
        val a = core.axes
        val lt = core.buttonValue(PadButton.LT)
        val rt = core.buttonValue(PadButton.RT)
        val now = floatArrayOf(a[0], a[1], a[2], a[3], lt, rt)
        val axesChanged = now.indices.any { abs(now[it] - sentAxes[it]) > AXIS_EPSILON || (now[it] == 0f) != (sentAxes[it] == 0f) }
        if (keys.isEmpty() && mask == sentMask && !axesChanged) return
        sentMask = mask
        now.copyInto(sentAxes)

        val js = StringBuilder(96)
        js.append("window.__gtv&&__gtv.input(").append(mask).append(",[")
        for (i in 0 until 4) { if (i > 0) js.append(','); js.append(fmt(a[i])) }
        js.append("],[").append(fmt(lt)).append(',').append(fmt(rt)).append("],[")
        keys.forEachIndexed { i, k ->
            if (i > 0) js.append(',')
            js.append('[').append(JSONObject.quote(k.type)).append(',')
                .append(JSONObject.quote(k.stroke.key)).append(',')
                .append(JSONObject.quote(k.stroke.code)).append(']')
        }
        js.append("])")
        webView.evaluateJavascript(js.toString(), null)
    }

    private fun fmt(v: Float): String = if (v == 0f) "0" else "%.3f".format(java.util.Locale.ROOT, v)

    // ---------------------------------------------------------------- navigáció

    /** A távirányító Vissza gombja Android 13 alatt. */
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
        if (overlay?.isShown == true) {
            overlay?.show(false)
            return
        }
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
        val playing = url != null && isAllowed(url) && !isStartPage(url) && !isAppPage(url)
        if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !isAllowed(request.url.toString()) // ami nem a minigames alá esik, csendben elnyelődik

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
            assetLoader.shouldInterceptRequest(request.url)

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            sentMask = -1 // az új oldal is megkapja az aktuális állapotot
            if (!documentStartInjection && url != null && isAllowed(url)) view.evaluateJavascript(bridgeJs, null)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            // Tartalék: ha a dokumentum eleji injektálás nem működik, itt biztosan bekerül (kétszer nem fut le).
            if (url != null && isAllowed(url)) view.evaluateJavascript(bridgeJs, null)
        }

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
        const val SITE_ORIGIN = "https://nomadbeni.github.io"
        const val HOME_URL = "$SITE_ORIGIN/minigames/"
        const val ASSET_ORIGIN = "https://" + WebViewAssetLoader.DEFAULT_DOMAIN
        const val ERROR_PAGE = "$ASSET_ORIGIN/assets/error.html"
        const val TEST_PAGE = "$ASSET_ORIGIN/assets/gamepad-test.html"
        private const val EXIT_WINDOW_MS = 2000L
        private const val AXIS_EPSILON = 0.02f

        /** Csak a minigames oldalai és az app saját (beágyazott) oldalai nyílhatnak meg. */
        fun isAllowed(url: String): Boolean =
            url.startsWith(HOME_URL) || url == HOME_URL.trimEnd('/') || isAppPage(url)

        fun isAppPage(url: String?): Boolean = url != null && url.startsWith("$ASSET_ORIGIN/assets/")

        fun isStartPage(url: String?): Boolean {
            if (url == null) return false
            val u = url.substringBefore('#').substringBefore('?')
            return u == HOME_URL || u == HOME_URL + "index.html" || u == HOME_URL.trimEnd('/')
        }
    }
}
