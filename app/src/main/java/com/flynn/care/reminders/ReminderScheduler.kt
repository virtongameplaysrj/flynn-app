package com.flynn.care.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.flynn.care.data.CareItem
import com.flynn.care.data.ItemType
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Cada celular agenda os próprios alarmes a partir dos dados sincronizados.
 * Sempre que a lista muda, cancelamos tudo e reagendamos — simples e sem duplicatas.
 */
object ReminderScheduler {
    const val CHANNEL = "lembretes"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    private const val PREFS = "reminders"

    fun reschedule(ctx: Context, items: List<CareItem>) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.getStringSet("codes", emptySet())?.forEach { c ->
            c.toIntOrNull()?.let { am.cancel(pending(app, it, Intent(app, ReminderReceiver::class.java))) }
        }

        val now = ZonedDateTime.now()
        val codes = mutableSetOf<String>()
        for (item in items) {
            if (item.done) continue
            val date = runCatching { LocalDate.parse(item.date) }.getOrNull() ?: continue
            for (d in item.offsets.distinct()) {
                if (d < 0) continue
                val at = date.minusDays(d.toLong())
                    .atTime(item.hour.coerceIn(0, 23), 0)
                    .atZone(ZoneId.systemDefault())
                if (!at.isAfter(now)) continue

                val code = "${item.id}#$d".hashCode()
                val (title, body) = message(item, d)
                val intent = Intent(app, ReminderReceiver::class.java)
                    .putExtra(EXTRA_ID, code)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_BODY, body)
                // Inexato mas dispara mesmo em modo soneca; não exige permissão de alarme exato.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending(app, code, intent))
                codes += code.toString()
            }
        }
        prefs.edit()
            .putStringSet("codes", codes)
            .putString("items", CareItem.listToJson(items))
            .apply()
    }

    /** Usado após reiniciar o celular, quando ainda não há internet. */
    fun rescheduleFromCache(ctx: Context) {
        val s = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", null)
        reschedule(ctx, CareItem.listFromJson(s))
    }

    private fun pending(ctx: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(ctx, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun message(item: CareItem, daysBefore: Int): Pair<String, String> {
        val whenTxt = when (daysBefore) {
            0 -> "HOJE"
            1 -> "Amanhã"
            else -> "Em $daysBefore dias"
        }
        val what = when (item.type) {
            ItemType.VACCINE -> "vacina ${item.title}"
            ItemType.FLEA -> "antipulgas ${item.title}" + if (item.dose.isNotBlank()) " (${item.dose})" else ""
            ItemType.EXAM -> "exame ${item.title}"
        }
        val body = item.notes.ifBlank { "Lembrete de cuidado do Flynn" }
        return "🐶 $whenTxt: $what" to body
    }
}
