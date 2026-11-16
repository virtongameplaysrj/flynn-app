package com.flynn.care.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** As 3 visualizações da curva de peso. */
enum class WeightScale(val label: String) { DAY("Dia"), MONTH("Mês"), YEAR("Ano") }

/** Um ponto da curva: início do período (para o eixo X), peso e rótulo do eixo. */
data class WeightPoint(val start: LocalDate, val kg: Double, val label: String)

private val dayFmt = DateTimeFormatter.ofPattern("dd/MM")
private val monthFmt = DateTimeFormatter.ofPattern("MMM/yy", Locale("pt", "BR"))

/**
 * Agrupa as pesagens por dia, mês ou ano. Quando há mais de uma pesagem no
 * mesmo período, usa a MÉDIA, assim um mês com 4 pesagens não "pesa" mais que
 * um mês com 1, e uma pesagem atípica (logo após comer) é suavizada.
 */
fun aggregateWeights(entries: List<WeightEntry>, scale: WeightScale): List<WeightPoint> {
    val parsed = entries.mapNotNull { e ->
        runCatching { LocalDate.parse(e.date) }.getOrNull()?.let { it to e.kg }
    }
    val groups: Map<LocalDate, List<Double>> = parsed.groupBy(
        keySelector = { (d, _) ->
            when (scale) {
                WeightScale.DAY -> d
                WeightScale.MONTH -> d.withDayOfMonth(1)
                WeightScale.YEAR -> d.withDayOfYear(1)
            }
        },
        valueTransform = { it.second },
    )
    return groups.toSortedMap().map { (start, kgs) ->
        val label = when (scale) {
            WeightScale.DAY -> dayFmt.format(start)
            WeightScale.MONTH -> monthFmt.format(YearMonth.from(start)).replace(".", "")
            WeightScale.YEAR -> start.year.toString()
        }
        WeightPoint(start, kgs.average(), label)
    }
}
