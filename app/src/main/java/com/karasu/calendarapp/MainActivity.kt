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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Current deep-link target; bumped on every fresh widget intent. */
    private var deepLinkKey by mutableStateOf("")

    /** Counts intents so the screen can tell one deep link from the next. */
    private var deepLinkGen by mutableStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Keep the opened day across rotation.
        outState.putString(WidgetKit.EXTRA_DATE_KEY, deepLinkKey)
    }

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
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        } else {
            deepLinkKey = savedInstanceState.getString(WidgetKit.EXTRA_DATE_KEY).orEmpty()
        }
        setContent {
            // Read inside the composition: a widget tap (singleTask ->
            // onNewIntent) re-targets the open day in place instead of tearing
            // the whole screen down and rebuilding it.
            CalendarApp(deepLinkKey = deepLinkKey, linkToken = deepLinkGen)
        }
    }

    /**
     * Parses the one-shot widget extra exactly once per intent, then strips it
     * so rotation never replays it.
     */
    private fun handleDeepLink(intent: Intent) {
        deepLinkKey = intent.getStringExtra(WidgetKit.EXTRA_DATE_KEY).orEmpty()
        intent.removeExtra(WidgetKit.EXTRA_DATE_KEY)
        deepLinkGen++
    }
}

private val WeekDays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
private val MonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@Composable
fun CalendarApp(deepLinkKey: String = "", linkToken: Int = 0) {
    val ctx = LocalContext.current
    // Material You: follows the system wallpaper palette like matugen on the desktop.
    // Recomposes by itself when the system flips dark mode.
    val dark = isSystemInDarkTheme()
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
            CalendarScreen(deepLinkKey = deepLinkKey, linkToken = linkToken)
        }
    }
}

@Composable
fun CalendarScreen(deepLinkKey: String = "", linkToken: Int = 0) {
    val ctx = LocalContext.current

    val notes = remember { mutableStateMapOf<String, List<TodoEntry>>() }
    LaunchedEffect(Unit) {
        // Yesterday's leftovers arrive in today before anything is drawn.
        TodoStore.rollOverUnfinished(ctx)
        notes.clear()
        notes.putAll(TodoStore.loadAll(ctx))
    }
    // Staying open across midnight counts too: wake up, carry the leftovers
    // over, repaint.
    LaunchedEffect(Unit) {
        while (true) {
            val now = Calendar.getInstance()
            val midnight = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            delay((midnight.timeInMillis - now.timeInMillis).coerceAtLeast(1_000L))
            TodoStore.rollOverUnfinished(ctx)
            notes.clear()
            notes.putAll(TodoStore.loadAll(ctx))
            WidgetKit.refreshWidgets(ctx)
        }
    }

    var shownYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var shownMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-based
    var selectedKey by remember { mutableStateOf(deepLinkKey) }

    // A widget tap re-targets the open day (and the grid month that holds it)
    // in place. Nothing is torn down, so there is no empty first frame.
    LaunchedEffect(linkToken) {
        selectedKey = deepLinkKey
        val parts = deepLinkKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year != null && month != null) {
            shownYear = year
            shownMonth = month - 1
        }
    }

    // Hoisted so the swipe handler can tell when the task list is at its top
    // (and a swipe-down should return to the calendar home instead of scrolling).
    val todoListState = rememberLazyListState()
    val monthListState = rememberLazyListState()

    // Every task the grid month holds, earliest first: what fills the space
    // under the calendar while no day is focused.
    val monthPrefix = "%04d-%02d".format(shownYear, shownMonth + 1)
    val monthTasks = notes
        .filterKeys { it.startsWith(monthPrefix) }
        .toSortedMap()
        .flatMap { (key, list) ->
            list.filter { it.text.isNotBlank() }.map { key to it }
        }
    val monthOpen = monthTasks.count { !it.second.done }

    fun persist() {
        TodoStore.saveAll(ctx, notes.toMap())
        WidgetKit.refreshWidgets(ctx)
    }

    // Leaving a day drops blank drafts so ghost rows never linger.
    fun dismissDay() {
        val key = selectedKey
        if (key.isNotEmpty()) {
            val clean = notes[key].orEmpty().filter { it.text.isNotBlank() }
            if (clean.isEmpty()) notes.remove(key) else notes[key] = clean
            persist()
            selectedKey = ""
        }
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
                        val down = awaitFirstDown()
                        var dx = 0f
                        var dy = 0f
                        var prevX = down.position.x
                        var prevY = down.position.y
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            // Scrollables consume vertical drags, which makes
                            // positionChange() report zero: track raw positions
                            // so swipes are still detected everywhere.
                            val x = change.position.x
                            val y = change.position.y
                            dx += x - prevX
                            dy += y - prevY
                            prevX = x
                            prevY = y

                            val absX = abs(dx)
                            val absY = abs(dy)
                            when {
                                absX >= threshold && absX >= absY -> {
                                    // Dominant horizontal swipe (nothing else
                                    // scrolls horizontally, so this is safe).
                                    change.consume()
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
                                    if (selectedKey.isNotEmpty()) {
                                        // A day's task list is showing. It owns
                                        // vertical scrolling, but a swipe-down
                                        // while already at the top goes home.
                                        if (dy > 0 && !todoListState.canScrollBackward) {
                                            change.consume()
                                            dismissDay()
                                        }
                                    } else {
                                        // Calendar home: vertical is navigation,
                                        // unless the month list is scrolled and
                                        // wants the drag for itself.
                                        if (monthListState.canScrollBackward) break
                                        change.consume()
                                        if (dy > 0) {
                                            // Swipe down on home -> nothing to scroll.
                                        } else {
                                            // Swipe up -> open the focused date's tasks.
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
            Spacer(Modifier.height(10.dp))
            Text(
                text = android.text.format.DateFormat.format("EEEE, MMMM d, yyyy", System.currentTimeMillis()).toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(10.dp))

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
                    MonthTaskList(
                        monthName = "${MonthNames[shownMonth]} $shownYear",
                        tasks = monthTasks,
                        openCount = monthOpen,
                        listState = monthListState,
                        onDayClick = { selectedKey = it },
                        onCheck = { dayKey, entry ->
                            val list = notes[dayKey].orEmpty()
                            notes[dayKey] = list.map {
                                if (it.id == entry.id) it.advance() else it
                            }
                            persist()
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    // Focused task view fills the remaining space.
                    TodoSection(
                        dateKey = selectedKey,
                        entries = notes[selectedKey].orEmpty(),
                        listState = todoListState,
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

/**
 * The whole grid month at a glance: every task of every day, grouped under
 * its date, filling the space under the calendar while no day is focused.
 * Tapping a row jumps straight to that day.
 */
@Composable
fun MonthTaskList(
    monthName: String,
    tasks: List<Pair<String, TodoEntry>>,
    openCount: Int,
    listState: LazyListState,
    onDayClick: (String) -> Unit,
    onCheck: (String, TodoEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing planned in $monthName",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$openCount open",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp)
        ) {
            itemsIndexed(tasks) { index, (dayKey, entry) ->
                // A header only when the date changes, so a day with three
                // tasks shows its name once.
                if (index == 0 || tasks[index - 1].first != dayKey) {
                    Text(
                        TodoStore.prettyDate(dayKey),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 26.dp, top = 10.dp, bottom = 2.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDayClick(dayKey) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The dot cycles the task on its own; the rest of the row
                    // still opens that day.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onCheck(dayKey, entry) },
                        contentAlignment = Alignment.Center
                    ) {
                        TaskStateDot(entry, size = 20.dp)
                    }
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The task's state as one circle: empty, split in half, or ticked. */
@Composable
fun TaskStateDot(entry: TodoEntry, scale: Float = 1f, size: Dp = 26.dp) {
    val shape = Modifier
        .size(size)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    when {
        entry.done -> Box(
            modifier = shape.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.6f)
            )
        }
        entry.half -> HalfDoneCircle(
            fill = MaterialTheme.colorScheme.primary,
            ring = MaterialTheme.colorScheme.outlineVariant,
            diameter = size
        )
        else -> Box(modifier = shape.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

/**
 * The circle split in two: the left half is filled, the right half stays
 * empty, with a divider down the middle.
 */
@Composable
fun HalfDoneCircle(fill: Color, ring: Color, diameter: Dp = 26.dp) {
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(
        modifier = Modifier
            .size(diameter)
    ) {
        val inset = stroke / 2f
        val diameter = size.minDimension - stroke
        // Bottom-left quadrant pair = one clean half of the disc.
        drawArc(
            color = fill,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(inset, inset),
            size = Size(diameter, diameter)
        )
        drawCircle(
            color = ring,
            radius = size.minDimension / 2f - inset,
            style = Stroke(width = stroke)
        )
        drawLine(
            color = fill,
            start = Offset(size.width / 2f, inset),
            end = Offset(size.width / 2f, size.height - inset),
            strokeWidth = stroke
        )
    }
}

@Composable
fun TodoSection(
    dateKey: String,
    entries: List<TodoEntry>,
    listState: LazyListState,
    onAdd: () -> Long,
    onChange: (List<TodoEntry>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingId by remember(dateKey) { mutableStateOf<Long?>(null) }
    var draftText by remember(dateKey) { mutableStateOf("") }

    // Blank drafts must never survive: leaving the day drops them so reopening
    // never shows ghost rows or stale "0/1 tasks" counts.
    fun closeClean() {
        onChange(entries.filter { it.text.isNotBlank() })
        editingId = null
        onClose()
    }

    fun saveDraft(entry: TodoEntry) {
        val text = draftText.trim()
        onChange(
            if (text.isEmpty()) entries.filterNot { it.id == entry.id }
            else entries.map { if (it.id == entry.id) it.copy(text = text) else it }
        )
        editingId = null
    }

    fun startEditing(entry: TodoEntry) {
        // Tapping another row while editing saves the open draft first.
        entries.firstOrNull { it.id == editingId }?.let { open ->
            val text = draftText.trim()
            val updated =
                if (text.isEmpty()) entries.filterNot { it.id == open.id }
                else entries.map { if (it.id == open.id) it.copy(text = text) else it }
            onChange(updated)
        }
        editingId = entry.id
        draftText = entry.text
    }

    // Halved so a partly done task reads as "1.5" rather than "3".
    val doneCount = entries.count { it.done } * 2 + entries.count { it.half }
    val countLabel =
        if (doneCount % 2 == 0) "${doneCount / 2}" else "${doneCount / 2}.5"
    val visibleEntries = entries.filter { it.text.isNotBlank() || it.id == editingId }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = TodoStore.prettyDate(dateKey),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // Count pill.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$countLabel/${entries.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            IconButton(onClick = { closeClean() }) {
                Icon(Icons.Default.Close, contentDescription = "Back to calendar")
            }
        }

        if (visibleEntries.isEmpty()) {
            // Empty state.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(28.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No tasks yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tap Add task below to plan this day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp)
            ) {
                items(visibleEntries, key = { it.id }) { entry ->
                    val isEditing = editingId == entry.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(enabled = !isEditing) { startEditing(entry) }
                            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Modern circular toggle: bouncy pop on check, full
                        // 48dp touch target. Three states - tap cycles
                        // empty -> half -> done, so a partly finished task
                        // can be recorded as such.
                        val checkPop by animateFloatAsState(
                            targetValue = when {
                                entry.done -> 1f
                                entry.half -> 0.9f
                                else -> 0.8f
                            },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "checkPop"
                        )
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onChange(entries.map {
                                    if (it.id == entry.id) it.advance() else it
                                }) },
                            contentAlignment = Alignment.Center
                        ) {
                            TaskStateDot(entry, scale = checkPop)
                        }

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
                                textStyle = MaterialTheme.typography.bodyLarge,
                                placeholder = {
                                    Text(
                                        "New task",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    // iOS-style inline clear.
                                    if (draftText.isNotEmpty()) {
                                        IconButton(onClick = { draftText = "" }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(0.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { saveDraft(entry) }
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            // iOS-style send bubble: grey when empty, blue when ready.
                            val canSend = draftText.isNotBlank()
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                    .clickable(enabled = canSend) { saveDraft(entry) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Save",
                                    tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Text(
                                text = entry.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface,
                                textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                                maxLines = 4,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

        // Thumb-friendly add button; disabled while a draft is open.
        FilledTonalButton(
            onClick = {
                val id = onAdd()
                editingId = id
                draftText = ""
            },
            enabled = editingId == null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add task")
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
