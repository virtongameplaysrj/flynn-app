package com.flynn.care

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flynn.care.data.CareItem
import com.flynn.care.data.Repo
import com.flynn.care.reminders.ReminderScheduler
import com.flynn.care.ui.HealthScreen
import com.flynn.care.ui.HistoryScreen
import com.flynn.care.ui.HomeScreen
import com.flynn.care.ui.LoginScreen
import com.flynn.care.ui.WeightScreen
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            FlynnTheme { Surface(Modifier.fillMaxSize()) { Root() } }
        }
    }
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFB5651D), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB8), secondary = Color(0xFF6B8E23),
    background = Color(0xFFFFFBF7), surface = Color(0xFFFFFBF7),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB870), onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF6A3C00), secondary = Color(0xFFB5D47A),
)

@Composable
fun FlynnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

@Composable
private fun Root() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    val u = user
    if (u == null) LoginScreen() else MainScaffold(u.email ?: "")
}

@Composable
private fun MainScaffold(email: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val name = email.substringBefore("@")

    // null = ainda carregando (não reagendar alarmes com lista vazia por engano)
    val items by remember { Repo.items() }.collectAsStateWithLifecycle(initialValue = null as List<CareItem>?)
    val total by remember { Repo.totalFeedings() }.collectAsStateWithLifecycle(initialValue = 0L)
    val recent by remember { Repo.recentFeedings() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val error by Repo.lastError.collectAsStateWithLifecycle()
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(items) { items?.let { ReminderScheduler.reschedule(ctx, it) } }
    LaunchedEffect(Unit) { update = UpdateChecker.check() }

    val tabs = listOf("Início" to "🏠", "Histórico" to "📋", "Peso" to "⚖️", "Saúde" to "🩺")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, emoji) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(emoji, fontSize = 20.sp) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            update?.let { u ->
                Banner("Nova versão ${u.version} disponível", "Ver", MaterialTheme.colorScheme.primaryContainer) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                }
            }
            error?.let { e ->
                val msg = if (e.contains("PERMISSION_DENIED")) "Sem permissão: este e-mail não está liberado nas regras do Firebase."
                else "Erro de sincronização: $e"
                Banner(msg, "OK", MaterialTheme.colorScheme.errorContainer) { Repo.lastError.value = null }
            }
            when (tab) {
                0 -> HomeScreen(
                    email = email,
                    total = total,
                    recent = recent,
                    care = items.orEmpty(),
                    onFeed = {
                        val id = Repo.feed(name)
                        scope.launch {
                            val r = snackbar.showSnackbar("Registrado! 🍖", "Desfazer", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) Repo.deleteFeeding(id)
                        }
                    },
                    onDeleteFeeding = { Repo.deleteFeeding(it.id) },
                )
                1 -> HistoryScreen()
                2 -> WeightScreen(by = name)
                else -> HealthScreen(items.orEmpty())
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, color: Color, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}
