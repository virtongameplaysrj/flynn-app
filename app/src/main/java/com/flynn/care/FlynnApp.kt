package com.flynn.care

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.flynn.care.reminders.ReminderScheduler
import com.flynn.care.reminders.SyncWorker
import java.util.concurrent.TimeUnit

class FlynnApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            ReminderScheduler.CHANNEL, "Lembretes do Flynn", NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Vacinas, antipulgas e exames" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val sync = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("sync-lembretes", ExistingPeriodicWorkPolicy.KEEP, sync)
    }
}
