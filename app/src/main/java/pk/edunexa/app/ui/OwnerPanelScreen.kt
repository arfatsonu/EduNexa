package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.operations.OwnerRepository
import pk.edunexa.app.operations.OwnerPolicy

private val ownerInk = Color(0xFF101C30)
private val ownerMuted = Color(0xFF5B6B82)

@Composable
fun OwnerPanelScreen(session: PortalSession, modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { OwnerRepository(session) }; val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    var schools by remember { mutableStateOf<List<DiaryItem>>(emptyList()) }; var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }; var retry by remember { mutableIntStateOf(0) }; var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }; var target by remember { mutableStateOf<DiaryItem?>(null) }; var reason by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf<DiaryItem?>(null) }; var editorTab by remember { mutableStateOf("Profile") }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(session.uid, retry) { busy = true; error = null; try { schools = repo.load() } catch(e: Exception) { if(e is CancellationException) throw e; error = e.message ?: "Schools could not load. Check Owner permission and published rules." } finally { busy = false } }
    val enabledCount = schools.count { it.raw["portalAccessEnabled"] != false }
    val visible = schools.filter { school ->
        val settings = school.raw["settings"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val matches = search.isBlank() || listOf(school.id, school.text("name"), settings["schoolName"], settings["campusName"], settings["schoolShortName"]).any { it?.toString()?.contains(search.trim(), true) == true }
        matches && when(filter) { "Enabled" -> school.raw["portalAccessEnabled"] != false; "Suspended" -> school.raw["portalAccessEnabled"] == false; else -> true }
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Schools overview", style = MaterialTheme.typography.headlineSmall, color = ownerInk, fontWeight = FontWeight.Bold)
            Text("Manage school identity, Admin logins and portal access.", style = MaterialTheme.typography.bodyMedium, color = ownerMuted)
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnerMetric("Schools", schools.size, Color(0xFF172554), Modifier.weight(1f))
            OwnerMetric("Enabled", enabledCount, Color(0xFF166534), Modifier.weight(1f))
            OwnerMetric("Suspended", schools.size - enabledCount, Color(0xFF9A3412), Modifier.weight(1f))
        } }
        item { Button(onClick = { registering = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = ownerInk)) { Text("+  Register a school") } }
        item { OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), label = { Text("Search school, campus or ID") }, singleLine = true, shape = RoundedCornerShape(14.dp), trailingIcon = { if(search.isNotEmpty()) TextButton(onClick = { search = "" }) { Text("Clear") } }) }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Enabled", "Suspended").forEach { label -> FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label, style = MaterialTheme.typography.labelMedium) }) }
            TextButton(onClick = { retry++ }, enabled = !busy) { Text("Refresh", style = MaterialTheme.typography.labelMedium) }
        } }
        if(busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item { PortalNotice(message + " Check your connection and tap Refresh.", true) } }
        notice?.let { message -> item { PortalNotice(message) } }
        if(!busy && error == null && visible.isEmpty()) item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if(schools.isEmpty()) "Your first school starts here" else "No schools match", fontWeight = FontWeight.Bold); Text(if(schools.isEmpty()) "Register a school to create its separate workspace and Admin login." else "Try another name, campus or status filter.", color = ownerMuted) } } }
        items(visible, key = { it.id }) { school ->
            @Suppress("UNCHECKED_CAST") val settings = school.raw["settings"] as? Map<String, Any?> ?: emptyMap()
            val enabled = school.raw["portalAccessEnabled"] != false
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0E7F0))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF1F5F9)) { SchoolLogo(Modifier.padding(6.dp).size(50.dp), settings["schoolLogo"]?.toString() ?: "") }
                        Column(Modifier.weight(1f)) {
                            Text(OwnerPolicy.displayText(settings["schoolName"]).ifBlank { school.text("name").ifBlank { school.id } }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ownerInk)
                            Text("${school.id} • ${OwnerPolicy.displayText(settings["campusName"])}", style = MaterialTheme.typography.bodySmall, color = ownerMuted)
                        }
                    }
                    Surface(color = if(enabled) Color(0xFFECFDF3) else Color(0xFFFFF1E8), shape = RoundedCornerShape(8.dp)) { Text(if(enabled) "●  Portal enabled" else "●  Portal suspended", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = if(enabled) Color(0xFF166534) else Color(0xFF9A3412), style = MaterialTheme.typography.labelMedium) }
                    @Suppress("UNCHECKED_CAST") val setup = school.raw["ownerSetup"] as? Map<String, Any?> ?: emptyMap()
                    val academic = pk.edunexa.app.students.EnrollmentPolicy.currentSession(setup)
                    val active = academic["status"]?.toString().equals("Active", true) || (academic["isCurrent"] == true && academic["status"]?.toString().isNullOrBlank())
                    Text("Session: ${if(active) OwnerPolicy.displayText(academic["name"]).ifBlank { "Unnamed session" } else "Setup needed"} • ${DiaryPolicy.maps(setup["classes"]).count { it["isActive"] == true }} active classes", style = MaterialTheme.typography.bodySmall, color = ownerMuted)
                    Text(OwnerPolicy.setup(settings), style = MaterialTheme.typography.bodySmall, color = ownerMuted)
                    settings["address"]?.toString()?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ownerMuted, maxLines = 2) }
                    HorizontalDivider(color = Color(0xFFEDF1F7))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editor = school; editorTab = "Profile" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = ownerInk)) { Text("Edit profile") }
                        OutlinedButton(onClick = { editor = school; editorTab = "Admin login" }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Manage Admin") }
                    }
                    TextButton(onClick = { target = school; reason = "" }, enabled = !busy && (!enabled || school.id != session.schoolId), colors = ButtonDefaults.textButtonColors(contentColor = if(enabled) Color(0xFFB45309) else Color(0xFF166534))) { Text(if(enabled) "Suspend portal access" else "Enable portal access") }
                }
            }
        }
        item { Text("EduNexa • Owner workspace", Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = ownerMuted) }
    }
    if(registering) SchoolRegistrationDialog(session, { registering = false }, { registering = false; retry++ })
    editor?.let { school -> OwnerSchoolEditor(session, school, editorTab, { editor = null }, { editor = null; notice = "School profile updated."; retry++ }) }
    target?.let { school -> val enabling = school.raw["portalAccessEnabled"] == false
        AlertDialog(onDismissRequest = { if(!busy) target = null }, title = { Text(if(enabling) "Enable school portal?" else "Suspend school portal?") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("School: ${school.id}. ${if(enabling) "School accounts can use the portal again." else "School Admin, Teacher and Student access will be blocked. Saved records are retained."}")
            OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), label = { Text("Reason") }, enabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = { busy = true; error = null; scope.launch { try { repo.access(school, enabling, reason); target = null; retry++ } catch(e: Exception) { if(e is CancellationException) throw e; error = e.message } finally { busy = false } } }, enabled = !busy && reason.trim().length in 3..300) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { target = null }, enabled = !busy) { Text("Cancel") } })
    }
}

@Composable
private fun OwnerMetric(label: String, count: Int, color: Color, modifier: Modifier) {
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE0E7F0))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ownerMuted)
        }
    }
}
