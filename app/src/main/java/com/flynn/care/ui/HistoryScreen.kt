package com.flynn.care.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flynn.care.data.Feeding
import com.flynn.care.data.Repo
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

const val HISTORY_DAYS = 30

private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("pt", "BR"))
private val shortFmt = DateTimeFormatter.ofPattern("dd/MM")

/** Aba "Histórico": refeições dos últimos 30 dias, um cartão por dia. */
@Composable
fun HistoryScreen() {
    val feedings by remember { Repo.recentFeedings(days = HISTORY_DAYS.toLong()) }
        .collectAsStateWithLifecycle(initialValue = null as List<Feeding>?)
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val byDay = feedings.orEmpty().groupBy { it.at.atZone(zone).toLocalDate() }
    // De hoje até o dia mais antigo com registro (não mostra dias antes de começarem a usar o app).
    val oldest = byDay.keys.minOrNull() ?: today
    val days = generateSequence(today) { it.minusDays(1) }
        .takeWhile { !it.isBefore(oldest) }
        .take(HISTORY_DAYS)
        .toList()

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text("📋 Histórico", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Refeições dos últimos $HISTORY_DAYS dias: horário e quem deu.", style = MaterialTheme.typography.bodySmall)
        }
        if (feedings == null) {
            item { Text("Carregando…") }
        } else {
            items(days, key = { it.toString() }) { day ->
                DayCard(day, today, byDay[day].orEmpty().sortedBy { it.at })
            }
        }
    }
}

@Composable
private fun DayCard(day: LocalDate, today: LocalDate, list: List<Feeding>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(dayLabel(day, today), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${list.size} 🍖", style = MaterialTheme.typography.titleMedium)
            }
            if (list.isEmpty()) {
                Text("Nenhuma refeição registrada", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            } else {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                list.forEach { f ->
                    Row {
                        Text(f.at.fmtTime(), Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                        Text(f.by)
                    }
                }
                val perPerson = list.groupingBy { it.by }.eachCount()
                Text(
                    perPerson.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String =
    when (ChronoUnit.DAYS.between(day, today)) {
        0L -> "Hoje · ${shortFmt.format(day)}"
        1L -> "Ontem · ${shortFmt.format(day)}"
        else -> weekdayFmt.format(day).replaceFirstChar { it.uppercase() }
    }
