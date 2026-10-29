package dev.spike.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier
import dev.spike.core.SpikeSession
import dev.spike.ui.SpikeApp
import java.io.File

/** Native (NDK) helper, see src/main/cpp. */
object Native {
    init { System.loadLibrary("spike_native") }
    external fun estimateTokens(text: String): Int
}

/** One session for the whole process so rotation and re-launch keep the conversation. */
object SpikeHolder {
    private var session: SpikeSession? = null
    @Synchronized
    fun get(c: Context): SpikeSession = session ?: SpikeSession(File(c.filesDir, "spike")).also {
        it.estimate = { t -> runCatching { Native.estimateTokens(t) }.getOrElse { (t.length + 3) / 4 } }
        it.localRea = false // REA needs Node.js; use remote-only mode to drive it on the desktop app
        session = it
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val session = SpikeHolder.get(applicationContext)
        setContent { SpikeApp(session, Modifier.systemBarsPadding().imePadding(), localRea = false) }
    }
}
