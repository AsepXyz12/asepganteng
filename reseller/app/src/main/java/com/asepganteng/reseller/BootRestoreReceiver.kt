package com.asepganteng.reseller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Dua tugas dalam satu receiver ini (sengaja digabung, cukup satu
 * <receiver> di manifest):
 *
 * 1. android.intent.action.BOOT_COMPLETED -- dipanggil Android sekali
 *    tiap HP baru selesai restart. Tanpa ini, semua alarm yang sudah
 *    didaftarkan lewat AlarmManager IKUT HILANG begitu HP restart
 *    (ini perilaku standar Android, bukan bug) -- jadi kita perlu
 *    daftar ulang semuanya dari jadwal yang terakhir tersimpan.
 *
 * 2. "com.asepganteng.<role>.MIDNIGHT_REFRESH" -- alarm internal yang
 *    dijadwalkan sendiri oleh PrayerAlarmScheduler tiap tengah malam,
 *    supaya alarm besok ikut ter-refresh otomatis walau app gak dibuka
 *    user sama sekali hari itu.
 */
class BootRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action?.endsWith("MIDNIGHT_REFRESH") == true
        ) {
            PrayerAlarmScheduler.applySchedule(context)
        }
    }
}
