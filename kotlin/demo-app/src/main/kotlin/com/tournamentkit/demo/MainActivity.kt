package com.tournamentkit.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

// The single screen of the demo app: shows a greeting.
class MainActivity : ComponentActivity() {
    // Sets the Compose content when the activity is created.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Greeting()
                }
            }
        }
    }
}

// Centered "Hello TournamentKit" text.
@Composable
fun Greeting() {
    Text(
        text = "Hello TournamentKit",
        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center)
    )
}

// Lets Android Studio render the greeting without running the app.
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MaterialTheme {
        Greeting()
    }
}
