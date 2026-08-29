package com.karasu.calendarapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

/**
 * Standalone demo of the fully-proportional collapsing header.
 * Launched separately from the calendar so nothing in the existing
 * CalendarApp flow is changed.
 */
class CollapsingHeaderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    CollapsingHeaderScreen()
                }
            }
        }
    }
}
