package com.flynn.care.data

import java.time.ZonedDateTime

/**
 * O número grande da tela inicial: quantas refeições contam como "hoje".
 *
 * @param feedings refeições dos últimos 3 dias, da mais recente para a mais antiga
 *                 (Feeding.at é um java.time.Instant).
 * @param now      data/hora atual no fuso do celular.
 */
fun feedingsToday(feedings: List<Feeding>, now: ZonedDateTime): Int {
    // O dia começa à meia-noite, no fuso do celular — igual à aba Histórico.
    val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
    return feedings.count { !it.at.isBefore(startOfDay) }
}
