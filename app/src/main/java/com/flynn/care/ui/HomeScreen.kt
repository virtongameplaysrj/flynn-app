package com.flynn.care.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flynn.care.BuildConfig
import com.flynn.care.data.ActionType
import com.flynn.care.data.CareEvent
import com.flynn.care.data.CareItem
import com.flynn.care.data.EyedropSettings
import com.flynn.care.data.TEETH_ALERT_DAYS
import com.flynn.care.data.countToday
import com.flynn.care.data.daysSinceLast
import com.flynn.care.data.recentDuplicate
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    email: String,
    myName: String,
    events: Map<ActionType, List<CareEvent>>,
    eyedrop: EyedropSettings,
    care: List<CareItem>,
    onRecord: (ActionType) -> Unit,
    onDelete: (CareEvent) -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = ZonedDateTime.now() }
    }
    var toDelete by remember { mutableStateOf<CareEvent?>(null) }
    var toConfirm by remember { mutableStateOf<CareEvent?>(null) }

    fun list(t: ActionType) = events[t].orEmpty()
    val food = list(ActionType.FOOD)
    val mealsToday = countToday(food, now)
    val upcoming = care.filter { !it.done && it.localDate() != null }.sortedBy { it.date }.take(4)
    val recent = events.values.flatten().sortedByDescending { it.at }.take(10)

    fun click(t: ActionType) {
        val dup = recentDuplicate(list(t), ZonedDateTime.now())
        if (dup != null) toConfirm = dup else onRecord(t)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Flynn 🐶", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total de refeições hoje", style = MaterialTheme.typography.titleMedium)
                    Text("$mealsToday", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    food.firstOrNull()?.let {
                        Text(
                            "Última: ${it.at.fmtTime()} (${ago(it.at, now)}) por ${it.by}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionType.entries.forEach { t ->
                    val (status, colors) = buttonState(t, list(t), eyedrop, now)
                    ActionButton(t, status, colors, Modifier.weight(1f)) { click(t) }
                }
            }
        }

        if (upcoming.isNotEmpty()) {
            item { Text("Próximos cuidados", style = MaterialTheme.typography.titleMedium) }
            items(upcoming, key = { "c" + it.id }) { c ->
                val days = c.daysUntil() ?: 0
                Card(
                    Modifier.fillMaxWidth(),
                    colors = if (days <= 2) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    else CardDefaults.cardColors(),
                ) {
                    Row(Modifier.padding(12.dp)) {
                        Text(c.headline(), Modifier.weight(1f))
                        Text(relative(days), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { Text("Registros recentes", style = MaterialTheme.typography.titleMedium) }
            items(recent, key = { it.type.name + it.id }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${e.type.emoji}  ${e.at.fmtDayTime()} — ${e.by}", Modifier.weight(1f))
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Default.Delete, contentDescription = "Apagar") }
                }
            }
        }

        item {
            TextButton(onClick = { FirebaseAuth.getInstance().signOut() }) {
                Text("Sair ($email) · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    toConfirm?.let { last ->
        val who = if (last.by == myName) "Você" else last.by
        AlertDialog(
            onDismissRequest = { toConfirm = null },
            title = { Text("${last.type.emoji} Já registraram há pouco") },
            text = { Text("$who já ${last.type.did} às ${last.at.fmtTime()} (${ago(last.at, ZonedDateTime.now())}). Registrar mesmo assim?") },
            confirmButton = { TextButton(onClick = { onRecord(last.type); toConfirm = null }) { Text("Registrar de novo") } },
            dismissButton = { TextButton(onClick = { toConfirm = null }) { Text("Cancelar") } },
        )
    }

    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Apagar registro?") },
            text = { Text("${e.type.emoji} ${e.type.tab} de ${e.at.fmtDayTime()} por ${e.by}.") },
            confirmButton = { TextButton(onClick = { onDelete(e); toDelete = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/** Texto curto sob o botão e a cor dele (verde = meta do colírio cumprida, vermelho = dentes atrasados). */
@Composable
private fun buttonState(
    t: ActionType,
    list: List<CareEvent>,
    eyedrop: EyedropSettings,
    now: ZonedDateTime,
): Pair<String, ButtonColors> {
    val cs = MaterialTheme.colorScheme
    val normal = ButtonDefaults.buttonColors()
    val today = countToday(list, now)
    return when (t) {
        ActionType.FOOD ->
            (if (today > 0) "$today hoje · ${list.first().at.fmtTime()}" else "nenhuma hoje") to normal

        ActionType.EYEDROP ->
            if (eyedrop.goal > 0) {
                val done = today >= eyedrop.goal
                "$today de ${eyedrop.goal} hoje" + (if (done) " ✓" else "") to
                    if (done) ButtonDefaults.buttonColors(containerColor = cs.secondary, contentColor = cs.onSecondary) else normal
            } else "$today hoje" to normal

        ActionType.TEETH -> {
            val days = daysSinceLast(list, now)
            val txt = when (days) {
                null -> "sem registro"
                0L -> "hoje · ${list.first().at.fmtTime()}"
                1L -> "ontem"
                else -> "há $days dias"
            }
            // Sem nenhum registro ainda (recém-instalado) não fica vermelho.
            val late = days != null && days >= TEETH_ALERT_DAYS
            txt to if (late) ButtonDefaults.buttonColors(containerColor = cs.error, contentColor = cs.onError) else normal
        }
    }
}

@Composable
private fun ActionButton(
    t: ActionType,
    status: String,
    colors: ButtonColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(t.emoji, fontSize = 34.sp)
            Text(t.button, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
            Text(status, fontSize = 12.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
