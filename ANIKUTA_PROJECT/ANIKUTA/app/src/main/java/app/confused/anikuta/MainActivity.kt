package app.confused.anikuta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.confused.anikuta.core.designsystem.theme.AnikutaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Design language principle #1: edge-to-edge
        setContent {
            AnikutaTheme(darkTheme = true) { // Dark is the default (owner preference)
                Surface(modifier = Modifier.fillMaxSize()) {
                    HelloWorld()
                }
            }
        }
    }
}

@Composable
private fun HelloWorld() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "ANIKUTA",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary, // #B1F256 green
        )
    }
}
