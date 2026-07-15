package com.asepganteng.anonymous

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dipanggil OS tepat pas waktu sholat (didaftarkan lewat
 * PrayerAlarmScheduler.scheduleOne -> AlarmManager). Tugasnya cuma buka
 * PrayerAlarmActivity dalam mode full-screen -- app TIDAK PERLU sedang
 * terbuka buat ini bisa jalan, karena BroadcastReceiver dijalankan
 * langsung oleh sistem Android.
 */
class PrayerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("prayer_key") ?: return
        val prayerLabel = intent.getStringExtra("prayer_label") ?: "Sholat"

        val activityIntent = Intent(context, PrayerAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("prayer_key", prayerKey)
            putExtra("prayer_label", prayerLabel)
        }
        context.startActivity(activityIntent)
    }
}
