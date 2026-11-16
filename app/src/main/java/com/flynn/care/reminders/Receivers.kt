package com.flynn.care.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.flynn.care.MainActivity
import com.flynn.care.R
import com.flynn.care.data.Repo
import com.google.firebase.auth.FirebaseAuth

/** Mostra a notificação quando o alarme dispara. */
class ReminderReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""
        val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_paw)
            .setContentTitle(intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Flynn")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0), n)
        } catch (_: SecurityException) {
        }
    }
}

/** Alarmes somem quando o celular reinicia ou o app atualiza — reagendamos. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleFromCache(context)
    }
}

/**
 * A cada ~6h busca os dados no Firestore e reagenda, para que um lembrete
 * criado no celular da outra pessoa apareça aqui mesmo sem abrir o app.
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            ReminderScheduler.reschedule(applicationContext, Repo.fetchItems())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
