package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DiaryActivity(val id: String, val title: String, val detail: String, val time: Long)

@Composable
fun RecentDiaryActivity(schoolId: String) {
    var diary by remember(schoolId) { mutableStateOf<List<DiaryActivity>>(emptyList()) }
    var fees by remember(schoolId) { mutableStateOf<List<DiaryActivity>>(emptyList()) }
    var exams by remember(schoolId) { mutableStateOf<List<DiaryActivity>>(emptyList()) }
    var homework by remember(schoolId) { mutableStateOf<List<DiaryActivity>>(emptyList()) }
    var attendance by remember(schoolId) { mutableStateOf<List<DiaryActivity>>(emptyList()) }
    var diaryReady by remember(schoolId) { mutableStateOf(false) }
    var homeworkReady by remember(schoolId) { mutableStateOf(false) }
    var error by remember(schoolId) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    DisposableEffect(schoolId, retry) {
        error = null; diaryReady = false; homeworkReady = false
        val root = FirebaseFirestore.getInstance().collection("schools").document(schoolId)
        fun event(doc: DocumentSnapshot, check: Boolean): DiaryActivity {
            fun str(key: String) = doc.getString(key).orEmpty()
            fun millis(value: Any?): Long = when (value) {
                is Timestamp -> value.toDate().time
                is String -> runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(value)?.time ?: 0L }.getOrDefault(0L)
                else -> 0L
            }
            val at = maxOf(millis(doc.get(if (check) "checkedAt" else "updatedAt")), millis(doc.get("cloudUpdatedAt")))
            val person = str(if (check) "checkedBy" else "teacherName").ifBlank { str("createdBy") }
            val action = if (check) "Homework checked" else if (str("updatedAt").isNotBlank() && str("updatedAt") != str("createdAt")) "Diary updated" else "Diary added"
            return DiaryActivity((if (check) "homework:" else "diary:") + doc.id, action + if (person.isNotBlank()) " • $person" else "",
                listOf(str("className"), str("section"), str("subject"), str("date")).filter { it.isNotBlank() }.joinToString(" • "), at)
        }
        val a = root.collection("diaryEntries").addSnapshotListener { snapshot, failure ->
            diaryReady = true
            if (failure != null) error = "Could not load recent activity. Try again."
            else diary = snapshot?.documents.orEmpty().map { event(it, false) }
        }
        val b = root.collection("homeworkSessions").addSnapshotListener { snapshot, failure ->
            homeworkReady = true
            if (failure != null) error = "Could not load recent activity. Try again."
            else homework = snapshot?.documents.orEmpty().map { event(it, true) }
        }
        val c = root.collection("tests").addSnapshotListener { snapshot, failure ->
            if (failure != null) error = "Could not load exam activity. Try again."
            else exams = snapshot?.documents.orEmpty().flatMap { doc ->
                val source = event(doc, false)
                val testEvent = source.copy(id = "test:" + doc.id, title = "Test scheduled/updated • " + doc.getString("title").orEmpty())
                val marksAt = doc.getString("marksUpdatedAt").orEmpty()
                val marksTime = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(marksAt)?.time ?: 0L }.getOrDefault(0L)
                if (marksTime > 0) listOf(testEvent, source.copy(id = "marks:" + doc.id, title = "Marks saved • " + doc.getString("marksUpdatedBy").orEmpty(), time = marksTime)) else listOf(testEvent)
            }
        }
        val d = root.collection("fees").addSnapshotListener { snapshot, failure ->
            if (failure != null) error = "Could not load fee activity. Try again."
            else fees = snapshot?.documents.orEmpty().flatMap { doc ->
                pk.edunexa.app.diary.DiaryPolicy.maps(doc.get("paymentHistory")).map { payment ->
                    val at = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).parse(payment["createdAt"].toString())?.time ?: 0L }.getOrDefault(0L)
                    DiaryActivity("fee:${doc.id}:${payment["id"]}", "Fee received • ${payment["receivedBy"].toString()}", "${doc.getString("studentName").orEmpty()} • ${doc.getString("feeType").orEmpty()} • Rs. ${payment["amount"]} • ${payment["receiptNo"]}", at)
                }
            }
        }
        val e = root.collection("attendance").addSnapshotListener { snapshot, failure ->
            if(failure != null) error = "Could not load attendance activity. Try again."
            else attendance = snapshot?.documents.orEmpty().groupBy { listOf(it.getString("classId"),it.getString("sectionId"),it.getString("attendanceDate"),it.getString("markedByTeacherId").orEmpty().ifBlank { it.getString("markedBy") }) }.map { (scope,docs) ->
                val doc = docs.maxByOrNull { it.getString("lastUpdatedAt").orEmpty().ifBlank { it.getString("markedAt").orEmpty() } }!!
                val stamp = doc.getString("lastUpdatedAt").orEmpty().ifBlank { doc.getString("markedAt").orEmpty() }
                val at = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).parse(stamp)?.time ?: 0L }.getOrDefault(0L)
                DiaryActivity("attendance:" + scope.joinToString(":"),"Attendance saved • " + doc.getString("markedBy").orEmpty(),listOf(doc.getString("className"),doc.getString("section"),doc.getString("attendanceDate")).filterNotNull().joinToString(" • "),at)
            }
        }
        onDispose { a.remove(); b.remove(); c.remove(); d.remove(); e.remove() }
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!diaryReady || !homeworkReady) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry += 1 }) { Text("Retry") } }
            val entries = (diary + homework + exams + fees + attendance).sortedByDescending { it.time }.take(15)
            if (diaryReady && homeworkReady && error == null && entries.isEmpty()) Text("No activity yet.", color = Color(0xFF64748B))
            entries.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                    if (item.time > 0) Text(SimpleDateFormat("dd MMM, h:mm a", Locale.US).format(Date(item.time)), style = MaterialTheme.typography.labelSmall, color = androidx.compose.material3.MaterialTheme.colorScheme.secondary)
                }
                HorizontalDivider(color = Color(0xFFE2E8F0))
            }
        }
    }
}


