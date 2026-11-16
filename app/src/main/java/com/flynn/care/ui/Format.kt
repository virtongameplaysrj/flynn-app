package com.flynn.care.ui

import com.flynn.care.data.CareItem
import com.flynn.care.data.ItemType
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val ddmmHhmm = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val ddmmyyyy = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun Instant.fmtTime(): String = hhmm.format(atZone(ZoneId.systemDefault()))
fun Instant.fmtDayTime(): String = ddmmHhmm.format(atZone(ZoneId.systemDefault()))
fun LocalDate.fmt(): String = ddmmyyyy.format(this)

fun ago(at: Instant, now: ZonedDateTime): String {
    val m = Duration.between(at, now.toInstant()).toMinutes().coerceAtLeast(0)
    return when {
        m < 1 -> "agora"
        m < 60 -> "há $m min"
        else -> "há ${m / 60}h%02d".format(m % 60)
    }
}

fun CareItem.localDate(): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()
fun CareItem.daysUntil(today: LocalDate = LocalDate.now()): Long? =
    localDate()?.let { ChronoUnit.DAYS.between(today, it) }

fun relative(days: Long): String = when {
    days == 0L -> "hoje"
    days == 1L -> "amanhã"
    days > 1 -> "em $days dias"
    days == -1L -> "atrasado 1 dia"
    else -> "atrasado ${-days} dias"
}

fun describeOffset(d: Int): String = when (d) {
    0 -> "no dia"
    1 -> "1 dia antes"
    else -> "$d dias antes"
}

fun CareItem.headline(): String = buildString {
    append(type.emoji).append(' ').append(title.ifBlank { "(sem nome)" })
    if (dose.isNotBlank()) append(" · ").append(dose)
}

fun titleLabel(t: ItemType) = when (t) {
    ItemType.VACCINE -> "Nome da vacina"
    ItemType.FLEA -> "Produto (antipulgas / vermífugo)"
    ItemType.EXAM -> "Nome do exame"
}

fun dateLabel(t: ItemType) = when (t) {
    ItemType.VACCINE -> "Data da vacina"
    ItemType.FLEA -> "Data da aplicação"
    ItemType.EXAM -> "Data do exame"
}
