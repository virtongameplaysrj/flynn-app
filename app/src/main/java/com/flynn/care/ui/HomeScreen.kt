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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flynn.care.BuildConfig
import com.flynn.care.data.CareItem
import com.flynn.care.data.Feeding
import com.flynn.care.data.feedingsToday
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import java.time.ZonedDateTime

@Composable
fun HomeScreen(
    email: String,
    total: Long,
    recent: List<Feeding>,
    care: List<CareItem>,
    onFeed: () -> Unit,
    onDeleteFeeding: (Feeding) -> Unit,
) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = ZonedDateTime.now() }
    }
    var toDelete by remember { mutableStateOf<Feeding?>(null) }
    val today = feedingsToday(recent, now)
    val upcoming = care.filter { !it.done && it.localDate() != null }.sortedBy { it.date }.take(4)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { Text("Flynn 🐶", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Refeições hoje", style = MaterialTheme.typography.titleMedium)
                    Text("$today", fontSize = 72.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Total de cliques: $total", style = MaterialTheme.typography.bodyMedium)
                    recent.firstOrNull()?.let {
                        Text(
                            "Última: ${it.at.fmtTime()} (${ago(it.at, now)}) por ${it.by}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = onFeed,
                modifier = Modifier.fillMaxWidth().height(96.dp),
                shape = RoundedCornerShape(28.dp),
            ) { Text("🍖  Dei comida", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
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
            item { Text("Refeições recentes", style = MaterialTheme.typography.titleMedium) }
            items(recent.take(10), key = { "f" + it.id }) { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.at.fmtDayTime()} — ${f.by}", Modifier.weight(1f))
                    IconButton(onClick = { toDelete = f }) { Icon(Icons.Default.Delete, contentDescription = "Apagar") }
                }
            }
        }

        item {
            TextButton(onClick = { FirebaseAuth.getInstance().signOut() }) {
                Text("Sair ($email) · v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    toDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Apagar registro?") },
            text = { Text("Refeição de ${f.at.fmtDayTime()} por ${f.by}.") },
            confirmButton = { TextButton(onClick = { onDeleteFeeding(f); toDelete = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}
