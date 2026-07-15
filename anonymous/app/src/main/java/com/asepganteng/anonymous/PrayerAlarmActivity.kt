package com.asepganteng.anonymous

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Layar alarm full-screen -- muncul di ATAS lockscreen (persis kayak
 * alarm bawaan HP) begitu waktu sholat tiba, walau app sedang tertutup
 * total. User bisa "Tandai Selesai" (matikan alarm & reminder susulan
 * buat waktu sholat ini hari ini) atau "Tunda 5 Menit".
 *
 * Suara: pakai file audio yang kamu taruh di res/raw/adzan.mp3 (lihat
 * catatan TODO di bawah) -- gampang diganti ke ceramah/murrotal lain,
 * tinggal timpa file itu.
 */
class PrayerAlarmActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Munculin di atas lockscreen & nyalain layar -- ini yang bikin
        // alarm keliatan walau HP lagi terkunci, sama seperti app alarm
        // bawaan.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, null)
        }

        val prayerKey = intent.getStringExtra("prayer_key") ?: ""
        val prayerLabel = intent.getStringExtra("prayer_label") ?: "Sholat"

        setContentView(buildUi(prayerKey, prayerLabel))

        playAlarmSound()
        vibrateDevice()
    }

    /** UI dibangun manual lewat kode (bukan XML layout) supaya patch ini
     * bisa ditempel tanpa perlu file layout tambahan -- silakan ganti ke
     * layout XML sendiri kalau mau tampilan lebih custom. */
    private fun buildUi(prayerKey: String, prayerLabel: String): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 160, 48, 48)
            setBackgroundColor(0xFF0A0F12.toInt())
        }

        val title = TextView(this).apply {
            text = "Waktu $prayerLabel Telah Tiba"
            textSize = 26f
            setTextColor(0xFFEAF2F1.toInt())
            setPadding(0, 0, 0, 24)
        }
        val subtitle = TextView(this).apply {
            text = "Yuk tunaikan sholat sekarang."
            textSize = 15f
            setTextColor(0xFF7F9092.toInt())
            setPadding(0, 0, 0, 64)
        }

        val doneBtn = Button(this).apply {
            text = "Tandai Selesai"
            setOnClickListener {
                stopAlarmSound()
                PrayerReminderScheduler.cancelReminderLoop(this@PrayerAlarmActivity, prayerKey)
                finish()
            }
        }
        val snoozeBtn = Button(this).apply {
            text = "Tunda 5 Menit"
            setOnClickListener {
                stopAlarmSound()
                PrayerReminderScheduler.snooze(this@PrayerAlarmActivity, prayerKey, prayerLabel)
                finish()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(doneBtn)
        root.addView(snoozeBtn)
        return root
    }

    private fun playAlarmSound() {
        try {
            // TODO: taruh file audio adzan/ceramah di app/src/main/res/raw/adzan.mp3
            // (nama file HARUS lowercase + tanpa spasi, format mp3/ogg/wav).
            val resId = resources.getIdentifier("adzan", "raw", packageName)
            if (resId == 0) return // file belum ditaruh -- skip diam-diam drpd crash
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(this@PrayerAlarmActivity, android.net.Uri.parse("android.resource://$packageName/$resId"))
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerAlarmActivity", "Gagal play alarm sound: ${e.message}")
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.apply { if (isPlaying) stop(); release() }
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun vibrateDevice() {
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val pattern = longArrayOf(0, 800, 400, 800, 400, 800)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
    }

    override fun onBackPressed() {
        // Sengaja TIDAK dibiarkan nutup activity cuma dengan tombol back
        // -- user harus pilih "Tandai Selesai" atau "Tunda" secara sadar,
        // supaya gak ke-skip gak sengaja kayak notifikasi biasa yang bisa
        // di-swipe tanpa lihat.
    }
}
