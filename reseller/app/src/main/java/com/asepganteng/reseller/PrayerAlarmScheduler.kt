package com.asepganteng.reseller

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Helper buat daftar/hapus alarm native (AlarmManager) per waktu sholat.
 *
 * Kenapa ini perlu padahal sudah ada timer di JS (prayer-alarm.js)?
 * -- Timer JS (setTimeout) di WebView cuma jalan selama PROSES app masih
 *    hidup (minimize/di-background masih oke di banyak kasus, tapi kalau
 *    app di-"swipe close" dari recent apps ATAU Android mem-kill proses
 *    buat hemat baterai, semua timer JS ikut mati total). AlarmManager
 *    beda -- dia didaftarkan ke SISTEM Android, jadi tetap jalan walau
 *    proses app-nya sendiri sudah tidak ada, dan Android yang akan
 *    "membangunkan" app sebentar cuma buat nampilin alarm itu.
 *
 * Data jadwal (jam:menit tiap waktu sholat + labelnya) DIKIRIM dari web
 * lewat WebAppInterface.schedulePrayerAlarms(...) di MainActivity.kt --
 * jadi sumber kebenaran jadwalnya TETAP di web/Aladhan API seperti
 * sebelumnya, cuma "pendaftaran alarm ke OS"-nya yang dipindah ke sini
 * biar tahan dari app di-kill.
 */
object PrayerAlarmScheduler {

    private const val PREFS_NAME = "prayer_alarm_prefs"
    private const val KEY_SCHEDULE_JSON = "schedule_json"
    // Kode request PendingIntent per waktu sholat -- angka ini SENGAJA
    // tetap (bukan random) supaya alarm lama utk waktu yang sama otomatis
    // ketimpa/bisa di-cancel dengan pasti, bukan malah numpuk dobel.
    private const val REQUEST_CODE_BASE = 5100

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Simpan jadwal terbaru (dipanggil dari WebAppInterface) & langsung
     * jadwalkan alarm native buat tiap waktu sholat hari ini yang belum
     * lewat.
     *
     * @param scheduleJson format: [{"key":"Fajr","label":"Subuh","time":"04:32"}, ...]
     */
    fun scheduleFromJson(context: Context, scheduleJson: String) {
        prefs(context).edit().putString(KEY_SCHEDULE_JSON, scheduleJson).apply()
        applySchedule(context)
    }

    /** Dipanggil ulang tiap hari (dari alarm "refresh tengah malam" di
     * bawah) & setelah HP restart (BootRestoreReceiver), supaya alarm
     * besok juga otomatis terpasang tanpa perlu buka app manual. */
    fun applySchedule(context: Context) {
        val json = prefs(context).getString(KEY_SCHEDULE_JSON, null) ?: return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                val key = entry.getString("key")
                val label = entry.getString("label")
                val time = entry.getString("time") // "HH:mm"
                scheduleOne(context, alarmManager, i, key, label, time)
            }
        } catch (e: Exception) {
            android.util.Log.e("PrayerAlarmScheduler", "Gagal parse/jadwalkan schedule: ${e.message}")
        }

        scheduleMidnightRefresh(context, alarmManager)
    }

    private fun scheduleOne(
        context: Context,
        alarmManager: AlarmManager,
        index: Int,
        key: String,
        label: String,
        time: String
    ) {
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Kalau jamnya udah lewat hari ini, jangan jadwalkan (biar gak
        // langsung alarm nyasar begitu app dibuka siang hari misalnya).
        if (cal.timeInMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra("prayer_key", key)
            putExtra("prayer_label", label)
        }
        val requestCode = REQUEST_CODE_BASE + index
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                    )
                } else {
                    // User belum kasih izin "Alarm & pengingat" di setting
                    // Android 12+ -- fallback ke alarm gak presisi drpd
                    // gak jadwal sama sekali (meleset beberapa menit tapi
                    // tetap jalan).
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent
                )
            }
        } catch (e: SecurityException) {
            android.util.Log.e("PrayerAlarmScheduler", "Gak punya izin exact alarm: ${e.message}")
        }
    }

    /** Jadwal ulang otomatis besok pagi jam 00:00:10 -- alarm ini yang
     * manggil applySchedule() lagi, TAPI berdasar jadwal yang TERAKHIR
     * disimpan (masih jadwal hari yang sama). Jadwal jam sholat yang
     * BENERAN akurat buat besok tetap perlu ditarik ulang dari
     * Aladhan API lewat web (lat/long device bisa geser). Makanya
     * MainActivity juga munculin WebView reload ringan tiap tengah malam
     * -- lihat MainActivity.kt. Alarm native di sini cuma jaring pengaman
     * kedua: kalau app lagi kebetulan gak dibuka pas tengah malam sama
     * sekali, minimal jadwal HARI SEBELUMNYA (yang mungkin jamnya beda
     * cuma 1-2 menit dari hari baru, karena jadwal sholat harian geser
     * dikit) tetap lebih baik daripada gak ada alarm sama sekali. */
    private fun scheduleMidnightRefresh(context: Context, alarmManager: AlarmManager) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 10)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, BootRestoreReceiver::class.java).apply {
            action = "com.asepganteng.reseller.MIDNIGHT_REFRESH"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE - 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        } catch (e: SecurityException) {
            android.util.Log.e("PrayerAlarmScheduler", "Gak bisa jadwal midnight refresh: ${e.message}")
        }
    }
}
