package com.asepganteng.owner

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import java.util.Calendar

/**
 * Reminder susulan native -- versi Kotlin dari logic yang sama seperti
 * public/js/prayer-alarm.js: kalau alarm utama sudah bunyi tapi user
 * belum "Tandai Selesai", kirim reminder ulang tiap 5 menit SELAMA
 * device masih aktif dipakai, dan BERHENTI begitu di-dismiss atau device
 * idle (dideteksi dari waktu layar terakhir menyala -- lihat isDeviceActive).
 *
 * PENTING (supaya konsisten dengan batasan yang sudah disepakati): ini
 * BUKAN reminder tanpa henti. Begitu salah satu syarat berhenti terpenuhi,
 * reminder loop dihentikan permanen buat waktu sholat itu di hari itu.
 */
object PrayerReminderScheduler {

    private const val PREFS_NAME = "prayer_alarm_prefs"
    private const val REMINDER_REQUEST_BASE = 5200
    private const val REMINDER_INTERVAL_MS = 5 * 60 * 1000L
    // Device dianggap "idle" kalau layar sudah gak nyala/gak ada interaksi
    // 5 menit terakhir -- dicek lewat waktu nyala layar terakhir yang
    // dicatat MainActivity (lihat WebAppInterface.reportUserActive()).
    private const val IDLE_THRESHOLD_MS = 5 * 60 * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun cancelReminderLoop(context: Context, prayerKey: String) {
        prefs(context).edit().putBoolean(dismissedKey(prayerKey), true).apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            putExtra("prayer_key", prayerKey)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderRequestCode(prayerKey), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun snooze(context: Context, prayerKey: String, prayerLabel: String) {
        scheduleNextReminder(context, prayerKey, prayerLabel, delayMs = REMINDER_INTERVAL_MS)
    }

    fun startReminderLoop(context: Context, prayerKey: String, prayerLabel: String) {
        if (isDismissed(context, prayerKey)) return
        scheduleNextReminder(context, prayerKey, prayerLabel, delayMs = REMINDER_INTERVAL_MS)
    }

    /** Dipanggil oleh PrayerReminderReceiver tiap kali reminder ke-trigger
     * -- cek syarat berhenti, dan kalau masih boleh lanjut, jadwalkan
     * tick berikutnya. */
    fun onReminderTick(context: Context, prayerKey: String, prayerLabel: String): Boolean {
        if (isDismissed(context, prayerKey)) return false
        if (!isDeviceActive(context)) return false

        scheduleNextReminder(context, prayerKey, prayerLabel, delayMs = REMINDER_INTERVAL_MS)
        return true
    }

    private fun scheduleNextReminder(context: Context, prayerKey: String, prayerLabel: String, delayMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerReminderReceiver::class.java).apply {
            putExtra("prayer_key", prayerKey)
            putExtra("prayer_label", prayerLabel)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderRequestCode(prayerKey), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("PrayerReminderScheduler", "Gak bisa jadwal reminder: ${e.message}")
        }
    }

    private fun reminderRequestCode(prayerKey: String): Int =
        REMINDER_REQUEST_BASE + prayerKey.hashCode() % 100

    private fun isDismissed(context: Context, prayerKey: String): Boolean =
        prefs(context).getBoolean(dismissedKey(prayerKey), false)

    private fun dismissedKey(prayerKey: String): String = "dismissed_${prayerKey}_${todayKey()}"

    /** Device dianggap aktif kalau timestamp interaksi terakhir (dicatat
     * lewat WebAppInterface.reportUserActive(), dipanggil dari JS tiap ada
     * sentuhan/klik/scroll -- sama seperti logic isDeviceActive() di
     * prayer-alarm.js) masih dalam 5 menit terakhir. */
    private fun isDeviceActive(context: Context): Boolean {
        val lastActive = prefs(context).getLong("last_interaction_at", 0L)
        return (System.currentTimeMillis() - lastActive) < IDLE_THRESHOLD_MS
    }

    private fun todayKey(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }
}
