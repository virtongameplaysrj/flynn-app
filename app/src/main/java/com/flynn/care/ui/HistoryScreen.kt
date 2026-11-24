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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flynn.care.data.ActionType
import com.flynn.care.data.CareEvent
import com.flynn.care.data.EyedropSettings
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

const val HISTORY_DAYS = 30

private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE, dd/MM", Locale("pt", "BR"))
private val shortFmt = DateTimeFormatter.ofPattern("dd/MM")

/**
 * Aba "Histórico": uma sub-aba por botão da tela inicial (comida, colírio, dentes),
 * cada uma com um cartão por dia dos últimos 30 dias.
 * @param events null = ainda carregando aquele tipo.
 */
@Composable
fun HistoryScreen(
    events: Map<ActionType, List<CareEvent>?>,
    eyedrop: EyedropSettings,
    onSaveEyedrop: (EyedropSettings) -> Unit,
) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val type = ActionType.entries[sub]
    Column {
        TabRow(selectedTabIndex = sub) {
            ActionType.entries.forEachIndexed { i, t ->
                Tab(
                    selected = sub == i,
                    onClick = { sub = i },
                    text = { Text("${t.emoji} ${t.tab}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        EventHistory(type, events[type], eyedrop, onSaveEyedrop)
    }
}

@Composable
private fun EventHistory(
    type: ActionType,
    events: List<CareEvent>?,
    eyedrop: EyedropSettings,
    onSaveEyedrop: (EyedropSettings) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val byDay = events.orEmpty().groupBy { it.at.atZone(zone).toLocalDate() }
    // De hoje até o dia mais antigo com registro (não mostra dias antes de começarem a usar o botão).
    val oldest = byDay.keys.minOrNull() ?: today
    val days = generateSequence(today) { it.minusDays(1) }
        .takeWhile { !it.isBefore(oldest) }
        .take(HISTORY_DAYS)
        .toList()
    val goal = if (type == ActionType.EYEDROP) eyedrop.goal else 0

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text("${type.emoji} ${type.tab}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Últimos $HISTORY_DAYS dias: horário e quem fez.", style = MaterialTheme.typography.bodySmall)
        }
        if (type == ActionType.EYEDROP) {
            item { EyedropGoalCard(eyedrop, onSaveEyedrop) }
        }
        if (events == null) {
            item { Text("Carregando…") }
        } else {
            items(days, key = { type.name + it }) { day ->
                DayCard(type, day, today, byDay[day].orEmpty().sortedBy { it.at }, goal)
            }
        }
    }
}

@Composable
private fun DayCard(type: ActionType, day: LocalDate, today: LocalDate, list: List<CareEvent>, goal: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(dayLabel(day, today), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val count = if (goal > 0) "${list.size}/$goal" else "${list.size}"
                Text("$count ${type.emoji}", style = MaterialTheme.typography.titleMedium)
            }
            // Comida todo dia é obrigatório; colírio só quando há meta; escovação não marca falta.
            val alarming = type == ActionType.FOOD || goal > 0
            if (list.isEmpty()) {
                Text(
                    type.none,
                    color = if (alarming) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                list.forEach { e ->
                    Row {
                        Text(e.at.fmtTime(), Modifier.width(64.dp), fontWeight = FontWeight.SemiBold)
                        Text(e.by)
                    }
                }
                val perPerson = list.groupingBy { it.by }.eachCount()
                Text(
                    perPerson.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Hoje ainda pode completar, então só aponta a falta em dias passados.
            if (goal > 0 && list.size < goal && day.isBefore(today)) {
                Text("Faltou ${goal - list.size} de $goal", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Meta diária do colírio + hora do lembrete; vale para os 2 celulares. */
@Composable
private fun EyedropGoalCard(s: EyedropSettings, onSave: (EyedropSettings) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Meta diária", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (s.goal == 0) "Sem meta" else "${s.goal}× por dia · " +
                        (if (s.reminderOn) "lembrete às ${s.reminderHour}h se faltar" else "sem lembrete"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { editing = true }) { Text("Alterar") }
        }
    }
    if (editing) {
        var goal by remember { mutableIntStateOf(s.goal) }
        var hour by remember { mutableIntStateOf(if (s.reminderHour in 0..23) s.reminderHour else -1) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("💧 Meta do colírio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper("Vezes por dia", if (goal == 0) "sem meta" else "$goal×",
                        onMinus = { goal = (goal - 1).coerceAtLeast(0) },
                        onPlus = { goal = (goal + 1).coerceAtMost(12) })
                    if (goal > 0) {
                        // -1 = desligado; depois 6h…23h.
                        Stepper("Lembrar se faltar, às", if (hour < 0) "desligado" else "${hour}h",
                            onMinus = { hour = if (hour <= 6) -1 else hour - 1 },
                            onPlus = { hour = if (hour < 0) 20 else (hour + 1).coerceAtMost(23) })
                        Text(
                            "Os 2 celulares avisam nesse horário se ainda não chegou na meta do dia.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(EyedropSettings(goal, if (goal > 0) hour else -1)); editing = false
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = onMinus) { Text("−") }
            Text(value, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = onPlus) { Text("+") }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String =
    when (ChronoUnit.DAYS.between(day, today)) {
        0L -> "Hoje · ${shortFmt.format(day)}"
        1L -> "Ontem · ${shortFmt.format(day)}"
        else -> weekdayFmt.format(day).replaceFirstChar { it.uppercase() }
    }
