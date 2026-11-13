package ro.fitnesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveable
import ro.fitnesshub.ui.navigation.AppNavHost
import ro.fitnesshub.ui.theme.FitnessHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FitnessHubApp).container
        setContent {
            FitnessHubTheme {
                // Decided once; rememberSaveable keeps the same answer after a rotation,
                // when the navigation graph is rebuilt with its saved back stack.
                val startAtSignIn = rememberSaveable { container.startsAtSignIn() }
                AppNavHost(startAtSignIn)
            }
        }
    }
}
