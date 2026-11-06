package pk.edunexa.app.ui
import pk.edunexa.app.notifications.DirectMessageHelper
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edunexa.app.auth.PortalRole
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.messaging.StudentTeacherMessage
import pk.edunexa.app.messaging.StudentTeacherMessageRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudentTeacherMessagingScreen(
    session: PortalSession,
    studentName: String = "",
    studentRollNo: String = "",
    classId: String = "",
    className: String = "",
    sectionId: String = "",
    sectionName: String = "",
    availableTeachers: List<Pair<String, String>> = emptyList(), // teacherId to teacherName
    availableSubjects: List<Pair<String, String>> = emptyList(), // subjectId to subjectName
    modifier: Modifier = Modifier
) {
    val repo = remember { StudentTeacherMessageRepository() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isStudent = session.role == PortalRole.STUDENT
    val isTeacher = session.role == PortalRole.TEACHER

    var loadedTeachers by remember(session.schoolId) { mutableStateOf(availableTeachers) }
    var loadedSubjects by remember(session.schoolId) { mutableStateOf(availableSubjects) }

    val activeTeachers = if (loadedTeachers.isNotEmpty()) loadedTeachers else availableTeachers
    val activeSubjects = if (loadedSubjects.isNotEmpty()) loadedSubjects else availableSubjects

    LaunchedEffect(session.schoolId) {
        if (loadedTeachers.isEmpty() || loadedSubjects.isEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val doc = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("academicConfig").document("current").get()
                    )
                    if (doc.exists()) {
                        val config = doc.data.orEmpty()
                        val sub = (config["subjects"] as? List<*>)?.mapNotNull { item ->
                            val m = item as? Map<*, *>
                            val id = m?.get("id")?.toString().orEmpty()
                            val name = m?.get("name")?.toString().orEmpty()
                            if (id.isNotBlank() && name.isNotBlank()) id to name else null
                        }.orEmpty()
                        if (sub.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                loadedSubjects = sub
                            }
                        }
                    }
                    val teachersSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("teachers").whereEqualTo("status", "Active").get()
                    )
                    val tList = teachersSnap.documents.mapNotNull { d ->
                        val id = d.id
                        val name = d.getString("name") ?: d.getString("fullName").orEmpty()
                        if (id.isNotBlank() && name.isNotBlank()) id to name else null
                    }
                    if (tList.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            loadedTeachers = tList
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    var messages by remember { mutableStateOf<List<StudentTeacherMessage>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAskDialog by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<StudentTeacherMessage?>(null) }
    var filterTab by rememberSaveable { mutableStateOf("All") }

    LaunchedEffect(session.uid, refresh) {
        loading = true
        error = null
        try {
            messages = if (isStudent) {
                repo.loadForStudent(session.schoolId, session.profileId.orEmpty())
            } else if (isTeacher) {
                repo.loadForTeacher(session.schoolId, session.profileId.orEmpty())
            } else emptyList()
        } catch (e: Exception) {
            error = e.message ?: "Could not load messages."
        } finally {
            loading = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = if (isStudent) "Ask Your Teacher" else "Student Questions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isStudent) "Direct Q&A with subject faculty" else "Inquiries and doubts from your classes",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
            if (isStudent) {
                Button(
                    onClick = { showAskDialog = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Outlined.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ask Question", fontSize = 12.sp)
                }
            }
        }

        // Tabs: All / Pending / Answered
        PortalTabs(
            options = listOf("All", "Pending", "Answered"),
            selected = filterTab,
            enabled = !loading
        ) { filterTab = it }

        Spacer(Modifier.height(8.dp))

        error?.let { PortalNotice(it, failure = true) }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = messages.filter {
                when (filterTab) {
                    "Pending" -> it.status == "Pending"
                    "Answered" -> it.status == "Replied"
                    else -> true
                }
            }

            if (filtered.isEmpty()) {
                PortalEmptyState(
                    icon = Icons.Outlined.QuestionAnswer,
                    title = "No messages found",
                    description = if (isStudent) "You haven't asked any questions yet. Tap 'Ask Question' to contact a subject teacher."
                    else "There are no student questions in this section."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (msg.status == "Replied") Color(0xFFDCFCE7) else Color(0xFFFEF3C7)
                                    ) {
                                        Text(
                                            text = if (msg.status == "Replied") "Answered ✔" else "Pending Response",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            color = if (msg.status == "Replied") Color(0xFF15803D) else Color(0xFFB45309),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(msg.createdAt))
                                    Text(dateStr, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }

                                Text(
                                    text = "${msg.subjectName} • ${if (isTeacher) msg.studentName + " (Roll " + msg.rollNo + ")" else "Teacher: " + msg.teacherName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )

                                // Question bubble
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF1F5F9),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text("Question:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        Text(msg.question, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1E293B))
                                    }
                                }

                                // Reply bubble (if replied)
                                if (msg.reply.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(10.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("${msg.teacherName}'s Answer:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                if (msg.repliedAt > 0) {
                                                    val replyDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(msg.repliedAt))
                                                    Text(replyDate, fontSize = 10.sp, color = Color(0xFF64748B))
                                                }
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(msg.reply, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                                        }
                                    }
                                } else if (isTeacher) {
                                    OutlinedButton(
                                        onClick = { replyingToMessage = msg },
                                        modifier = Modifier.align(Alignment.End),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Outlined.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reply", fontSize = 12.sp)
                                    }
                                } else if (isStudent) {
                                    OutlinedButton(
                                        onClick = {
                                            val qText = "Assalam-o-Alaikum Sir ${msg.teacherName},\nI am ${msg.studentName} (Class ${msg.className}, Roll ${msg.rollNo}).\nQuestion regarding ${msg.subjectName}:\n${msg.question}"
                                            DirectMessageHelper.sendWhatsApp(context, "", qText)
                                        },
                                        modifier = Modifier.align(Alignment.End),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        WhatsAppIcon(Modifier.size(14.dp), contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("WhatsApp Sir ${msg.teacherName}", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Student Ask Question Dialog
    if (showAskDialog) {
        var selectedTeacherId by remember { mutableStateOf("") }
        var selectedSubjectId by remember { mutableStateOf("") }
        var questionText by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!submitting) showAskDialog = false },
            title = { Text("Ask Subject Teacher", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select your subject and teacher to ask a question or clarify homework/topics.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    
                    PortalSelect(
                        label = "Subject",
                        value = selectedSubjectId,
                        options = listOf("" to "Select Subject") + activeSubjects,
                        enabled = !submitting,
                        onSelect = { selectedSubjectId = it }
                    )

                    PortalSelect(
                        label = "Teacher",
                        value = selectedTeacherId,
                        options = listOf("" to "Select Teacher") + activeTeachers,
                        enabled = !submitting,
                        onSelect = { selectedTeacherId = it }
                    )

                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        label = { Text("Your Question / Doubt") },
                        placeholder = { Text("Type your question here clearly...") },
                        enabled = !submitting
                    )

                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val subName = activeSubjects.firstOrNull { it.first == selectedSubjectId }?.second.orEmpty()
                        val tName = activeTeachers.firstOrNull { it.first == selectedTeacherId }?.second.orEmpty()
                        if (selectedSubjectId.isBlank() || selectedTeacherId.isBlank()) {
                            dialogError = "Please select both a subject and a teacher."
                            return@Button
                        }
                        if (questionText.trim().length < 5) {
                            dialogError = "Please enter a question with at least 5 characters."
                            return@Button
                        }
                        submitting = true
                        dialogError = null
                        scope.launch {
                            try {
                                repo.sendQuestion(
                                    StudentTeacherMessage(
                                        schoolId = session.schoolId,
                                        studentId = session.profileId.orEmpty(),
                                        studentName = studentName.ifBlank { session.name },
                                        rollNo = studentRollNo,
                                        classId = classId,
                                        className = className,
                                        sectionId = sectionId,
                                        section = sectionName,
                                        subjectId = selectedSubjectId,
                                        subjectName = subName,
                                        teacherId = selectedTeacherId,
                                        teacherName = tName,
                                        question = questionText.trim()
                                    )
                                )
                                submitting = false
                                showAskDialog = false
                                refresh++
                            } catch (e: Exception) {
                                submitting = false
                                dialogError = e.message ?: "Could not send question."
                            }
                        }
                    },
                    enabled = !submitting
                ) {
                    Text(if (submitting) "Sending..." else "Send Question")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAskDialog = false },
                    enabled = !submitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Teacher Reply Dialog
    replyingToMessage?.let { targetMsg ->
        var replyText by remember { mutableStateOf("") }
        var submittingReply by remember { mutableStateOf(false) }
        var replyError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!submittingReply) replyingToMessage = null },
            title = { Text("Reply to ${targetMsg.studentName}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Student: ${targetMsg.studentName} (Roll: ${targetMsg.rollNo})", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                    Text("Subject: ${targetMsg.subjectName}", color = Color(0xFF64748B), fontSize = 12.sp)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(targetMsg.question, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }

                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                        label = { Text("Teacher's Response") },
                        placeholder = { Text("Type your answer or instructions...") },
                        enabled = !submittingReply
                    )

                    replyError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (replyText.trim().isBlank()) {
                            replyError = "Please enter an answer."
                            return@Button
                        }
                        submittingReply = true
                        replyError = null
                        scope.launch {
                            try {
                                repo.sendReply(session.schoolId, targetMsg.id, replyText)
                                submittingReply = false
                                replyingToMessage = null
                                refresh++
                            } catch (e: Exception) {
                                submittingReply = false
                                replyError = e.message ?: "Could not send reply."
                            }
                        }
                    },
                    enabled = !submittingReply
                ) {
                    Text(if (submittingReply) "Sending..." else "Send Reply")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { replyingToMessage = null },
                    enabled = !submittingReply
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
