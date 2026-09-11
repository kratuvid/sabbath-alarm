package com.mns.suneventalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.mns.suneventalarm.data.AlarmRepository
import com.mns.suneventalarm.data.LocationRepository
import com.mns.suneventalarm.data.SunAlarm
import com.mns.suneventalarm.data.SunEventType
import com.mns.suneventalarm.scheduler.AlarmScheduler
import com.mns.suneventalarm.ui.theme.SabbathAlarmTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import androidx.glance.appwidget.updateAll

data class PlaceResult(val name: String, val lat: String, val lon: String)

suspend fun searchPlaces(query: String): List<PlaceResult> {
    return withContext(Dispatchers.IO) {
        try {
            if (query.length < 3) return@withContext emptyList()
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val connection = URL("https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=5").openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "SunEventAlarmApp")
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(response)
            val results = mutableListOf<PlaceResult>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                results.add(PlaceResult(
                    name = item.getString("display_name"),
                    lat = item.getString("lat"),
                    lon = item.getString("lon")
                ))
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || 
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchLocationAndSave()
        } else {
            Toast.makeText(this, "Location permission is required for sun events", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            SabbathAlarmTheme {
                MainScreen()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            requestPermissionLauncher.launch(notGranted.toTypedArray())
        } else {
            val locRepo = LocationRepository(this)
            if (locRepo.getLocation() == null) {
                fetchLocationAndSave()
            }
        }
    }

    private fun fetchLocationAndSave() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    LocationRepository(this).saveLocation(location.latitude, location.longitude)
                    Toast.makeText(this, "Location saved!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { AlarmRepository(context) }
    val scheduler = remember { AlarmScheduler(context) }
    
    var alarms by remember { mutableStateOf(repository.getAlarms()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(alarms) {
        com.mns.suneventalarm.widget.NextAlarmWidget().updateAll(context)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.main_bg),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            alpha = 0.25f
        )
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Sabbath Alarm", fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = androidx.compose.foundation.shape.CircleShape,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(alarms) { alarm ->
                AlarmItem(
                    alarm = alarm,
                    scheduler = scheduler,
                    onToggle = { isEnabled ->
                        val updated = alarm.copy(isEnabled = isEnabled)
                        repository.updateAlarm(updated)
                        alarms = repository.getAlarms()
                        if (isEnabled) scheduler.scheduleAlarm(updated) else scheduler.cancelAlarm(updated.id)
                    },
                    onDelete = {
                        scheduler.cancelAlarm(alarm.id)
                        repository.deleteAlarm(alarm.id)
                        alarms = repository.getAlarms()
                    }
                )
            }
        }

        if (showAddDialog) {
            AddAlarmDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { newAlarm ->
                    if (alarms.any { it.isDuplicateOf(newAlarm) }) {
                        Toast.makeText(context, "This exact alarm already exists!", Toast.LENGTH_SHORT).show()
                    } else {
                        repository.addAlarm(newAlarm)
                        alarms = repository.getAlarms()
                        scheduler.scheduleAlarm(newAlarm)
                        showAddDialog = false
                    }
                }
            )
        }

        if (showSettingsDialog) {
            SettingsDialog(
                onDismiss = {
                    showSettingsDialog = false
                    val activeAlarms = repository.getAlarms().filter { it.isEnabled }
                    activeAlarms.forEach { scheduler.scheduleAlarm(it) }
                    alarms = repository.getAlarms()
                },
                locRepo = LocationRepository(context)
            )
        }
    }
}
}

@Composable
fun AlarmItem(alarm: SunAlarm, scheduler: AlarmScheduler, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    var nextTriggerTime by remember(alarm, alarm.isEnabled) { mutableStateOf<java.time.ZonedDateTime?>(null) }
    var timeInfoText by remember { mutableStateOf("") }

    LaunchedEffect(alarm, alarm.isEnabled) {
        if (alarm.isEnabled) {
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                nextTriggerTime = scheduler.calculateNextTriggerTime(alarm)
            }
        } else {
            nextTriggerTime = null
            timeInfoText = "Alarm disabled"
        }
    }

    LaunchedEffect(nextTriggerTime) {
        if (nextTriggerTime != null) {
            while (true) {
                val now = java.time.ZonedDateTime.now()
                val duration = java.time.Duration.between(now, nextTriggerTime)
                if (duration.isNegative || duration.isZero) {
                    timeInfoText = "Firing now"
                } else {
                    val totalMinutes = duration.toMinutes()
                    val days = totalMinutes / (60 * 24)
                    val hours = (totalMinutes / 60) % 24
                    val minutes = totalMinutes % 60
                    
                    val timeFormatter = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                    val formattedTime = nextTriggerTime!!.format(timeFormatter)

                    val remainingStr = when {
                        days > 0 -> "in $days d, $hours h, $minutes m"
                        hours > 0 -> "in $hours h, $minutes m"
                        else -> "in $minutes m"
                    }
                    timeInfoText = "$formattedTime ($remainingStr)"
                }
                kotlinx.coroutines.delay(60000)
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleCaseEvent = alarm.eventType.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.titlecase() } }
                Text(
                    text = "$titleCaseEvent${if (alarm.offsetMinutes > 0) " +${alarm.offsetMinutes}m" else if (alarm.offsetMinutes < 0) " ${alarm.offsetMinutes}m" else ""}",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    val scheduleText = if (alarm.targetDateMillis != null) {
                        val zdt = java.time.Instant.ofEpochMilli(alarm.targetDateMillis).atZone(java.time.ZoneId.systemDefault())
                        java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).format(zdt)
                    } else if (alarm.daysOfWeek.isNotEmpty()) {
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                        alarm.daysOfWeek.sorted().joinToString(", ") { days[it - 1] }
                    } else {
                        "Everyday"
                    }
                    Text(
                        text = scheduleText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (alarm.useFullScreenIntent) "Full Screen Wakeup" else "Notification Only",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (timeInfoText.isNotEmpty()) {
                        Text(
                            text = timeInfoText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Alarm", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAlarmDialog(onDismiss: () -> Unit, onAdd: (SunAlarm) -> Unit) {
    var selectedEvent by remember { mutableStateOf(SunEventType.SUNSET) }
    var useFullScreen by remember { mutableStateOf(true) }
    var offset by remember { mutableStateOf("0") }
    
    var isRecurring by remember { mutableStateOf(true) }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Sabbath Alarm") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Select Event:", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SunEventType.values().forEach { type ->
                        val titleCaseEvent = type.name.split("_").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.titlecase() } }
                        FilterChip(
                            selected = selectedEvent == type,
                            onClick = { selectedEvent = type },
                            label = { Text(titleCaseEvent) }
                        )
                    }
                }
                
                OutlinedTextField(
                    value = offset,
                    onValueChange = { offset = it },
                    label = { Text("Offset (minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isRecurring, onCheckedChange = { isRecurring = it })
                    Text("Recurring (Weekly)")
                }

                if (isRecurring) {
                    Text("Days of Week:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val days = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
                        days.forEach { (value, label) ->
                            FilterChip(
                                selected = selectedDays.contains(value),
                                onClick = {
                                    selectedDays = if (selectedDays.contains(value)) {
                                        selectedDays - value
                                    } else {
                                        selectedDays + value
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                } else {
                    Button(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        val dateText = datePickerState.selectedDateMillis?.let {
                            val zdt = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.of("UTC"))
                            java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).format(zdt)
                        } ?: "Select Date"
                        Text(dateText)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useFullScreen, onCheckedChange = { useFullScreen = it })
                    Text("Full Screen Wakeup")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val offsetInt = offset.toIntOrNull() ?: 0
                val targetMillis = if (!isRecurring) {
                    datePickerState.selectedDateMillis?.let { utcMillis ->
                        val utcDate = java.time.Instant.ofEpochMilli(utcMillis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                        utcDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                } else null
                
                onAdd(SunAlarm(
                    eventType = selectedEvent, 
                    offsetMinutes = offsetInt, 
                    useFullScreenIntent = useFullScreen,
                    daysOfWeek = if (isRecurring) selectedDays else emptySet(),
                    targetDateMillis = targetMillis
                ))
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit, 
    locRepo: LocationRepository
) {
    var lat by remember { mutableStateOf(locRepo.getLocation()?.first?.toString() ?: "") }
    var lng by remember { mutableStateOf(locRepo.getLocation()?.second?.toString() ?: "") }
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isUserTyping by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        if (isUserTyping && searchQuery.length > 2) {
            isSearching = true
            kotlinx.coroutines.delay(500)
            searchResults = searchPlaces(searchQuery)
            isSearching = false
        } else if (isUserTyping) {
            searchResults = emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Location Settings") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    lat = location.latitude.toString()
                                    lng = location.longitude.toString()
                                    Toast.makeText(context, "GPS Location fetched!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Could not fetch GPS. Ensure location is enabled.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Location permission not granted. Go to app settings.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get Current GPS Location")
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        isUserTyping = true
                    },
                    label = { Text("Search for a city...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (searchResults.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        searchResults.forEach { result ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isUserTyping = false
                                        lat = result.lat
                                        lng = result.lon
                                        searchQuery = result.name
                                        searchResults = emptyList()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Text(
                                    text = result.name,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                Text("Manual Override:", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lng,
                    onValueChange = { lng = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val latDouble = lat.toDoubleOrNull()
                val lngDouble = lng.toDoubleOrNull()
                if (latDouble != null && lngDouble != null) {
                    locRepo.saveLocation(latDouble, lngDouble)
                    Toast.makeText(context, "Location saved! Alarms rescheduled.", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } else {
                    Toast.makeText(context, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
