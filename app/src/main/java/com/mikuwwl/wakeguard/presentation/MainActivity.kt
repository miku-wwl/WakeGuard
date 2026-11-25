package com.mikuwwl.wakeguard.presentation

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikuwwl.wakeguard.R
import com.mikuwwl.wakeguard.data.AlarmEntity
import com.mikuwwl.wakeguard.data.WakeEventEntity
import com.mikuwwl.wakeguard.data.WakeGuardRepository
import com.mikuwwl.wakeguard.data.WakeOutcome
import com.mikuwwl.wakeguard.domain.VerificationPolicy
import com.mikuwwl.wakeguard.vision.CameraVerificationScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val resumeCounter = mutableLongStateOf(0L)
    private val repository by lazy { WakeGuardRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            repository.activeEvent.collect { event ->
                setShowWhenLocked(event != null)
                setTurnScreenOn(event != null)
            }
        }
        setContent {
            WakeGuardRoot(
                repository = repository,
                refreshToken = resumeCounter.longValue,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCounter.longValue += 1
        lifecycleScope.launch(Dispatchers.IO) { repository.restoreOnForeground() }
    }
}

private object Route {
    const val HOME = "home"
    const val EDITOR = "editor"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val RINGING = "ringing"
    const val VERIFY = "verify"
    const val RESULT = "result"
}

@Composable
private fun WakeGuardRoot(
    repository: WakeGuardRepository,
    refreshToken: Long,
) {
    val alarms by repository.alarms.collectAsStateWithLifecycle(emptyList())
    val activeEvent by repository.activeEvent.collectAsStateWithLifecycle(null)
    val events by repository.recentEvents.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    var editedAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var emergencyEventId by remember { mutableStateOf<Long?>(null) }
    var resultWasSuccess by rememberSaveable { mutableStateOf(false) }
    var resultLabel by rememberSaveable { mutableStateOf("") }
    var cameraEventId by remember { mutableStateOf<Long?>(null) }
    var permissionUpdates by remember { mutableLongStateOf(0L) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionUpdates++
    }
    val eventId = activeEvent?.id
    val screen = when {
        eventId == null -> route
        eventId == cameraEventId -> Route.VERIFY
        else -> Route.RINGING
    }
    val activePolicy = remember(eventId) { VerificationPolicy.production }
    val exactPermission = remember(refreshToken) { repository.canScheduleExactAlarms() }

    BackHandler(enabled = eventId != null && screen == Route.RINGING) { /* Back never completes an alarm. */ }

    WakeGuardTheme {
        when (screen) {
            Route.EDITOR -> AlarmEditorScreen(
                alarm = editedAlarm,
                onCancel = { route = Route.HOME },
                onSave = { alarm -> scope.launch { repository.saveAlarm(alarm); route = Route.HOME } },
            )
            Route.RINGING -> AlarmRingingScreen(
                onStartVerification = {
                    if (eventId != null) scope.launch {
                        if (repository.beginVerification(eventId)) cameraEventId = eventId
                    }
                },
                onEmergencyStop = { emergencyEventId = eventId },
            )
            Route.VERIFY -> if (eventId != null) androidx.compose.runtime.key(eventId) {
                CameraVerificationScreen(
                    eventId = eventId,
                    policy = activePolicy,
                    onVerified = {
                        scope.launch {
                            if (repository.finishVerified(eventId)) {
                                resultWasSuccess = true
                                resultLabel = "已通过本次现场洗漱场景识别"
                                route = Route.RESULT
                            }
                        }
                    },
                    onEmergencyStop = { emergencyEventId = eventId },
                    onBackToAlarm = { cameraEventId = null },
                )
            }
            Route.RESULT -> ResultScreen(
                success = resultWasSuccess,
                detail = resultLabel,
                onDone = { route = Route.HOME },
            )
            else -> Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = route == Route.HOME, onClick = { route = Route.HOME }, icon = { Icon(painterResource(R.drawable.ic_wakeguard), null) }, label = { Text("闹钟") })
                        NavigationBarItem(selected = route == Route.STATS, onClick = { route = Route.STATS }, icon = { Icon(painterResource(R.drawable.ic_statistics), null) }, label = { Text("统计") })
                        NavigationBarItem(selected = route == Route.SETTINGS, onClick = { route = Route.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("设置") })
                    }
                },
                floatingActionButton = {
                    if (route == Route.HOME) FloatingActionButton(
                        modifier = Modifier.testTag("add_alarm"),
                        onClick = { editedAlarm = null; route = Route.EDITOR },
                    ) { Icon(Icons.Default.Add, contentDescription = "新增闹钟") }
                },
            ) { insets ->
                when (route) {
                    Route.HOME -> AlarmListScreen(
                        alarms = alarms,
                        exactPermission = exactPermission,
                        onEdit = { editedAlarm = it; route = Route.EDITOR },
                        onToggle = { alarm, enabled -> scope.launch { repository.setEnabled(alarm, enabled) } },
                        onDelete = { alarm -> scope.launch { repository.deleteAlarm(alarm.id) } },
                        modifier = Modifier.padding(insets),
                    )
                    Route.STATS -> StatisticsScreen(events, Modifier.padding(insets))
                    else -> androidx.compose.runtime.key(refreshToken, permissionUpdates) {
                        SettingsScreen(repository, notificationPermission, Modifier.padding(insets))
                    }
                }
            }
        }
    }

    val stoppingId = emergencyEventId
    if (stoppingId != null && stoppingId == eventId) {
        AlertDialog(
            onDismissRequest = { emergencyEventId = null },
            title = { Text("紧急停止闹钟？") },
            text = { Text("这会停止声音并记录为未完成验证，不会计为成功。") },
            confirmButton = {
                Button(onClick = {
                    emergencyEventId = null
                    scope.launch {
                        if (repository.finishEmergency(stoppingId)) {
                            resultWasSuccess = false
                            resultLabel = "已紧急停止；本次未完成验证"
                            route = Route.RESULT
                        }
                    }
                }) { Text("紧急停止") }
            },
            dismissButton = { TextButton(onClick = { emergencyEventId = null }) { Text("继续验证") } },
        )
    }
}

@Composable
private fun AlarmListScreen(
    alarms: List<AlarmEntity>,
    exactPermission: Boolean,
    onEdit: (AlarmEntity) -> Unit,
    onToggle: (AlarmEntity, Boolean) -> Unit,
    onDelete: (AlarmEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nextAlarm = if (exactPermission) alarms.asSequence()
        .filter { it.enabled && it.deliveryToken != null }.mapNotNull { it.scheduledAt }.minOrNull()
    else null
    var deleteAlarm by remember { mutableStateOf<AlarmEntity?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("alarm_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("WakeGuard", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("把清醒带到洗手台", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (!exactPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("精确闹钟权限未开启。请前往“设置”授权，闹钟才能按时触发。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            nextAlarm?.let { next ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("下一个已排程闹钟", style = MaterialTheme.typography.labelLarge)
                        Text(formatDateTime(next), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (alarms.isEmpty()) item { EmptyAlarmsCard() }
        items(alarms, key = { it.id }) { alarm ->
            AlarmCard(alarm, exactPermission, onEdit = { onEdit(alarm) }, onToggle = { onToggle(alarm, it) }, onDelete = { deleteAlarm = alarm })
        }
    }
    deleteAlarm?.let { alarm ->
        AlertDialog(
            onDismissRequest = { deleteAlarm = null },
            title = { Text("删除闹钟？") },
            text = { Text("${formatTime(alarm.hour, alarm.minute)} 将从列表中移除。") },
            confirmButton = { Button(onClick = { onDelete(alarm); deleteAlarm = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteAlarm = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EmptyAlarmsCard() {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_wakeguard), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text("还没有闹钟", style = MaterialTheme.typography.titleMedium)
            Text("添加一个，让明早从清醒开始。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlarmCard(alarm: AlarmEntity, exactPermission: Boolean, onEdit: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatTime(alarm.hour, alarm.minute), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Light)
                    Text(alarm.label.ifBlank { "WakeGuard alarm" }, style = MaterialTheme.typography.titleSmall)
                    Text(repeatLabel(alarm.repeatMask), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    if (alarm.enabled && (!exactPermission || alarm.deliveryToken == null)) {
                        Text("尚未排程", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(checked = alarm.enabled, onCheckedChange = onToggle, modifier = Modifier.testTag("alarm_enabled"))
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_alarm")) { Icon(Icons.Default.Edit, contentDescription = "编辑闹钟 ${formatTime(alarm.hour, alarm.minute)}") }
                IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_alarm")) { Icon(Icons.Default.Delete, contentDescription = "删除闹钟 ${formatTime(alarm.hour, alarm.minute)}") }
            }
        }
    }
}

@Composable
private fun AlarmEditorScreen(alarm: AlarmEntity?, onCancel: () -> Unit, onSave: (AlarmEntity) -> Unit) {
    val context = LocalContext.current
    val now = remember { LocalTime.now().plusMinutes(1) }
    var hour by remember(alarm?.id) { mutableStateOf(alarm?.hour ?: now.hour) }
    var minute by remember(alarm?.id) { mutableStateOf(alarm?.minute ?: now.minute) }
    var label by remember(alarm?.id) { mutableStateOf(alarm?.label ?: "WakeGuard alarm") }
    var repeatMask by remember(alarm?.id) { mutableStateOf(alarm?.repeatMask ?: 127) }
    var vibration by remember(alarm?.id) { mutableStateOf(alarm?.vibration ?: true) }
    var ringtoneUri by remember(alarm?.id) { mutableStateOf(alarm?.ringtoneUri) }
    var showTimeDialog by remember { mutableStateOf(false) }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            ringtoneUri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }?.toString()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text(if (alarm == null) "新建闹钟" else "编辑闹钟", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(formatTime(hour, minute), Modifier.fillMaxWidth().clickable { showTimeDialog = true }.padding(vertical = 18.dp).testTag("alarm_time"), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(onClick = { showTimeDialog = true }) { Text("选择时间") }
            }
            item {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("alarm_label"))
            }
            item {
                Text("重复日期", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayLabels.forEachIndexed { index, day ->
                        val selected = repeatMask and (1 shl index) != 0
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { repeatMask = if (selected) repeatMask and (1 shl index).inv() else repeatMask or (1 shl index) },
                            contentAlignment = Alignment.Center,
                        ) { Text(day, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Text(if (repeatMask == 0) "仅响铃一次" else repeatLabel(repeatMask), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("振动", style = MaterialTheme.typography.titleMedium); Text("闹钟响铃时振动", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(checked = vibration, onCheckedChange = { vibration = it })
                }
                OutlinedButton(onClick = {
                    val picker = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                    }
                    ringtonePicker.launch(picker)
                }) { Text(if (ringtoneUri == null) "默认铃声" else "已选择自定义铃声 · 更改") }
            }
        }
        Button(
            onClick = {
                val saved = alarm ?: AlarmEntity(hour = hour, minute = minute)
                onSave(saved.copy(
                    label = label.trim().ifBlank { "WakeGuard alarm" },
                    hour = hour,
                    minute = minute,
                    repeatMask = repeatMask,
                    vibration = vibration,
                    ringtoneUri = ringtoneUri,
                ))
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).testTag("save_alarm"),
        ) { Text("保存闹钟") }
    }

    if (showTimeDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showTimeDialog = false }) {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("设置时间", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    TimePickerDialog(
                        context = context,
                        initialHour = hour,
                        initialMinute = minute,
                        onConfirm = { h, m -> hour = h; minute = m; showTimeDialog = false },
                        onCancel = { showTimeDialog = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePickerDialog(context: android.content.Context, initialHour: Int, initialMinute: Int, onConfirm: (Int, Int) -> Unit, onCancel: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(initialHour, initialMinute) {
        val dialog = android.app.TimePickerDialog(context, { _, hour, minute -> onConfirm(hour, minute) }, initialHour, initialMinute, true)
        dialog.setOnCancelListener { onCancel() }
        dialog.show()
        onDispose { dialog.dismiss() }
    }
}

@Composable
private fun AlarmRingingScreen(onStartVerification: () -> Unit, onEmergencyStop: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1D1B)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0xFFB5E8D0)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_wakeguard), contentDescription = null, tint = Color(0xFF123E34), modifier = Modifier.size(48.dp))
            }
            Text("正在响铃，请起床。", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("请前往洗手池附近。", color = Color(0xFFC2D3D0), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onStartVerification, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_verification")) { Text("开始现场验证") }
            OutlinedButton(
                onClick = onEmergencyStop,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) { Text("紧急停止（记录为未完成）") }
            Text("返回键不会关闭闹钟。按 Home 后，声音与通知会继续。", color = Color(0xFF93AAA5), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ResultScreen(success: Boolean, detail: String, onDone: () -> Unit) {
    val color = if (success) Color(0xFF0B6B55) else Color(0xFF8E3D32)
    Box(Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (success) "验证完成" else "闹钟已停止", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(detail, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onDone) { Text("返回闹钟列表") }
        }
    }
}

@Composable
private fun StatisticsScreen(events: List<WakeEventEntity>, modifier: Modifier = Modifier) {
    val success = events.count { it.outcome == WakeOutcome.VERIFIED }
    val emergency = events.count { it.outcome == WakeOutcome.EMERGENCY_STOP }
    val failedAttempts = events.sumOf { it.failedAttempts }
    val completed = events.count { it.finishedAt != null }
    val rate = if (completed == 0) 0 else success * 100 / completed
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("起床统计", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("记录保存在这台设备上", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("验证成功", success.toString(), Modifier.weight(1f))
                StatCard("成功率", "$rate%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("紧急停止", emergency.toString(), Modifier.weight(1f))
                StatCard("未通过次数", failedAttempts.toString(), Modifier.weight(1f))
            }
            Text("最近记录", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        }
        if (events.isEmpty()) item { Text("闹钟触发后，历史记录会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(events, key = { it.id }) { event -> EventRow(event) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventRow(event: WakeEventEntity) {
    val time = formatDateTime(event.triggeredAt)
    val result = when (event.outcome) {
        WakeOutcome.VERIFIED -> "验证成功"
        WakeOutcome.EMERGENCY_STOP -> "紧急停止 · 未验证"
        WakeOutcome.INTERRUPTED -> "系统中断"
        WakeOutcome.MISSED -> "错过闹钟"
        WakeOutcome.FAILED -> "无法响铃"
        WakeOutcome.VERIFYING, WakeOutcome.RINGING -> "进行中"
        else -> event.outcome
    }
    val duration = if (event.finishedAt != null && event.verificationStartedAt != null) {
        " · 验证 ${((event.finishedAt - event.verificationStartedAt) / 1000).coerceAtLeast(0)} 秒"
    } else ""
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(time, fontWeight = FontWeight.Medium); Text(result + duration, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SettingsScreen(
    repository: WakeGuardRepository,
    notificationPermission: androidx.activity.result.ActivityResultLauncher<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val alarmManager = remember(context) { context.getSystemService(AlarmManager::class.java) }
    val notifications = remember(context) { context.getSystemService(NotificationManager::class.java) }
    var showLicenses by remember { mutableStateOf(false) }
    val modelStatus = remember {
        runCatching { context.assets.open("efficientdet_lite0.tflite").close(); context.assets.open("toothpaste_classifier.onnx").close() }.isSuccess
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("设置与权限", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("检查 Android 授权和本地识别状态", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PermissionCard("精确闹钟", if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) "已允许" else "未允许") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                }
            }
        }
        item {
            val notificationGranted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            PermissionCard("通知", if (notificationGranted) "已允许" else "未允许") {
                if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        item {
            val fullScreenAllowed = Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent()
            PermissionCard("锁屏全屏闹钟", if (fullScreenAllowed) "已允许" else "未允许 · 通知仍可打开") {
                if (Build.VERSION.SDK_INT >= 34) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }
            }
        }
        item {
            val cameraModelReady = modelStatus
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("离线视觉模型", style = MaterialTheme.typography.titleMedium)
                    Text(if (cameraModelReady) "检测模型与牙膏分类器已就绪" else "模型文件不完整；验证将保持失败关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("只处理相机实时帧，不保存照片。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("现场验证规则", style = MaterialTheme.typography.titleMedium)
                    Text("本次闹钟规则固定：在 12 秒内的实时画面中识别洗手池和牙刷或牙膏，并确认至少两帧。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("返回、切到后台或关闭相机后会清除本次识别进度。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { TextButton(onClick = { showLicenses = true }) { Text("查看第三方模型许可") } }
        item { Text("Android 可能限制后台启动、全屏通知、精确闹钟和省电策略。系统强制停止或关机无法由普通应用阻止。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (showLicenses) {
        val notices = remember { runCatching { context.assets.open("third_party_notices.txt").bufferedReader().use { it.readText() } }.getOrDefault("第三方许可说明暂不可用。") }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("第三方模型许可") },
            text = { Text(notices, Modifier.height(360.dp).verticalScroll(rememberScrollState()).padding(4.dp), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun PermissionCard(title: String, status: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("管理", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun WakeGuardTheme(content: @Composable () -> Unit) {
    val light = lightColorScheme(
        primary = Color(0xFF0B6B60), onPrimary = Color.White,
        primaryContainer = Color(0xFFC7F1E5), onPrimaryContainer = Color(0xFF00201B),
        secondary = Color(0xFF4A635C), background = Color(0xFFF4F7F5), surface = Color(0xFFF4F7F5),
    )
    val dark = darkColorScheme(
        primary = Color(0xFF8ED8C3), secondary = Color(0xFFB1CCC3),
        background = Color(0xFF101816), surface = Color(0xFF101816),
        primaryContainer = Color(0xFF214A41), onPrimaryContainer = Color(0xFFC7F1E5),
    )
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) dark else light, content = content)
}

private fun formatTime(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)

private fun repeatLabel(mask: Int): String = when (mask) {
    0 -> "仅一次"
    127 -> "每天"
    31 -> "周一至周五"
    96 -> "周末"
    else -> listOf("一", "二", "三", "四", "五", "六", "日").mapIndexedNotNull { index, label -> if (mask and (1 shl index) != 0) "周$label" else null }.joinToString("、")
}

private fun formatDateTime(timestamp: Long): String = DateTimeFormatter.ofPattern("M月d日 E HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
