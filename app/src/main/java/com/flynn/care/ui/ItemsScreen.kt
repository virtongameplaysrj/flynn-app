@file:OptIn(ExperimentalMaterial3Api::class)

package com.flynn.care.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flynn.care.data.CareItem
import com.flynn.care.data.DEFAULT_HOUR
import com.flynn.care.data.FIXED_OFFSETS
import com.flynn.care.data.ItemType
import com.flynn.care.data.Repo
import java.time.LocalDate

/** Uma aba (Vacinas / Antipulgas / Exames). */
@Composable
fun ItemsScreen(type: ItemType, all: List<CareItem>) {
    var editing by remember { mutableStateOf<CareItem?>(null) }
    val ofType = all.filter { it.type == type }
    val pending = ofType.filter { !it.done }.sortedBy { it.date }
    val done = ofType.filter { it.done }.sortedByDescending { it.date }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        ) {
            item {
                Text("${type.emoji} ${type.tab}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (type == ItemType.EXAM) "Lembretes personalizáveis em cada exame."
                    else "Lembretes: 2 dias antes, 1 dia antes e no dia, às ${DEFAULT_HOUR}h.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (ofType.isEmpty()) item { Text("Nada cadastrado ainda. Toque em + para adicionar.") }
            items(pending, key = { it.id }) { ItemCard(it) { editing = it } }
            if (done.isNotEmpty()) {
                item { Text("Concluídos", style = MaterialTheme.typography.titleMedium) }
                items(done, key = { it.id }) { ItemCard(it) { editing = it } }
            }
        }
        FloatingActionButton(
            onClick = {
                editing = CareItem(type = type, offsets = if (type == ItemType.EXAM) listOf(7, 1, 0) else FIXED_OFFSETS)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Default.Add, contentDescription = "Adicionar") }
    }

    editing?.let { ItemEditor(it, onDismiss = { editing = null }) }
}

@Composable
private fun ItemCard(item: CareItem, onClick: () -> Unit) {
    val days = item.daysUntil()
    val urgent = !item.done && days != null && days <= 2
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (urgent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                Text(
                    listOfNotNull(item.localDate()?.fmt(), days?.takeIf { !item.done }?.let(::relative)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (item.notes.isNotBlank()) {
                    Text(item.notes, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Checkbox(checked = item.done, onCheckedChange = { Repo.save(item.copy(done = it)) })
        }
    }
}

@Composable
private fun ItemEditor(initial: CareItem, onDismiss: () -> Unit) {
    val type = initial.type
    val isNew = initial.id.isBlank()
    var title by remember { mutableStateOf(initial.title) }
    var dose by remember { mutableStateOf(initial.dose) }
    var date by remember { mutableStateOf(initial.localDate() ?: LocalDate.now()) }
    var notes by remember { mutableStateOf(initial.notes) }
    var offsetsText by remember { mutableStateOf(initial.offsets.joinToString(", ")) }
    var hourText by remember { mutableStateOf(initial.hour.toString()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedOffsets = offsetsText.split(',', ' ', ';')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 0..365 }.distinct().sortedDescending()
    val parsedHour = hourText.trim().toIntOrNull()?.takeIf { it in 0..23 }
    val valid = title.isNotBlank() && (type != ItemType.EXAM || parsedHour != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Novo: ${type.singular}" else "Editar ${type.singular}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(titleLabel(type)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (type == ItemType.FLEA) {
                    OutlinedTextField(
                        value = dose, onValueChange = { dose = it },
                        label = { Text("Dose (ex: 1 comprimido, 0,5 ml)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("📅 ${dateLabel(type)}: ${date.fmt()}")
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(if (type == ItemType.EXAM) "Resultados / observações" else "Observações") },
                    minLines = 2, modifier = Modifier.fillMaxWidth(),
                )
                if (type == ItemType.EXAM) {
                    OutlinedTextField(
                        value = offsetsText, onValueChange = { offsetsText = it },
                        label = { Text("Lembrar quantos dias antes? (ex: 7, 1, 0)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = hourText, onValueChange = { hourText = it },
                        label = { Text("Horário do lembrete (0–23h)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (parsedOffsets.isEmpty()) "Sem lembretes para este exame."
                        else "Lembretes: ${parsedOffsets.joinToString { describeOffset(it) }}, às ${parsedHour ?: "?"}h.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text("Lembretes automáticos: 2 dias antes, 1 dia antes e no dia, às ${DEFAULT_HOUR}h.", style = MaterialTheme.typography.bodySmall)
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                Repo.save(
                    initial.copy(
                        title = title.trim(), dose = dose.trim(), date = date.toString(), notes = notes.trim(),
                        offsets = if (type == ItemType.EXAM) parsedOffsets else FIXED_OFFSETS,
                        hour = if (type == ItemType.EXAM) parsedHour ?: DEFAULT_HOUR else DEFAULT_HOUR,
                    )
                )
                onDismiss()
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )

    if (pickDate) {
        PickDateDialog(initial = date, onPicked = { date = it }, onDismiss = { pickDate = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir?") },
            text = { Text("\"${initial.title}\" será apagado nos 2 celulares.") },
            confirmButton = {
                TextButton(onClick = { Repo.delete(initial); confirmDelete = false; onDismiss() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}
