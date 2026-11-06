package pk.edunexa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import pk.edunexa.app.diary.DiaryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UpcomingExams(schoolId: String, sessionId: String, onOpen: () -> Unit) {
    var entries by remember(schoolId, sessionId) { mutableStateOf<List<DiaryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }; var retry by remember { mutableStateOf(0) }
    DisposableEffect(schoolId, sessionId, retry) {
        loading = true; error = null
        val listener = FirebaseFirestore.getInstance().collection("schools").document(schoolId).collection("tests").addSnapshotListener { snapshot, failure ->
            loading = false
            if (failure != null) error = "Could not load the exam schedule."
            else {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                entries = snapshot?.documents.orEmpty().map { DiaryItem(it.id, it.data.orEmpty()) }.filter { it.text("academicSessionId") == sessionId && it.text("date") >= today && it.text("status") !in listOf("Cancelled", "Draft") }.sortedWith(compareBy({ it.text("date") }, { it.text("startTime") })).take(6)
            }
        }
        onDispose { listener.remove() }
    }
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Upcoming Tests & Exams", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry += 1 }) { Text("Retry") } }
            if (!loading && error == null && entries.isEmpty()) Text("No upcoming exams in the current session.", color = Color(0xFF64748B))
            entries.forEach { test -> Column {
                Text(test.text("title"), fontWeight = FontWeight.SemiBold)
                Text("${test.text("date")} ${test.text("startTime")} • ${test.text("className")} ${test.text("section")} • ${test.text("subject")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            } }
            TextButton(onClick = onOpen) { Text("View Exam Schedule") }
        }
    }
}

