@file:OptIn(ExperimentalMaterial3Api::class)

package com.flynn.care.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flynn.care.data.Repo
import com.flynn.care.data.WeightEntry
import com.flynn.care.data.WeightPoint
import com.flynn.care.data.WeightScale
import com.flynn.care.data.aggregateWeights
import java.time.LocalDate
import kotlin.math.max

/** "8.35" -> "8,35 kg"; "8.0" -> "8 kg". */
fun fmtKg(v: Double): String = "%.2f".format(v).trimEnd('0').trimEnd(',', '.') + " kg"

/** Aba "Peso": registrar pesagens e ver a curva por dia, mês ou ano. */
@Composable
fun WeightScreen(by: String) {
    val entries by remember { Repo.weights() }.collectAsStateWithLifecycle(initialValue = null as List<WeightEntry>?)
    var scale by rememberSaveable { mutableStateOf(WeightScale.DAY) }
    var kgText by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<WeightEntry?>(null) }

    val kg = kgText.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 && it < 150 }
    val list = entries.orEmpty()
    val points = remember(list, scale) { aggregateWeights(list, scale) }
    val newestFirst = remember(list) { list.sortedByDescending { it.date } }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item { Text("⚖️ Peso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        // Registrar pesagem
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = kgText, onValueChange = { kgText = it },
                            label = { Text("Peso (kg)") }, placeholder = { Text("ex: 8,4") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = kgText.isNotBlank() && kg == null,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { pickDate = true }) { Text("📅 ${date.fmt()}") }
                    }
                    Button(
                        enabled = kg != null,
                        onClick = {
                            Repo.saveWeight(date, kg!!, by)
                            kgText = ""
                            date = LocalDate.now()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Salvar peso") }
                }
            }
        }

        // Resumo
        newestFirst.firstOrNull()?.let { last ->
            item {
                val prev = newestFirst.getOrNull(1)
                val diff = prev?.let { last.kg - it.kg }
                Text(
                    buildString {
                        append("Último: ${fmtKg(last.kg)} em ${runCatching { LocalDate.parse(last.date).fmt() }.getOrDefault(last.date)}")
                        if (diff != null) append(" · ${if (diff >= 0) "+" else "−"}${fmtKg(kotlin.math.abs(diff))} desde a anterior")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Curva
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        WeightScale.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = scale == s,
                                onClick = { scale = s },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = WeightScale.entries.size),
                            ) { Text(s.label) }
                        }
                    }
                    when {
                        entries == null -> Text("Carregando…")
                        points.isEmpty() -> Text("Registre a primeira pesagem para ver a curva.")
                        else -> {
                            WeightChart(points)
                            if (scale != WeightScale.DAY) {
                                Text(
                                    "Cada ponto é a média das pesagens do ${if (scale == WeightScale.MONTH) "mês" else "ano"}.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lista de pesagens
        if (newestFirst.isNotEmpty()) {
            item { Text("Pesagens", style = MaterialTheme.typography.titleMedium) }
            items(newestFirst, key = { it.id }) { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        runCatching { LocalDate.parse(w.date).fmt() }.getOrDefault(w.date),
                        Modifier.weight(1f),
                    )
                    Text(fmtKg(w.kg), fontWeight = FontWeight.SemiBold)
                    Text("  ${w.by}", style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { toDelete = w }) { Icon(Icons.Default.Delete, contentDescription = "Apagar") }
                }
            }
        }
    }

    if (pickDate) PickDateDialog(initial = date, onPicked = { date = it }, onDismiss = { pickDate = false })

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Apagar pesagem?") },
            text = { Text("${fmtKg(w.kg)} em ${runCatching { LocalDate.parse(w.date).fmt() }.getOrDefault(w.date)}.") },
            confirmButton = { TextButton(onClick = { Repo.deleteWeight(w.id); toDelete = null }) { Text("Apagar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

/**
 * Gráfico de linha: eixo Y = peso (kg), eixo X = tempo.
 * O X é proporcional à data real, então intervalos maiores entre pesagens aparecem maiores.
 */
@Composable
private fun WeightChart(points: List<WeightPoint>) {
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    Canvas(Modifier.fillMaxWidth().height(220.dp)) {
        val left = 44.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()

        val minKg = points.minOf { it.kg }
        val maxKg = points.maxOf { it.kg }
        val pad = max((maxKg - minKg) * 0.15, 0.2)
        val yMin = minKg - pad
        val yMax = maxKg + pad
        val d0 = points.first().start.toEpochDay()
        val d1 = points.last().start.toEpochDay()

        fun x(p: WeightPoint): Float =
            if (d1 == d0) (left + right) / 2f
            else left + (p.start.toEpochDay() - d0).toFloat() / (d1 - d0) * (right - left)
        fun y(kg: Double): Float = bottom - ((kg - yMin) / (yMax - yMin)).toFloat() * (bottom - top)

        // Linhas de grade + rótulos do eixo Y
        for (i in 0..3) {
            val v = yMin + (yMax - yMin) * i / 3
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
            val t = measurer.measure("%.1f".format(v), labelStyle)
            drawText(t, topLeft = Offset(left - t.size.width - 6.dp.toPx(), yy - t.size.height / 2f))
        }

        // Rótulos do eixo X (até 4, espaçados)
        val idx = if (points.size <= 4) points.indices.toList()
        else listOf(0, points.size / 3, 2 * points.size / 3, points.size - 1)
        idx.distinct().forEach { i ->
            val p = points[i]
            val t = measurer.measure(p.label, labelStyle)
            val half = t.size.width / 2f
            val cx = if (right - left > 2 * half) x(p).coerceIn(left + half, right - half) else x(p)
            drawText(t, topLeft = Offset(cx - half, bottom + 4.dp.toPx()))
        }

        // Curva + pontos
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(x(p), y(p.kg)) else path.lineTo(x(p), y(p.kg))
            }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEach { drawCircle(line, radius = 4.dp.toPx(), center = Offset(x(it), y(it.kg))) }
    }
}
