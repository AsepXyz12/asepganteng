package com.asepganteng.reseller

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val targetUrl = "http://server.szxennofficial.my.id:3100/reseller"

    // Dipakai buat nyambungin hasil pilih file/kamera (dari Activity lain)
    // balik ke <input type="file"> di halaman web. WebView TIDAK bisa
    // munculin file chooser sendiri -- wajib di-handle manual lewat
    // WebChromeClient.onShowFileChooser + activity result launcher ini.
    // Tanpa ini, tombol upload PP/post/foto di web diem aja gak respon.
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = fileUploadCallback
            fileUploadCallback = null
            if (callback == null) return@registerForActivityResult

            if (result.resultCode != RESULT_OK || result.data == null) {
                callback.onReceiveValue(null)
                return@registerForActivityResult
            }

            val data = result.data
            val uris: Array<Uri> = when {
                data?.clipData != null -> {
                    val clip = data.clipData!!
                    Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                else -> emptyArray()
            }
            callback.onReceiveValue(if (uris.isEmpty()) null else uris)
        }

        val root = FrameLayout(this)
        swipeRefresh = SwipeRefreshLayout(this)
        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportZoom(false)
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                swipeRefresh.isRefreshing = false
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val scheme = uri.scheme?.lowercase()

                // Skema non-http(s) (whatsapp://, tg:, tel:, mailto:, intent://, dll)
                // WAJIB dilempar ke Intent Android -- WebView gak bisa & gak akan
                // pernah bisa nge-load skema kayak gitu sendiri (bakal selalu gagal
                // net::ERR_UNKNOWN_URL_SCHEME kalau dibiarin).
                if (scheme != "http" && scheme != "https") {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    } catch (e: Exception) {
                        // App tujuan (WhatsApp/Telegram/dll) gak keinstall -- jangan
                        // biarin WebView nyoba load & nge-crash, cukup diemin aja.
                        true
                    }
                }

                // Link http(s) ke domain server sendiri: tetap di dalam WebView.
                if (uri.host != null && uri.host == Uri.parse(targetUrl).host) {
                    return false
                }

                // Link http(s) ke domain LAIN (misal wa.me/t.me sebelum redirect ke
                // skema app): lempar ke Intent juga (biar Android yang milihin --
                // kalau WhatsApp/Telegram terinstall & App Links-nya aktif, bakal
                // langsung ke app tanpa mampir buka halaman wa.me/t.me dulu;
                // fallback otomatis ke browser default kalau gak ada app yang cocok).
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (e: Exception) {
                    false // fallback: biar WebView sendiri yang coba load
                }
            }
        }

        // WAJIB buat <input type="file"> (upload PP/postingan/foto/video)
        // bisa beneran buka file chooser Android.
        webView.webChromeClient = object : WebChromeClient() {
            // Tanpa override ini, alert()/confirm() dari JS di web DIDIEMIN TOTAL
            // (gak ada dialog yang muncul sama sekali) -- ini yang bikin pesan
            // error dari web (misal gagal bikin QRIS) gak pernah keliatan di app.
            override fun onJsAlert(
                view: WebView,
                url: String,
                message: String,
                result: android.webkit.JsResult
            ): Boolean {
                androidx.appcompat.app.AlertDialog.Builder(view.context)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView,
                url: String,
                message: String,
                result: android.webkit.JsResult
            ): Boolean {
                androidx.appcompat.app.AlertDialog.Builder(view.context)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setNegativeButton("Batal") { _, _ -> result.cancel() }
                    .setCancelable(false)
                    .show()
                return true
            }

            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                android.util.Log.d(
                    "WebViewConsole",
                    "${consoleMessage.message()} -- line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                )
                return true
            }

            override fun onShowFileChooser(
                view: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                val intent = fileChooserParams.createIntent().apply {
                    // Boleh multi-pilih kalau <input> web-nya punya atribut "multiple"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    fileUploadCallback = null
                    false
                }
            }
        }

        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(swipeRefresh, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        webView.loadUrl(targetUrl)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
