package dev.spike.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.spike.core.*

private val Ink = Color(0xFF0E1621)
private val Panel = Color(0xFF17212F)
private val Amber = Color(0xFFFFB02E)
private val Paper = Color(0xFFE6EAF0)
private val Dim = Color(0xFF8A97A8)
private val Red = Color(0xFFFF6B5E)

private val Scheme = darkColorScheme(
    primary = Amber, onPrimary = Color(0xFF1A1200), background = Ink, onBackground = Paper,
    surface = Panel, onSurface = Paper, error = Red, surfaceVariant = Panel, onSurfaceVariant = Dim,
)

@Composable
fun SpikeApp(session: SpikeSession, modifier: Modifier = Modifier, localRea: Boolean = true) {
    var settingsOpen by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = Scheme) {
        Surface(modifier.fillMaxSize(), color = Ink) {
            if (settingsOpen) SettingsPane(session, localRea) { settingsOpen = false }
            else ChatPane(session) { settingsOpen = true }
        }
    }
}

@Composable
private fun ChatPane(session: SpikeSession, openSettings: () -> Unit) {
    val lines by session.lines.collectAsState()
    val busy by session.busy.collectAsState()
    val model by session.settings.collectAsState()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.animateScrollToItem(lines.lastIndex) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SPIKE", fontSize = 22.sp, color = Amber)
                Text("$SPK_NAME on ${model.model}, about ${session.contextTokens()} tokens in context", fontSize = 12.sp, color = Dim)
            }
            TextButton(onClick = { session.clear() }) { Text("Clear") }
            TextButton(onClick = openSettings) { Text("Settings") }
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                if (lines.isEmpty()) item { Text("Ask SPK 1.0 to build, fix or explain something in your workspace.", color = Dim) }
                items(lines) { Line(it) }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message SPK 1.0") }, maxLines = 5)
            if (busy) Button(onClick = { session.stop() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Stop") }
            else Button(onClick = { session.send(input); input = "" }, enabled = input.isNotBlank()) { Text("Send") }
        }
    }
}

@Composable
private fun Line(l: UiLine) {
    when (l.kind) {
        Kind.USER -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(l.text, Modifier.background(Panel, RoundedCornerShape(14.dp)).padding(10.dp), color = Paper)
        }
        Kind.AGENT -> Text(l.text, color = Paper)
        Kind.TOOL -> Text(l.text.take(300), color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Kind.ERROR -> Text(l.text, color = Red)
        Kind.INFO -> Text(l.text, color = Dim, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsPane(session: SpikeSession, localRea: Boolean, close: () -> Unit) {
    var s by remember { mutableStateOf(session.settings.value) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Settings", fontSize = 22.sp, color = Amber)
        Text("Model backend (any OpenAI-compatible endpoint)", color = Dim)
        Field("Base URL", s.baseUrl) { s = s.copy(baseUrl = it) }
        Field("Model", s.model) { s = s.copy(model = it) }
        Field("API key (optional)", s.apiKey, secret = true) { s = s.copy(apiKey = it) }
        Field("Workspace folder (blank = app folder)", s.workspace) { s = s.copy(workspace = it) }
        Toggle("Allow shell commands", s.allowShell) { s = s.copy(allowShell = it) }
        Text("Reverse engineering (REA)", color = Dim)
        if (localRea) {
            Toggle("Enable REA tools (needs Node.js 22.23+, 24.22+ or 26.12+)", s.reaEnabled) { s = s.copy(reaEnabled = it) }
            Field("REA command", s.reaCommand) { s = s.copy(reaCommand = it) }
            Field("Ghidra install folder (optional)", s.reaGhidraDir) { s = s.copy(reaGhidraDir = it) }
            Field("JAVA_HOME for Ghidra (optional)", s.reaJavaHome) { s = s.copy(reaJavaHome = it) }
            Text("Only analyze software you own or are authorized to examine.", color = Dim, fontSize = 12.sp)
        } else {
            Text("REA runs in the Windows app. Turn on remote-only below to send prompts to it.", color = Dim, fontSize = 12.sp)
        }
        Text("HiveMQ bus (watch or drive this agent from another device)", color = Dim)
        Toggle("Enable bus", s.busEnabled) { s = s.copy(busEnabled = it) }
        Field("Broker host", s.busHost) { s = s.copy(busHost = it) }
        Field("Port", s.busPort.toString()) { v -> v.toIntOrNull()?.let { s = s.copy(busPort = it) } }
        Toggle("TLS", s.busTls) { s = s.copy(busTls = it) }
        Field("Username", s.busUser) { s = s.copy(busUser = it) }
        Field("Password", s.busPass, secret = true) { s = s.copy(busPass = it) }
        Field("Channel (use something hard to guess)", s.busChannel) { s = s.copy(busChannel = it) }
        Toggle("Remote-only: send my prompts to another device", s.busRemoteOnly) { s = s.copy(busRemoteOnly = it) }
        Toggle("Trust prompts from the bus: shell, writes, REA (needs a username)", s.busTrustRemote) { s = s.copy(busTrustRemote = it) }
        Text("Untrusted bus prompts run read-only. Only trust them on your own broker with a login.", color = Dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { session.updateSettings(s); close() }) { Text("Save") }
            TextButton(onClick = close) { Text("Cancel") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun Toggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(on, onChange)
    }
}
