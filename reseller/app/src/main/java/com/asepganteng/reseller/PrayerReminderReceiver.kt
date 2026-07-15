package com.asepganteng.reseller

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Dipanggil tiap 5 menit (selama syarat masih terpenuhi) buat nampilin
 * notifikasi pengingat susulan -- BUKAN full-screen alarm lagi, cukup
 * notifikasi biasa yang bisa di-tap buat buka app.
 *
 * Teks notifikasi diambil dari daftar array yang sama seperti di web
 * (services/prayerTimes.service.js -> REMINDER_MESSAGES) -- di sini
 * disalin ringkas supaya reminder tetap bisa muncul walau device lagi
 * offline saat itu. Kalau mau selalu sinkron 1:1 dengan yang di web,
 * bisa juga diganti supaya receiver ini nge-fetch ke
 * /api/prayer-times/reminder dulu (butuh koneksi internet saat itu).
 */
class PrayerReminderReceiver : BroadcastReceiver() {

    private val fallbackMessages = listOf(
        "Rasulullah \u25ce bersabda: \"Amal yang pertama kali dihisab dari seorang hamba pada hari kiamat adalah sholatnya.\" (HR. Tirmidzi)",
        "Dari Ibnu Mas'ud, Nabi \u25ce ditanya amalan paling dicintai Allah, jawab beliau: \"Sholat tepat pada waktunya.\" (HR. Bukhari-Muslim)",
        "Nabi \u25ce bersabda: \"Perbanyaklah mengingat pemutus segala kenikmatan (kematian).\" (HR. Tirmidzi)",
        "Rasulullah \u25ce bersabda: \"Pembeda antara seorang muslim dan kekufuran adalah meninggalkan sholat.\" (HR. Muslim)"
    )

    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("prayer_key") ?: return
        val prayerLabel = intent.getStringExtra("prayer_label") ?: "Sholat"

        val shouldContinue = PrayerReminderScheduler.onReminderTick(context, prayerKey, prayerLabel)
        if (!shouldContinue) return // syarat berhenti terpenuhi -- gak nampilin notif lagi

        showReminderNotification(context, prayerKey, prayerLabel)
    }

    private fun showReminderNotification(context: Context, prayerKey: String, prayerLabel: String) {
        val channelId = "prayer_reminder_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Pengingat Sholat", NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val message = fallbackMessages[Math.abs(System.currentTimeMillis().toInt()) % fallbackMessages.size]

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Waktu $prayerLabel masih menunggu")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: ganti ke ikon app sendiri
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(prayerKey.hashCode(), notification)
    }
}
