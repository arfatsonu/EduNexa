package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.settings.*

@Composable
fun SchoolSettingsScreen(session: PortalSession, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { SchoolSettingsRepository(session) }; val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Map<String, Any?>?>(null) }; var values by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
    var tab by rememberSaveable { mutableStateOf("School Profile") }; var editing by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableStateOf(0) }; var discard by remember { mutableStateOf(false) }
    AutoDismissSuccess(notice) { notice = null }
    LaunchedEffect(session.uid, retry) { loading = true; runCatching { repo.load() }.onSuccess { original = it; values = SchoolSettingsPolicy.editable(it); error = null; editing = false }.onFailure { if(it is CancellationException) throw it; error = it.message ?: "Settings load failed." }; loading = false }
    fun cancel() { values = SchoolSettingsPolicy.editable(original.orEmpty()); editing = false; error = null; discard = false }
    BackHandler(enabled = editing) { if(!busy) discard = true }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) { Column(Modifier.weight(1f)) { Text("School Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("EduNexa ${pk.edunexa.app.BuildConfig.VERSION_NAME} • Identity & branding", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }; TextButton(onClick = { retry += 1 }, enabled = !busy && !editing) { Text("Refresh") } }
        PortalTabs(listOf("School Profile", "Attendance Policy", "Branding", "Import Data"), tab, !busy && !editing) { tab = it; notice = null }
        notice?.let { PortalNotice(it) }; error?.let { PortalNotice(it, true) }
        if(loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else if(original == null) TextButton(onClick = { retry += 1 }) { Text("Retry") }
        else if(tab == "Import Data") ExcelImportScreen(session, onSaved, Modifier.weight(1f))
        else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(when(tab) { "School Profile" -> "Official School Profile"; "Branding" -> "Branding & Appearance"; else -> "Attendance Rules" }, Modifier.weight(1f), fontWeight = FontWeight.Bold); if(!editing) TextButton(onClick = { editing = true; notice = null; error = null }) { Text("Edit") } }
                if(tab == "School Profile") SchoolSettingsPolicy.profileFields.forEach { (key, label) ->
                    OutlinedTextField(values[key]?.toString().orEmpty(), { values = values + (key to it) }, Modifier.fillMaxWidth(), enabled = editing && !busy, label = { Text(label + if(key in setOf("schoolName", "campusName")) " *" else "") }, singleLine = key != "address", minLines = if(key == "address") 2 else 1, maxLines = if(key == "address") 4 else 1, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = when(key) { "email" -> KeyboardType.Email; "phone", "whatsapp" -> KeyboardType.Phone; else -> KeyboardType.Text }))
                } else if(tab == "Branding") {
                    BrandingControls(values, editing && !busy, { values = it }, { busy = it }, session.schoolId, original.orEmpty(), !busy && !editing)
                } else {
                    var minimum by remember(original, editing) { mutableStateOf(values["attendanceMinimumPercent"]?.toString().orEmpty()) }
                    OutlinedTextField(minimum, { minimum = it; values = values + ("attendanceMinimumPercent" to it.toDoubleOrNull()) }, Modifier.fillMaxWidth(), enabled = editing && !busy, label = { Text("Minimum Attendance (%)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    SettingSwitch("Count Late as Present", "Late remains recorded separately; this controls the rate numerator.", values["lateCountsAsPresent"] == true, editing && !busy) { values = values + ("lateCountsAsPresent" to it) }
                    SettingSwitch("Exclude Leave from Rate", "Approved leave is removed from the rate denominator.", values["excludeLeaveFromAttendance"] == true, editing && !busy) { values = values + ("excludeLeaveFromAttendance" to it) }
                    SettingSwitch("Automatic Late on QR Scan", "Use Pakistan time on the scanning phone. Keep its automatic date and time enabled.", values["autoLateEnabled"] == true, editing && !busy) { values = values + ("autoLateEnabled" to it) }
                    OutlinedTextField(values["schoolStartTime"]?.toString().orEmpty(), { values = values + ("schoolStartTime" to it) }, Modifier.fillMaxWidth(), enabled = editing && !busy, label = { Text("School start time • HH:mm, e.g. 08:00") }, singleLine = true)
                    var grace by remember(original,editing) { mutableStateOf(values["lateGraceMinutes"]?.toString().orEmpty()) }
                    OutlinedTextField(grace, { grace=it; values=values+("lateGraceMinutes" to it.toIntOrNull()) }, Modifier.fillMaxWidth(), enabled=editing && !busy, label={Text("Late grace minutes (0–180)")}, singleLine=true, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    Text("These rules calculate saved attendance in the dashboard, attendance overview and reports. Minimum attendance flags students in reports. Existing attendance entries stay intact.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
                if(editing) {
                    val profile = tab == "School Profile"
                    val valid = runCatching { if(tab == "Branding") BrandingPolicy.validate(values) else if(profile) SchoolSettingsPolicy.validateProfile(values) else SchoolSettingsPolicy.validateAttendance(values) }
                    if(valid.isFailure) Text(valid.exceptionOrNull()?.message.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { busy = true; error = null; scope.launch { runCatching { if(tab == "Branding") repo.saveBranding(original!!, values) else repo.save(original!!, values, profile) }.onSuccess { original = it; values = SchoolSettingsPolicy.editable(it); busy = false; editing = false; notice = when(tab) { "Branding" -> "Branding saved."; "School Profile" -> "School profile saved."; else -> "Attendance policy saved." }; onSaved() }.onFailure { if(it is CancellationException) throw it; busy = false; error = it.message ?: "Save failed." } } }, enabled = !busy && valid.isSuccess, modifier = Modifier.fillMaxWidth()) { Text(if(busy) "Saving…" else "Save Changes") }
                    OutlinedButton(onClick = { discard = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
    if(discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard Changes?") }, text = { Text("Your unsaved edits will be removed.") }, confirmButton = { TextButton(onClick = { cancel() }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep Editing") } })
}
@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) } }
@Composable private fun SettingSwitch(title: String, description: String, value: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }; Switch(value, onChange, enabled = enabled) } }


