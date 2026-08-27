@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.karasu.calendarapp

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Android 14+ denies exact alarms by default; send the user to the
        // "Alarms & reminders" toggle once.
        val am = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            try {
                startActivity(
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(android.net.Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
            }
        }
        setContent {
            CalendarApp()
        }
    }
}

private val WeekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun CalendarApp() {
    val ctx = LocalContext.current
    // Material You: follows the system wallpaper palette like matugen on the desktop.
    val dark = (ctx.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else expressiveLightColorScheme()
    }

    // M3 Expressive: springy motion tokens, bigger shape radii.
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(32.dp),
            extraLarge = RoundedCornerShape(44.dp)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            CalendarScreen()
        }
    }
}

@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current

    val notes = remember { mutableStateMapOf<String, List<TodoEntry>>() }
    LaunchedEffect(Unit) {
        notes.clear()
        notes.putAll(TodoStore.loadAll(ctx))
    }

    var shownYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var shownMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-based
    var selectedKey by remember { mutableStateOf("") }

    fun persist() {
        TodoStore.saveAll(ctx, notes.toMap())
    }

    fun shiftMonth(amount: Int) {
        var m = shownMonth + amount
        var y = shownYear
        while (m < 0) { m += 12; y-- }
        while (m > 11) { m -= 12; y++ }
        shownMonth = m
        shownYear = y
    }

    // When a date is focused, the header chevrons move to the next/previous
    // day (following into the adjacent month when needed).
    fun shiftDay(amount: Int) {
        if (selectedKey.isEmpty()) return
        val parts = selectedKey.split("-")
        val cal = Calendar.getInstance()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        cal.add(Calendar.DAY_OF_MONTH, amount)
        selectedKey = TodoStore.keyFor(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        shownYear = cal.get(Calendar.YEAR)
        shownMonth = cal.get(Calendar.MONTH)
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    val threshold = with(density) { 24.dp.toPx() }
                    awaitEachGesture {
                        awaitFirstDown()
                        var dx = 0f
                        var dy = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            // Weight recent movement so fast flicks count more.
                            dx += change.positionChange().x
                            dy += change.positionChange().y
                            change.consume()

                            val absX = abs(dx)
                            val absY = abs(dy)
                            when {
                                absX >= threshold && absX >= absY -> {
                                    // Dominant horizontal swipe.
                                    if (dx > 0) {
                                        // Left-to-right -> previous day (or month).
                                        if (selectedKey.isEmpty()) shiftMonth(-1) else shiftDay(-1)
                                    } else {
                                        // Right-to-left -> next day (or month).
                                        if (selectedKey.isEmpty()) shiftMonth(1) else shiftDay(1)
                                    }
                                    break
                                }
                                absY >= threshold && absY >= absX -> {
                                    // Dominant vertical swipe.
                                    if (dy > 0) {
                                        // Swipe down -> back to calendar home.
                                        selectedKey = ""
                                    } else {
                                        // Swipe up -> open the focused date's tasks.
                                        if (selectedKey.isEmpty()) {
                                            val t = Calendar.getInstance()
                                            selectedKey = TodoStore.keyFor(
                                                t.get(Calendar.YEAR),
                                                t.get(Calendar.MONTH) + 1,
                                                t.get(Calendar.DAY_OF_MONTH)
                                            )
                                        }
                                    }
                                    break
                                }
                            }
                            if (!change.pressed) break
                        }
                    }
                }
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(bottom = 140.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = Calendar.getInstance().let {
                    String.format(Locale.US, "%s %d", MonthNames[it.get(Calendar.MONTH)], it.get(Calendar.YEAR))
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = android.text.format.DateFormat.format("EEEE, MMMM d, yyyy", System.currentTimeMillis()).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Tapping the compact date label returns to the calendar (home) view.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp,
                onClick = { if (selectedKey.isNotEmpty()) selectedKey = "" }
            ) {
                Column(
                    Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    // Compact month / date navigation row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (selectedKey.isEmpty()) shiftMonth(-1) else shiftDay(-1)
                        }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                        Text(
                            text = if (selectedKey.isEmpty())
                                "${MonthNames[shownMonth]} $shownYear"
                                else TodoStore.prettyDate(selectedKey),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedKey = "" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        IconButton(onClick = {
                            if (selectedKey.isEmpty()) shiftMonth(1) else shiftDay(1)
                        }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }

                    // Animated: full compact grid when no date is focused,
                    // collapses away (fade + scale) when a day is picked.
                    AnimatedContent(
                        targetState = selectedKey.isEmpty(),
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                        },
                        label = "gridCollapse"
                    ) { showGrid ->
                        if (showGrid) {
                            Column {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    for (d in WeekDays) {
                                        Text(
                                            text = d,
                                            modifier = Modifier.weight(1f),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                MonthGrid(
                                    compact = true,
                                    year = shownYear,
                                    month = shownMonth,
                                    notes = notes,
                                    selectedKey = selectedKey,
                                    onDayClick = { key ->
                                        selectedKey = if (selectedKey == key) "" else key
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Animated transition between the home hint and the focused task view.
            AnimatedContent(
                targetState = selectedKey.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                },
                label = "taskFocus"
            ) { isHome ->
                if (isHome) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Tap a day to see its tasks",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Focused task view fills the remaining space.
                    TodoSection(
                        dateKey = selectedKey,
                        entries = notes[selectedKey].orEmpty(),
                        onAdd = {
                            val list = notes[selectedKey].orEmpty().toMutableList()
                            val id = (notes.values.flatMap { it }.maxOfOrNull { it.id } ?: 0L) + 1
                            list.add(TodoEntry(id = id, text = "", done = false))
                            notes[selectedKey] = list
                            persist()
                            id
                        },
                        onChange = { list ->
                            // Never keep blank tasks.
                            val clean = list.filter { it.text.isNotBlank() }
                            if (clean.isEmpty()) notes.remove(selectedKey)
                            else notes[selectedKey] = clean
                            persist()
                        },
                        onClose = { selectedKey = "" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        // Floating countdown card, overlaid at the bottom of the screen.
        TimerCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
    }
}

@Composable
fun MonthGrid(
    compact: Boolean = false,
    year: Int,
    month: Int,
    notes: Map<String, List<TodoEntry>>,
    selectedKey: String,
    onDayClick: (String) -> Unit
) {
    val today = Calendar.getInstance()
    val firstDow = Calendar.getInstance().apply { set(year, month, 1) }.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = Calendar.getInstance().apply { set(year, month + 1, 0) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cellSize = if (compact) 28.dp else 34.dp
    val cellFont = if (compact) 13.sp else 15.sp

    Column {
        val rows = (0 until firstDow + daysInMonth).chunked(7)
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val day = row.getOrNull(i)?.let { it - firstDow + 1 }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.05f)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null && day in 1..daysInMonth) {
                            val key = TodoStore.keyFor(year, month + 1, day)
                            val isToday = today.get(Calendar.YEAR) == year &&
                                today.get(Calendar.MONTH) == month &&
                                today.get(Calendar.DAY_OF_MONTH) == day
                            val isSelected = key == selectedKey
                            val hasNote = notes[key]?.isNotEmpty() == true

                            // Expressive: selected and today both pop with a
                            // springy scale-up and morph into a cookie shape.
                            val emphasized = isSelected || isToday
                            val scale by animateFloatAsState(
                                targetValue = if (emphasized) 1.2f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "dayScale"
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(
                                            if (emphasized) MaterialShapes.Cookie9Sided.toShape()
                                            else CircleShape
                                        )
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { onDayClick(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.toString(),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Normal,
                                        fontSize = cellFont
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (hasNote) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TodoSection(
    dateKey: String,
    entries: List<TodoEntry>,
    onAdd: () -> Long,
    onChange: (List<TodoEntry>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingId by remember(dateKey) { mutableStateOf<Long?>(null) }
    var draftText by remember(dateKey) { mutableStateOf("") }

    val doneCount = entries.count { it.done }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = TodoStore.prettyDate(dateKey),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$doneCount/${entries.size} tasks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Add button lives in the header row so it never overlaps the list.
            IconButton(onClick = {
                val id = onAdd()
                // Instantly open the editor for the freshly added task.
                editingId = id
                draftText = ""
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Back to calendar")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                val isEditing = editingId == entry.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable(enabled = !isEditing) {
                            // Enter edit mode prefilled with the real text so
                            // the task is never shown blank (which looked like
                            // a duplicate/ghost row).
                            editingId = entry.id
                            draftText = entry.text
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Done checkbox
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (entry.done) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (entry.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onChange(entries.map {
                                    if (it.id == entry.id) it.copy(done = !it.done) else it
                                })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (entry.done) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Done",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))

                    if (isEditing) {
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            // Android 17 / expressive: pill-shaped filled field.
                            shape = RoundedCornerShape(18.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        IconButton(onClick = {
                            val text = draftText.trim()
                            if (text.isNotEmpty()) {
                                onChange(entries.map {
                                    if (it.id == entry.id) it.copy(text = text) else it
                                })
                            } else {
                                // Saving with no text drops the task entirely.
                                onChange(entries.filterNot { it.id == entry.id })
                            }
                            editingId = null
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    } else {
                        Text(
                            text = entry.text,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                            maxLines = 2
                        )
                        IconButton(onClick = {
                            onChange(entries.filterNot { it.id == entry.id })
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimerCard(modifier: Modifier = Modifier) {
    var remainingMs by TimerState::remainingMs
    var running by TimerState::running
    var targetTime by TimerState::targetTime
    var totalMs by TimerState::totalMs

    val ctx = LocalContext.current

    // Expressive: the wave amplitude grows as more time has elapsed.
    val elapsedFraction by animateFloatAsState(
        targetValue = if (totalMs > 0)
            ((totalMs - remainingMs).toFloat() / totalMs).coerceIn(0f, 1f)
        else 0f,
        animationSpec = WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "timerProgress"
    )

    // In-app tick while the timer runs. The alarm itself fires through
    // AlarmManager so it works even in the background.
    LaunchedEffect(running) {
        while (running) {
            remainingMs = (targetTime - System.currentTimeMillis()).coerceAtLeast(0)
            if (remainingMs == 0L) {
                running = false
                break
            }
            delay(250)
        }
    }

    fun scheduleAlarm() {
        TimerState.start(ctx)
    }

    fun cancelAlarm() {
        TimerState.cancel(ctx)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val h = remainingMs / 3_600_000
                val m = (remainingMs / 60_000) % 60
                val s = (remainingMs / 1000) % 60
                Text(
                    text = if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                    else String.format(Locale.US, "%02d:%02d", m, s),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = if (remainingMs <= 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                FilledSmallButton("−") {
                    remainingMs = ((remainingMs / 60_000) - 5).coerceIn(1, 3599) * 60_000
                    totalMs = remainingMs
                    if (running) scheduleAlarm()
                }
                Spacer(Modifier.width(6.dp))
                FilledSmallButton("+") {
                    remainingMs = ((remainingMs / 60_000) + 5).coerceIn(1, 3599) * 60_000
                    totalMs = remainingMs
                    if (running) scheduleAlarm()
                }
                Spacer(Modifier.width(6.dp))

                SmallIconButton(
                    icon = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    desc = if (running) "Pause" else "Start",
                    emphasized = true
                ) {
                    if (running) {
                        running = false
                        cancelAlarm()
                    } else {
                        TimerState.start(ctx)
                    }
                }
                Spacer(Modifier.width(6.dp))
                SmallIconButton(icon = Icons.Default.Refresh, desc = "Reset") {
                    running = false
                    cancelAlarm()
                    remainingMs = 25 * 60_000L
                    totalMs = remainingMs
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearWavyProgressIndicator(
                progress = { elapsedFraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (running) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

@Composable
fun FilledSmallButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (emphasized) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
