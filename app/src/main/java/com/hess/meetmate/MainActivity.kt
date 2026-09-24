package com.hess.meetmate

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hess.meetmate.data.MeetingEntity
import com.hess.meetmate.domain.*
import com.hess.meetmate.integration.ModelDownloadState
import kotlinx.coroutines.delay

private val Ink = Color(0xFF1D2433); private val Muted = Color(0xFF747C8F); private val Canvas = Color(0xFFF7F8FC); private val Violet = Color(0xFF5B5FEF); private val Mint = Color(0xFF13A889)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { MeetMateTheme { MeetMateApp() } } }
}

@Composable fun MeetMateTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = lightColorScheme(primary = Violet, onPrimary = Color.White, background = Canvas, surface = Color.White, onSurface = Ink), typography = Typography(defaultFontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif), content = content) }

private enum class Tab { HOME, HISTORY, SETTINGS }

@Composable fun MeetMateApp(vm: MeetMateViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.HOME) }; var setupOpen by remember { mutableStateOf(false) }; val phase by vm.phase.collectAsState(); val error by vm.error.collectAsState()
    LaunchedEffect(phase) { while (phase == MeetingPhase.LIVE) { vm.tick(); delay(1000) } }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        when (phase) { MeetingPhase.SETUP -> if (setupOpen) SetupScreen(vm) { setupOpen = false } else Scaffold(bottomBar = { if (tab != Tab.SETTINGS) BottomBar(tab) { tab = it } }) { pad -> Box(Modifier.padding(pad)) { when (tab) { Tab.HOME -> HomeScreen(vm, { tab = Tab.HISTORY }) { setupOpen = true }; Tab.HISTORY -> HistoryScreen(vm); Tab.SETTINGS -> SettingsScreen2(vm) } } }; MeetingPhase.LIVE, MeetingPhase.PAUSED -> LiveScreen(vm); MeetingPhase.COMPLETE -> CompletedScreen(vm) }
        }
    if (error != null) {
        LaunchedEffect(error) { delay(5000); vm.clearError() }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE8E8)), shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(16.dp)) { Text(error.orEmpty(), color = Color(0xFFB42318), modifier = Modifier.padding(14.dp), fontSize = 13.sp) }
    }
}

@Composable private fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit) { NavigationBar(containerColor = Color.White) { listOf(Tab.HOME to Icons.Default.Home, Tab.HISTORY to Icons.Default.History, Tab.SETTINGS to Icons.Default.Settings).forEach { (tab, icon) -> NavigationBarItem(selected = selected == tab, onClick = { onSelect(tab) }, icon = { Icon(icon, null) }, label = { Text(tab.name.lowercase().replaceFirstChar { it.uppercase() }) }) } } }

@Composable private fun HomeScreen(vm: MeetMateViewModel, openHistory: () -> Unit, startSetup: () -> Unit) {
    val meetings by vm.meetings.collectAsState(); val draft by vm.draft.collectAsState(); var title by remember(draft.title) { mutableStateOf(draft.title) }; var participants by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Good morning", color = Muted, fontSize = 14.sp); Text("Your meeting studio", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold) }; Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFE9E8FF)), contentAlignment = Alignment.Center) { Text("H", color = Violet, fontWeight = FontWeight.Bold) } } }
        item { Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF383F51)), contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = Color(0xFFA6A8FF)) }; Spacer(Modifier.width(10.dp)); Text("NEW SESSION", color = Color(0xFFA6A8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold) }; Text("Capture the conversation.\nKeep the momentum.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp); Button(onClick = { vm.updateDraft(title = title, participants = participants); startSetup() }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Set up a meeting") } } } }
        item { Text("Quick setup", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
        item { OutlinedTextField(value = title, onValueChange = { title = it; vm.updateDraft(title = it) }, label = { Text("Meeting title") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = participants, onValueChange = { participants = it; vm.updateDraft(participants = it) }, label = { Text("Participants (optional)") }, leadingIcon = { Icon(Icons.Default.People, null) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Recent meetings", fontWeight = FontWeight.Bold, fontSize = 17.sp); TextButton(onClick = openHistory) { Text("See all") } } }
        if (meetings.isEmpty()) item { EmptyState() } else items(meetings.take(3)) { meeting -> MeetingRow(meeting) { vm.openMeeting(meeting) } }
    }
}

@Composable private fun EmptyState() { Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AutoAwesome, null, tint = Violet, modifier = Modifier.size(30.dp)); Spacer(Modifier.height(8.dp)); Text("Your conversations will appear here", fontWeight = FontWeight.SemiBold); Text("Start a session to create your first transcript.", color = Muted, fontSize = 13.sp) } } }

@Composable private fun MeetingRow(meeting: MeetingEntity, onClick: () -> Unit) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE8F6F2)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Mic, null, tint = Mint) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(meeting.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${formatDuration(meeting.durationSeconds)}  ·  ${meeting.participants.ifBlank { "Solo" }}", color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Muted) } } }

@Composable private fun SetupScreen(vm: MeetMateViewModel, back: () -> Unit) {
    val draft by vm.draft.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.startMeeting() }
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Prepare your session", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(18.dp)); Text("Selected agents", color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) { items(AgentCatalog.all) { agent -> AgentCard(agent, draft.agents.any { it.id == agent.id && it.enabled }) { vm.toggleAgent(agent.id) } } }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEDEEFF)), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CloudOff, null, tint = Violet); Spacer(Modifier.width(10.dp)); Column { Text("Whisper offline transcription", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text("Download a model from Settings before starting", color = Muted, fontSize = 11.sp) } } }
        Spacer(Modifier.height(12.dp)); Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("Start listening", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun AgentCard(agent: AiAgent, enabled: Boolean, onToggle: () -> Unit) { Card(colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF0F1F5)), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(Color(agent.tint)), contentAlignment = Alignment.Center) { Text(agent.name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(agent.name, fontWeight = FontWeight.SemiBold); Text(agent.role, color = Muted, fontSize = 12.sp) }; Switch(checked = enabled, onCheckedChange = { onToggle() }) } } }

@Composable private fun LiveScreen(vm: MeetMateViewModel) {
    val draft by vm.draft.collectAsState(); val transcript by vm.transcript.collectAsState(); val partial by vm.partial.collectAsState(); val elapsed by vm.elapsed.collectAsState(); val phase by vm.phase.collectAsState()
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("LIVE SESSION", color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Text(draft.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(formatDuration(elapsed), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
        Text(draft.participants.ifBlank { "Just you · add participants in meeting details" }, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(18.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(21.dp), modifier = Modifier.weight(1f).fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(9.dp).clip(CircleShape).background(if (phase == MeetingPhase.LIVE) Color(0xFFFF5A69) else Muted)); Spacer(Modifier.width(8.dp)); Text("LIVE TRANSCRIPT", fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp); Spacer(Modifier.weight(1f)); Text("${if (phase == MeetingPhase.LIVE) "Listening" else "Paused"}", color = Muted, fontSize = 12.sp) }; Spacer(Modifier.height(17.dp)); if (transcript.isBlank() && partial.isBlank()) { Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.RecordVoiceOver, null, tint = Color(0xFFD7D9E2), modifier = Modifier.size(44.dp)); Spacer(Modifier.height(10.dp)); Text("Start speaking to see your words", color = Muted) } } else { LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp)) { if (transcript.isNotBlank()) item { Text(transcript, fontSize = 17.sp, lineHeight = 27.sp, modifier = Modifier.fillMaxWidth()) }; if (partial.isNotBlank()) item { Text(partial, color = Violet, fontSize = 17.sp, lineHeight = 27.sp) } } } } }
        Spacer(Modifier.height(14.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F4)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AutoAwesome, null, tint = Mint); Spacer(Modifier.width(9.dp)); Column { Text("AI is standing by", fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text("${draft.agents.count { it.enabled }} agents will process your notes", color = Muted, fontSize = 12.sp) } } }
        Spacer(Modifier.height(14.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { if (phase == MeetingPhase.LIVE) vm.pauseMeeting() else vm.resumeMeeting() }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) { Icon(if (phase == MeetingPhase.LIVE) Icons.Default.Pause else Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (phase == MeetingPhase.LIVE) "Pause" else "Resume") }; Button(onClick = vm::stopMeeting, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65365)), modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Finish") } }
    }
}

@Composable private fun CompletedScreen(vm: MeetMateViewModel) { val draft by vm.draft.collectAsState(); val transcript by vm.transcript.collectAsState(); val elapsed by vm.elapsed.collectAsState(); val analysis by vm.analysis.collectAsState(); Column(Modifier.fillMaxSize().padding(22.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Mint, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Column { Text("Meeting saved", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("${formatDuration(elapsed)} · ${draft.title}", color = Muted, fontSize = 13.sp) } }; Spacer(Modifier.height(16.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F4)), shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("AI INSIGHTS", color = Mint, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Text(analysis?.summary ?: "Preparing your summary…", fontWeight = FontWeight.SemiBold); analysis?.tasks?.forEach { Text("• $it", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) } } }; Spacer(Modifier.height(12.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth().weight(1f)) { Column(Modifier.padding(18.dp)) { Text("Transcript", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); Text(transcript.ifBlank { "No words were captured. You can add notes from meeting history." }, color = if (transcript.isBlank()) Muted else Ink, fontSize = 16.sp, lineHeight = 25.sp) } }; Spacer(Modifier.height(14.dp)); Button(onClick = vm::reset, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Start another meeting") } } }

@Composable private fun HistoryScreen(vm: MeetMateViewModel) { val meetings by vm.meetings.collectAsState(); Column(Modifier.fillMaxSize().padding(22.dp)) { Text("Meeting history", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Your conversations, kept local", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)); if (meetings.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(meetings) { MeetingRow(it) { vm.openMeeting(it) } } } } }

@Composable private fun SettingsScreen2(vm: MeetMateViewModel) {
    val models by vm.models.collectAsState(); val offline by vm.offlineMode.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Speech Recognition · Whisper Models", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Current engine", color = Muted, fontSize = 12.sp); Text("Whisper", fontWeight = FontWeight.Bold, fontSize = 18.sp) }; Icon(Icons.Default.Memory, null, tint = Violet) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Offline mode", fontWeight = FontWeight.SemiBold); Text("Whisper only · no network recognition", color = Muted, fontSize = 12.sp) }; Switch(checked = offline, onCheckedChange = vm::setOfflineMode) }
                Text("Whisper is the only speech recognition engine in this build.", color = Muted, fontSize = 12.sp)
            }
        }
        Text("Available Models", color = Violet, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        models.forEach { model ->
            Card(colors = CardDefaults.cardColors(containerColor = if (model.active) Color(0xFFEDEEFF) else Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { Text(model.label, fontWeight = FontWeight.Bold, fontSize = 16.sp); if (model.recommended) { Spacer(Modifier.width(8.dp)); Text("RECOMMENDED", color = Mint, fontSize = 9.sp, fontWeight = FontWeight.Bold) } }; Text(model.sizeLabel, color = Muted, fontSize = 12.sp) }; if (model.active) Icon(Icons.Default.CheckCircle, null, tint = Mint) }
                    if (model.state == ModelDownloadState.DOWNLOADING) { LinearProgressIndicator(progress = model.progress / 100f, modifier = Modifier.fillMaxWidth()); Text("Downloading ${model.progress}%", color = Muted, fontSize = 11.sp) }
                    if (model.storageWarning && !model.downloaded) Text("Low storage for this model. Free more space before downloading.", color = Color(0xFFB45F00), fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        when (model.state) {
                            ModelDownloadState.READY -> { if (!model.active) TextButton(onClick = { vm.selectModel(model.id) }) { Text("Use model") }; TextButton(onClick = { vm.deleteModel(model.id) }) { Text("Delete", color = Color(0xFFE65365)) } }
                            ModelDownloadState.DOWNLOADING -> TextButton(onClick = { }) { Text("Downloading…") }
                            else -> TextButton(enabled = !model.storageWarning, onClick = { vm.downloadModel(model.id) }) { Text("Download") }
                        }
                    }
                }
            }
        }
        Text("Recommendations use RAM, CPU cores, and available storage. Large models need a high-end device.", color = Muted, fontSize = 12.sp)
        SettingsSection("Telegram delivery") { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Auto-send completed meetings", fontWeight = FontWeight.SemiBold); Text("Configure bot token and chat ID", color = Muted, fontSize = 12.sp) }; Switch(checked = false, onCheckedChange = { }) } }
    }
}

@Composable private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) { Text(title.uppercase(), color = Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(8.dp)); Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) } } }

private fun formatDuration(seconds: Long): String { val m = seconds / 60; val s = seconds % 60; return "%02d:%02d".format(m, s) }
