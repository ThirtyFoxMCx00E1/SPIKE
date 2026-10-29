package dev.spike.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.spike.core.SPK_NAME
import dev.spike.core.SpikeSession
import dev.spike.ui.SpikeApp
import java.io.File

fun main() = application {
    val session = remember { SpikeSession(File(System.getProperty("user.home"), ".spike")) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "SPIKE - $SPK_NAME",
        state = rememberWindowState(width = 1000.dp, height = 720.dp),
    ) { SpikeApp(session) }
}
