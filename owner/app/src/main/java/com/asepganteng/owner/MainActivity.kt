package com.asepganteng.owner

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
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
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val targetUrl = PanelConfig.OWNER_ENDPOINT

    // Dipakai buat nyambungin hasil pilih file/kamera (dari Activity lain)
    // balik ke <input type="file"> di halaman web. WebView TIDAK bisa
    // munculin file chooser sendiri -- wajib di-handle manual lewat
    // WebChromeClient.onShowFileChooser + activity result launcher ini.
    // Tanpa ini, tombol upload PP/post/foto di web diem aja gak respon.
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    // ===== Ditambahkan untuk fitur Alarm Jadwal Sholat =====

    // Callback WebView buat izin lokasi (dipanggil pas web manggil
    // navigator.geolocation.getCurrentPosition). Tanpa override
    // onGeolocationPermissionsShowPrompt di WebChromeClient, WebView bakal
    // nolak diam-diam semua permintaan lokasi dari JS.
    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null

    // Izin RUNTIME Android (POST_NOTIFICATIONS, lokasi) -- ini BEDA dari
    // izin WebView di atas. Android 13+ butuh izin notifikasi runtime dari
    // OS dulu SEBELUM WebView-level permission apapun ada gunanya.
    private lateinit var runtimePermissionLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var prefs: SharedPreferences

    // ========================================================

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("prayer_alarm_prefs", Context.MODE_PRIVATE)

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

        // ===== Ditambahkan: minta izin runtime notifikasi + lokasi =====
        runtimePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* hasil ditangani otomatis lewat WebView permission callback berikutnya */ }
        requestRuntimePermissionsIfNeeded()
        // ================================================================

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
            // Wajib di-enable manual -- tanpa ini, navigator.geolocation di JS
            // bakal gagal/reject walau app sudah punya izin ACCESS_FINE_LOCATION
            // dari Android dan onGeolocationPermissionsShowPrompt sudah di-override.
            setGeolocationEnabled(true)
        }

        // ===== Jembatan JS <-> Kotlin buat alarm sholat native =====
        webView.addJavascriptInterface(WebAppInterface(), "AndroidPrayerAlarm")
        // =============================================================

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

            // ===== Ditambahkan: izin GEOLOKASI untuk WebView =====
            // Tanpa override ini, navigator.geolocation.getCurrentPosition()
            // di prayer-alarm.js akan SELALU gagal/timeout di dalam WebView,
            // walau app sudah punya izin ACCESS_FINE_LOCATION dari Android.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFineLocation) {
                    callback.invoke(origin, true, false)
                } else {
                    // Simpan callback, minta izin runtime dulu, baru di-invoke
                    // lagi setelah user merespons dialog izin Android.
                    geolocationCallback = callback
                    geolocationOrigin = origin
                    runtimePermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
                }
            }

            // Izin notifikasi Web Notification API di dalam WebView (BEDA
            // dari izin notifikasi Android runtime POST_NOTIFICATIONS --
            // WebView punya lapisan izin sendiri buat JS Notification API).
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    request.grant(request.resources)
                }
            }
            // ========================================================
        }

        swipeRefresh.setOnRefreshListener { webView.reload() }
        swipeRefresh.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(swipeRefresh, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        webView.loadUrl(targetUrl)
    }

    // ===== Ditambahkan: minta izin Android runtime (bukan izin WebView) =====
    private fun requestRuntimePermissionsIfNeeded() {
        val toRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (toRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(toRequest.toTypedArray())
        }

        // Android 12+ (API 31+) mewajibkan user approve manual "Alarm &
        // pengingat" lewat halaman Settings khusus -- gak bisa lewat
        // dialog permission biasa. Kalau belum diizinkan, arahkan user
        // ke halaman itu sekali (cukup sekali seumur install biasanya).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    // Beberapa OEM custom ROM gak selalu ada halaman ini -- jangan
                    // sampai app crash kalau intent-nya gak ketemu.
                }
            }
        }
    }

    /**
     * Jembatan yang dipanggil dari JS lewat window.AndroidPrayerAlarm.xxx().
     * Dipasang di prayer-alarm.js sebagai pelengkap opsional -- kalau
     * window.AndroidPrayerAlarm ada (berarti jalan di dalam APK ini),
     * jadwal & status aktivitas JUGA didaftarkan ke alarm native di sini,
     * supaya tetap jalan walau app ditutup total. Kalau dibuka di browser
     * biasa (bukan APK), interface ini otomatis tidak ada dan web tetap
     * jalan pakai timer JS biasa seperti sebelumnya.
     */
    inner class WebAppInterface {

        /** Dipanggil dari prayer-alarm.js setelah fetch jadwal sholat
         * berhasil. scheduleJson formatnya sama seperti dikembalikan API:
         * [{"key":"Fajr","label":"Subuh","time":"04:32"}, ...] */
        @android.webkit.JavascriptInterface
        fun schedulePrayerAlarms(scheduleJson: String) {
            PrayerAlarmScheduler.scheduleFromJson(applicationContext, scheduleJson)
        }

        /** Dipanggil dari prayer-alarm.js tiap ada interaksi user (klik/
         * scroll/sentuh) -- dipakai PrayerReminderScheduler buat nentuin
         * device masih aktif dipakai atau sudah idle. */
        @android.webkit.JavascriptInterface
        fun reportUserActive() {
            prefs.edit().putLong("last_interaction_at", System.currentTimeMillis()).apply()
        }

        /** Dipanggil dari prayer-alarm.js pas user klik "Tandai Selesai"
         * di widget web -- supaya alarm native juga ikut berhenti, gak
         * cuma yang di JS. */
        @android.webkit.JavascriptInterface
        fun markPrayerDone(prayerKey: String) {
            PrayerReminderScheduler.cancelReminderLoop(applicationContext, prayerKey)
        }
    }
    // ==========================================================================

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
