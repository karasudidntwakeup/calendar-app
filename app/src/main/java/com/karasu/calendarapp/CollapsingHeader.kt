package com.karasu.calendarapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A header that collapses "strictly proportional" to the finger's scroll:
 *  - height shrinks 1:1 with the scroll distance (no snap, no animation easing),
 *  - it scrolls away with the finger, and
 *  - lift your finger anywhere -> it stays exactly there.
 *
 * Fully continuous, no auto-expand / auto-close.
 */
@Composable
fun CollapsingHeaderScreen() {
    val listState = rememberLazyListState()
    val collapsedHeight = 56.dp
    val expandedHeight = 220.dp

    val rangePx = with(LocalDensity.current) {
        (expandedHeight - collapsedHeight).toPx()
    }

    // Strictly proportional collapse: 0f = expanded, 1f = collapsed.
    // scrollPx / rangePx is a pure function of the finger's raw pixel scroll.
    val collapseProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                // Header has scrolled out of view; stay collapsed, don't bounce back.
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / rangePx).coerceIn(0f, 1f)
            }
        }
    }
    val expandFactor = 1f - collapseProgress

    // Height in dp stays correct for any density (unlike px/dp arithmetic).
    val headerHeight = expandedHeight - (expandedHeight - collapsedHeight) * collapseProgress

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(Color(0xFFECEFF1)),
    ) {
        item(key = "header") {
            // Fixed slot so list layout never jumps; inner visual shrinks.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(expandedHeight),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.BottomStart),
                ) {
                    Text(
                        "Scroll down — header collapses with your finger",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .padding(16.dp)
                            .graphicsLayer { alpha = expandFactor },
                    )
                }
            }
        }

        items(60) { i ->
            Text(
                "Item $i",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(if (i % 2 == 0) Color.White else Color(0xFFD0D6DC))
                    .padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
