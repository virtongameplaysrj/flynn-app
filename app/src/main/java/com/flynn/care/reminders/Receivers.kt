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
import com.flynn.care.data.ActionType
import com.flynn.care.data.Repo
import com.flynn.care.data.startOfToday
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime

/** Mostra uma notificação que abre o app ao tocar. */
@SuppressLint("MissingPermission")
private fun showNotification(context: Context, id: Int, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return

    val open = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
    val n = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL)
        .setSmallIcon(R.drawable.ic_paw)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    try {
        NotificationManagerCompat.from(context).notify(id, n)
    } catch (_: SecurityException) {
    }
}

/** Mostra a notificação quando o alarme de vacina/antipulgas/exame dispara. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        showNotification(
            context,
            intent.getIntExtra(ReminderScheduler.EXTRA_ID, 0),
            intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Flynn",
            intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: "",
        )
    }
}

/**
 * Alarme diário do colírio: só avisa se ainda não bateu a meta de hoje.
 * Depois agenda o de amanhã.
 */
class EyedropReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync() // o receiver tem ~10s; a consulta ao Firestore roda em segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = ReminderScheduler.eyedropFromCache(app)
                if (s.reminderOn && FirebaseAuth.getInstance().currentUser != null) {
                    val since = startOfToday(ZonedDateTime.now())
                    // Sem internet o Firestore responde com o cache local; se nem isso der, avisa assim mesmo.
                    val done = withTimeoutOrNull(7_000) {
                        runCatching { Repo.countSince(ActionType.EYEDROP, since) }.getOrNull()
                    }
                    when {
                        done == null -> showNotification(
                            app, ReminderScheduler.EYEDROP_NOTIFICATION_ID, "💧 Colírio do Flynn",
                            "Confira se o colírio de hoje já foi pingado (meta: ${s.goal}×).",
                        )
                        done < s.goal -> showNotification(
                            app, ReminderScheduler.EYEDROP_NOTIFICATION_ID, "💧 Falta colírio hoje",
                            "Pingaram $done de ${s.goal} vezes hoje. Falta${if (s.goal - done > 1) "m" else ""} ${s.goal - done}.",
                        )
                    }
                }
            } finally {
                ReminderScheduler.scheduleEyedrop(app, ReminderScheduler.eyedropFromCache(app))
                result.finish()
            }
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
            ReminderScheduler.scheduleEyedrop(applicationContext, Repo.fetchEyedropSettings())
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
