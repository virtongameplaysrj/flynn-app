package com.flynn.care.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.flynn.care.data.CareItem
import com.flynn.care.data.ItemType

/** Aba "Saúde": Vacinas, Antipulgas e Exames como sub-abas (a barra inferior comporta no máximo 5). */
@Composable
fun HealthScreen(items: List<CareItem>) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    Column {
        TabRow(selectedTabIndex = sub) {
            ItemType.entries.forEachIndexed { i, t ->
                Tab(
                    selected = sub == i,
                    onClick = { sub = i },
                    text = { Text("${t.emoji} ${t.tab}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
        ItemsScreen(ItemType.entries[sub], items)
    }
}
