package ro.fitnesshub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ro.fitnesshub.ui.navigation.AppNavHost
import ro.fitnesshub.ui.theme.FitnessHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitnessHubTheme {
                AppNavHost()
            }
        }
    }
}
