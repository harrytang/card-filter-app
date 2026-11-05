package app.cardfilter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Without this the status bar icons are white-on-white in light mode on Android 15+.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CardFilterApplication).container
        setContent { App(container) }
    }
}
