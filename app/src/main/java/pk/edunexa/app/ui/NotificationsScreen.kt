package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.notifications.*
import java.util.UUID

private data class NoticeFeed(val rows: List<DiaryItem>, val readIds: Set<String>, val loading: Boolean, val error: String?)
@Composable
private fun noticeFeed(session: PortalSession, retry: Int = 0, activityRows: List<DiaryItem> = emptyList(), activityLoading: Boolean = false): NoticeFeed {
    val context = LocalContext.current
    val activityReads = remember(session.uid, session.schoolId, session.profileId) { StudentActivityReadStore(context, session) }
    var localReads by remember(activityReads) { mutableStateOf(activityReads.ids()) }
    DisposableEffect(activityReads) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> localReads = activityReads.ids() }
        activityReads.preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { activityReads.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var rows by remember(session.uid) { mutableStateOf<List<DiaryItem>>(emptyList()) }
    var readIds by remember(session.uid) { mutableStateOf<Set<String>>(emptySet()) }
    var rowsReady by remember(session.uid) { mutableStateOf(false) }; var readsReady by remember(session.uid) { mutableStateOf(session.role == PortalRole.ADMIN) }
    var rowsError by remember(session.uid) { mutableStateOf<String?>(null) }; var readsError by remember(session.uid) { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.uid) { while(true) { now = System.currentTimeMillis(); delay(30000) } }
    DisposableEffect(session.uid, retry) {
        rowsReady = false; readsReady = session.role == PortalRole.ADMIN; rowsError = null; readsError = null; rows = emptyList(); readIds = emptySet()
        val root = FirebaseFirestore.getInstance().collection("schools").document(session.schoolId)
        val admin = session.role == PortalRole.ADMIN; val recipient = session.profileId.orEmpty()
        if(!admin && recipient.isBlank()) { rowsError = "Linked profile required."; rowsReady = true; readsReady = true; onDispose {} }
        else {
            val query = if(admin) root.collection("notifications") else root.collection("notifications").whereArrayContains("recipientIds", recipient)
            val a = query.addSnapshotListener { value, failure -> rowsReady = true; rowsError = failure?.message; if(failure == null) rows = value?.documents.orEmpty().map { DiaryItem(it.id, it.data.orEmpty()) } else rows = emptyList() }
            val b = if(admin) null else root.collection("notificationReads").whereEqualTo("userId", recipient).addSnapshotListener { value, failure -> readsReady = true; readsError = failure?.message; if(failure == null) readIds = value?.documents.orEmpty().mapNotNull { it.getString("notificationId") }.toSet() else readIds = emptySet() }
            onDispose { a.remove(); b?.remove() }
        }
    }
    val visible = rows.filter { NotificationPolicy.visible(it, session.role == PortalRole.ADMIN, session.profileId.orEmpty(), now) }
    val combined = StudentActivityAlerts.merge(visible, activityRows.filter { NotificationPolicy.visible(it, false, session.profileId.orEmpty(), now) })
    return NoticeFeed(combined.sortedByDescending { NotificationPolicy.millis(it.text("publishAt")) ?: NotificationPolicy.millis(it.text("createdAt")) ?: 0L }, readIds + localReads, !rowsReady || !readsReady || activityLoading, rowsError ?: readsError)
}
@Composable
internal fun NotificationsBell(session: PortalSession, activityRows: List<DiaryItem> = emptyList(), activityLoading: Boolean = false, onClick: () -> Unit) {
    if(session.role == PortalRole.ADMIN) { IconButton(onClick = onClick) { Icon(Icons.Outlined.Notifications, "Notifications") }; return }
    val feed = noticeFeed(session, activityRows = activityRows, activityLoading = activityLoading)
    val unread = if(feed.loading || feed.error != null) 0 else feed.rows.count { it.id !in feed.readIds }
    IconButton(onClick = onClick) { BadgedBox(badge = { if(unread > 0) Badge { Text(if(unread > 99) "99+" else unread.toString()) } }) { Icon(Icons.Outlined.Notifications, "Notifications${if(unread > 0) ", $unread unread" else ""}") } }
}
@Composable
fun NotificationsScreen(session: PortalSession, modifier: Modifier = Modifier, activityRows: List<DiaryItem> = emptyList(), activityLoading: Boolean = false) {
    val repo = remember(session.uid) { NotificationRepository(session) }; val scope = rememberCoroutineScope(); val admin = session.role == PortalRole.ADMIN
    val context = LocalContext.current
    val activityReads = remember(session.uid, session.schoolId, session.profileId) { StudentActivityReadStore(context, session) }
    suspend fun markRead(item: DiaryItem) {
        if(StudentActivityAlerts.isActivity(item)) activityReads.markRead(item) else repo.markRead(item)
    }
    var retry by remember { mutableStateOf(0) }; val feed = noticeFeed(session, retry, activityRows, activityLoading)
    var filter by rememberSaveable { mutableStateOf("All") }; var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<DiaryItem?>(null) }; var details by remember { mutableStateOf<DiaryItem?>(null) }; var action by remember { mutableStateOf<Pair<DiaryItem, String>?>(null) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(message) { message = null }
    fun operation(block: suspend () -> Unit) { busy = true; error = null; scope.launch { runCatching { block() }.onSuccess { busy = false; editor = null; action = null; message = "Saved successfully." }.onFailure { if(it is kotlinx.coroutines.CancellationException) throw it; busy = false; error = it.message ?: "Save failed. Retry." } } }
    BackHandler(enabled = editor != null || details != null) { if(!busy) { editor = null; details = null; error = null } }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if(editor != null) NoticeEditor(editor!!, repo, busy, error, { editor = null; error = null }) { title, text, priority, audience, cls, section, recipient -> operation { repo.saveDraft(editor!!, title, text, priority, audience, cls, section, recipient) } }
        else {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Column(Modifier.weight(1f)) { Text("Notifications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if(admin) "School notices & announcements" else "${feed.rows.count { it.id !in feed.readIds }} unread notices", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }
                if(admin) TextButton(onClick = { error = null; editor = DiaryItem("notice-${UUID.randomUUID()}", emptyMap()) }, enabled = !busy) { Text("+ New") }
            }
            PortalTabs(if(admin) listOf("All", "Draft", "Published", "Scheduled", "Archived") else listOf("All", "Unread", "Read"), filter, !busy) { filter = it }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, placeholder = { Text("Search title or message") }, shape = RoundedCornerShape(12.dp))
            message?.let { PortalNotice(it) }; (error ?: feed.error)?.let { PortalNotice(it, true); TextButton(onClick = { retry += 1; error = null }) { Text("Retry") } }
            if(feed.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            val rows = feed.rows.filter { row -> (if(admin) filter == "All" || row.text("status") == filter else filter == "All" || (filter == "Read") == (row.id in feed.readIds)) && (row.text("title").contains(query.trim(), true) || row.text("message").contains(query.trim(), true)) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if(!feed.loading && feed.error == null && rows.isEmpty()) item { PortalEmptyState(Icons.Outlined.Notifications, "No notifications", if(admin) "Create a draft to prepare a school notice." else "Attendance, fee payments, homework and school notices will appear here.") }
                items(rows, key = { it.id }) { notice -> Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row { Text(notice.text("title"), Modifier.weight(1f), fontWeight = FontWeight.Bold); PortalStatusBadge(if(admin) notice.text("status") else if(notice.id in feed.readIds) "Read" else "Unread") }
                        Text("${notice.text("priority")} • ${notice.text("audience")} • ${notice.text("className")} ${notice.text("sectionName")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                        Text(notice.text("message"), maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                        Text(notice.text("publishAt").ifBlank { notice.text("createdAt") }, style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { details = notice; error = null; if(!admin && notice.id !in feed.readIds) operation { markRead(notice) } }, enabled = !busy) { Text("View") }
                            if(admin && notice.text("status") == "Draft") { TextButton(onClick = { editor = notice; error = null }, enabled = !busy) { Text("Edit") }; TextButton(onClick = { action = notice to "Published"; error = null }, enabled = !busy) { Text("Publish") } }
                            if(admin && notice.text("status") != "Archived") TextButton(onClick = { action = notice to "Archived"; error = null }, enabled = !busy) { Text("Archive") }
                            Spacer(Modifier.weight(1f))
                            val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
                            IconButton(
                                onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareWhatsAppBroadcast(context, schoolName, notice.text("title"), notice.text("message")) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                WhatsAppIcon(Modifier.size(18.dp), contentDescription = "Share via WhatsApp")
                            }
                            IconButton(
                                onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareSmsBroadcast(context, schoolName, notice.text("title"), notice.text("message")) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = "Share Notice", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } }
            }
        }
    }
    details?.let { notice ->
        val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
        AlertDialog(
            onDismissRequest = { if(!busy) details = null },
            title = { Text(notice.text("title"), fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${notice.text("priority")} • Audience: ${notice.text("audience")}", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
                    Text(notice.text("message"), style = MaterialTheme.typography.bodyLarge)
                    Text("By ${notice.text("createdBy")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (admin) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("1-Click Share to All Parents / Staff:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareWhatsAppBroadcast(context, schoolName, notice.text("title"), notice.text("message")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("WhatsApp", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareSmsBroadcast(context, schoolName, notice.text("title"), notice.text("message")) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Share SMS", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    }

                    if(!admin && notice.id !in feed.readIds && !busy) TextButton(onClick = { operation { markRead(notice) } }) { Text("Mark Read") }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }, enabled = !busy) { Text("Close") } }
        )
    }
    action?.let { (notice, status) -> AlertDialog(onDismissRequest = { if(!busy) action = null }, title = { Text(if(status == "Published") "Publish Notice?" else "Archive Notice?") }, text = { Column { Text(notice.text("title")); Text(if(status == "Published") "Audience: ${notice.text("audience")}. ${(notice.raw["recipientIds"] as? List<*>)?.size ?: 0} recipients. This notice will become visible to these recipients." else "Recipients will no longer see this notice. The saved notice remains available to Admin."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = { operation { repo.changeStatus(notice, status) } }, enabled = !busy) { Text(if(busy) "Saving…" else "Confirm") } }, dismissButton = { TextButton(onClick = { action = null }, enabled = !busy) { Text("Cancel") } }) }
}

@Composable
private fun NoticeEditor(item: DiaryItem, repo: NotificationRepository, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (String, String, String, String, String, String, String) -> Unit) {
    val context = LocalContext.current
    var directory by remember { mutableStateOf<NotificationDirectory?>(null) }; var loadError by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableStateOf(0) }
    var title by remember(item.id) { mutableStateOf(item.text("title")) }; var text by remember(item.id) { mutableStateOf(item.text("message")) }
    var priority by remember(item.id) { mutableStateOf(item.text("priority").ifBlank { "Normal" }) }; var audience by remember(item.id) { mutableStateOf(item.text("audience").ifBlank { "All" }) }
    var cls by remember(item.id) { mutableStateOf(item.text("classId")) }; var section by remember(item.id) { mutableStateOf(item.text("sectionId")) }; var recipient by remember(item.id) { mutableStateOf(item.text("recipientId")) }
    LaunchedEffect(item.id, retry) { runCatching { repo.directory() }.onSuccess { directory = it; loadError = null }.onFailure { if(it is kotlinx.coroutines.CancellationException) throw it; loadError = it.message } }
    val current = directory
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Back") }
        Text("Notice Draft", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Save prepares a draft. Publish is a separate action after reviewing recipients.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
        if(current == null && loadError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        loadError?.let { PortalNotice(it, true); TextButton(onClick = { retry += 1 }) { Text("Retry") } }
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !busy)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Message") }, minLines = 4, maxLines = 10, enabled = !busy)
        PortalSelect("Priority", priority, NotificationPolicy.priorities.map { it to it }, !busy) { priority = it }
        PortalSelect("Audience", audience, NotificationPolicy.audiences.map { it to it }, !busy) { audience = it; cls = ""; section = ""; recipient = "" }
        if(current != null) {
            Text("Session: ${current.currentSession["name"]?.toString().orEmpty()}", style = MaterialTheme.typography.bodySmall)
            if(audience == "Class") {
                PortalSelect("Class", cls, current.options("classes").filter { it["isActive"] == true }.map { it["id"].toString() to it["name"].toString() }, !busy) { cls = it; section = "" }
                PortalSelect("Section", section, listOf("" to "All Sections") + current.options("sections").filter { it["classId"] == cls && it["isActive"] == true }.map { it["id"].toString() to it["name"].toString() }, !busy) { section = it }
            }
            if(audience == "Individual") {
                val people = current.teachers.filter { it.text("status") == "Active" }.map { it.id to "Teacher • ${it.text("name")} • ${it.text("employeeId")}" } + current.students.filter { it.text("status") in setOf("Active", "On Leave") && it.text("academicSessionId").ifBlank { it.text("academicSession") } in current.aliases }.map { it.id to "Student • ${it.text("fullName")} • ${it.text("admissionNo")}" }
                PortalSelect("Recipient", recipient, people.sortedBy { it.second }, !busy) { recipient = it }
            }
            val count = runCatching { NotificationPolicy.recipients(audience, current.teachers, current.students, current.aliases, cls, section, recipient).size }.getOrDefault(1)
            Text("$count active recipients selected for this notice.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))

            val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
            if (title.trim().isNotBlank() && text.trim().isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1-Click Instant Broadcast to Parents / Staff:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareWhatsAppBroadcast(context, schoolName, title, text) },
                                modifier = Modifier.weight(1f)
                            ) {
                                WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("WhatsApp Broadcast", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { pk.edunexa.app.notifications.DirectMessageHelper.shareSmsBroadcast(context, schoolName, title, text) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share SMS", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            error?.let { PortalNotice(it, true) }
            Button(onClick = { onSave(title, text, priority, audience, cls, section, recipient) }, enabled = !busy && title.trim().isNotBlank() && text.trim().isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if(busy) "Saving…" else "Save Draft") }
        }
    }
}

