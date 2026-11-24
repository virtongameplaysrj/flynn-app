package com.flynn.care.data

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Meia-noite de hoje no fuso do celular — igual à aba Histórico. */
fun startOfToday(now: ZonedDateTime): Instant = now.toLocalDate().atStartOfDay(now.zone).toInstant()

/** Quantos registros contam como "hoje" (desde a meia-noite). */
fun countToday(events: List<CareEvent>, now: ZonedDateTime): Int {
    val start = startOfToday(now)
    return events.count { !it.at.isBefore(start) }
}

/** Janela do aviso de "já registraram há pouco". */
val DUPLICATE_WINDOW: Duration = Duration.ofMinutes(30)

/**
 * O registro mais recente se foi feito há menos de [DUPLICATE_WINDOW] — para perguntar
 * antes de registrar de novo (evita o Flynn comer duas vezes).
 * @param events mais recente primeiro.
 */
fun recentDuplicate(events: List<CareEvent>, now: ZonedDateTime): CareEvent? =
    events.firstOrNull()?.takeIf { Duration.between(it.at, now.toInstant()) < DUPLICATE_WINDOW }

/** Dias de calendário desde o registro mais recente (0 = hoje); null se nunca houve. */
fun daysSinceLast(events: List<CareEvent>, now: ZonedDateTime): Long? =
    events.firstOrNull()?.let {
        ChronoUnit.DAYS.between(it.at.atZone(now.zone).toLocalDate(), now.toLocalDate())
    }

/** A partir de quantos dias sem escovar o botão fica vermelho. */
const val TEETH_ALERT_DAYS = 3
