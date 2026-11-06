package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Schedule
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
import pk.edunexa.app.timetable.TimetableEntry
import pk.edunexa.app.timetable.TimetableRepository

@Composable
fun TimetableScreen(
    session: PortalSession,
    classId: String = "",
    sectionId: String = "",
    className: String = "",
    sectionName: String = "",
    availableClasses: List<Pair<String, String>> = emptyList(), // id to name
    availableSections: List<Triple<String, String, String>> = emptyList(), // id to name to classId
    availableSubjects: List<Pair<String, String>> = emptyList(), // id to name
    availableTeachers: List<Pair<String, String>> = emptyList(), // id to name
    modifier: Modifier = Modifier
) {
    val repo = remember { TimetableRepository() }
    val scope = rememberCoroutineScope()
    val isAdmin = session.role == PortalRole.ADMIN
    val isTeacher = session.role == PortalRole.TEACHER

    var loadedClasses by remember(session.schoolId) { mutableStateOf(availableClasses) }
    var loadedSections by remember(session.schoolId) { mutableStateOf(availableSections) }
    var loadedSubjects by remember(session.schoolId) { mutableStateOf(availableSubjects) }
    var loadedTeachers by remember(session.schoolId) { mutableStateOf(availableTeachers) }

    val activeClasses = if (loadedClasses.isNotEmpty()) loadedClasses else availableClasses
    val activeSections = if (loadedSections.isNotEmpty()) loadedSections else availableSections
    val activeSubjects = if (loadedSubjects.isNotEmpty()) loadedSubjects else availableSubjects
    val activeTeachers = if (loadedTeachers.isNotEmpty()) loadedTeachers else availableTeachers

    var selectedClassId by rememberSaveable { mutableStateOf(classId) }
    var selectedSectionId by rememberSaveable { mutableStateOf(sectionId) }
    var selectedDay by rememberSaveable { mutableStateOf("Monday") }

    LaunchedEffect(session.schoolId) {
        if (isAdmin && loadedClasses.isEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val doc = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("academicConfig").document("current").get()
                    )
                    if (doc.exists()) {
                        val config = doc.data.orEmpty()
                        val cls = (config["classes"] as? List<*>)?.mapNotNull { item ->
                            val m = item as? Map<*, *>
                            val id = m?.get("id")?.toString().orEmpty()
                            val name = m?.get("name")?.toString().orEmpty()
                            if (id.isNotBlank() && name.isNotBlank()) id to name else null
                        }.orEmpty()
                        val sec = (config["sections"] as? List<*>)?.mapNotNull { item ->
                            val m = item as? Map<*, *>
                            val id = m?.get("id")?.toString().orEmpty()
                            val name = m?.get("name")?.toString().orEmpty()
                            val cId = m?.get("classId")?.toString().orEmpty()
                            if (id.isNotBlank() && name.isNotBlank()) Triple(id, name, cId) else null
                        }.orEmpty()
                        val sub = (config["subjects"] as? List<*>)?.mapNotNull { item ->
                            val m = item as? Map<*, *>
                            val id = m?.get("id")?.toString().orEmpty()
                            val name = m?.get("name")?.toString().orEmpty()
                            if (id.isNotBlank() && name.isNotBlank()) id to name else null
                        }.orEmpty()
                        withContext(Dispatchers.Main) {
                            if (cls.isNotEmpty()) {
                                loadedClasses = cls
                                if (selectedClassId.isBlank()) selectedClassId = cls.first().first
                            }
                            if (sec.isNotEmpty()) loadedSections = sec
                            if (sub.isNotEmpty()) loadedSubjects = sub
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

    var entries by remember { mutableStateOf<List<TimetableEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<TimetableEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedClassId, selectedSectionId, session.uid, refresh) {
        loading = true
        error = null
        try {
            entries = when {
                isTeacher -> repo.loadForTeacher(session.schoolId, session.profileId.orEmpty(), session.name)
                else -> {
                    if (selectedClassId.isNotBlank()) {
                        repo.loadForClass(session.schoolId, selectedClassId, selectedSectionId)
                    } else emptyList()
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not load timetable."
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
                    text = when {
                        isTeacher -> "My Teaching Timetable"
                        isAdmin -> "School Timetable"
                        else -> "Class Timetable"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isTeacher) "Assigned lecture schedule" else "Weekly class periods",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
            if (isAdmin) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (selectedDay == "Monday" && entries.any { it.day.equals("Monday", ignoreCase = true) }) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repo.copyDayToWeekdays(session.schoolId, selectedClassId, selectedSectionId, "Monday")
                                    refresh++
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Copy Mon to Weekdays", fontSize = 11.sp)
                        }
                    }
                    Button(
                        onClick = {
                            val clsId = selectedClassId.ifBlank { activeClasses.firstOrNull()?.first.orEmpty() }
                            if (selectedClassId.isBlank() && clsId.isNotBlank()) selectedClassId = clsId
                            val clsName = activeClasses.firstOrNull { it.first == clsId }?.second.orEmpty()
                            val secName = activeSections.firstOrNull { it.first == selectedSectionId }?.second.orEmpty()
                            editingEntry = TimetableEntry(
                                schoolId = session.schoolId,
                                classId = selectedClassId,
                                className = clsName,
                                sectionId = selectedSectionId,
                                section = secName,
                                day = selectedDay
                            )
                            showAddDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Period", fontSize = 12.sp)
                    }
                }
            }
        }

        // Admin Class & Section Dropdown Selectors
        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Class Selector
                PortalSelect(
                    label = "Class",
                    value = selectedClassId,
                    options = listOf("" to "Select Class") + activeClasses,
                    enabled = true,
                    onSelect = {
                        selectedClassId = it
                        selectedSectionId = ""
                    }
                )
                if (selectedClassId.isNotBlank()) {
                    val classSections = activeSections.filter { it.third == selectedClassId }
                    if (classSections.isNotEmpty()) {
                        PortalSelect(
                            label = "Section",
                            value = selectedSectionId,
                            options = listOf("" to "All Sections") + classSections.map { it.first to it.second },
                            enabled = true,
                            onSelect = { selectedSectionId = it }
                        )
                    }
                }
            }
        }

        // Days of week selector tabs
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TimetableEntry.days.forEach { dayName ->
                    val isSelected = selectedDay == dayName
                    Surface(
                        onClick = { selectedDay = dayName },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ) {
                        Text(
                            text = dayName,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (isSelected) Color.White else Color(0xFF64748B),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        error?.let { PortalNotice(it, failure = true) }

        if (loading) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val dayEntries = entries.filter { it.day.equals(selectedDay, ignoreCase = true) }
            if (dayEntries.isEmpty()) {
                PortalEmptyState(
                    icon = Icons.Outlined.Schedule,
                    title = "No lectures scheduled",
                    description = "There are no periods scheduled for $selectedDay."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayEntries, key = { it.id }) { item ->
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
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${item.periodName} • ${item.startTime} - ${item.endTime}",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isAdmin) {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingEntry = item
                                                    showAddDialog = true
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color(0xFF0284C7), modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        repo.delete(session.schoolId, item.id)
                                                        refresh++
                                                    }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = item.subjectName.ifBlank { "Subject" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (isTeacher) "Class: ${item.className} ${item.section}" else "Teacher: ${item.teacherName.ifBlank { "Assigned Faculty" }}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF475569)
                                    )
                                    if (item.room.isNotBlank()) {
                                        Text(
                                            text = "Room: ${item.room}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Dialog for Admin
    if (showAddDialog && editingEntry != null) {
        var periodNum by remember { mutableStateOf(editingEntry!!.periodNumber.toString()) }
        var periodName by remember { mutableStateOf(editingEntry!!.periodName) }
        var startTime by remember { mutableStateOf(editingEntry!!.startTime) }
        var endTime by remember { mutableStateOf(editingEntry!!.endTime) }
        var subjectId by remember { mutableStateOf(editingEntry!!.subjectId) }
        var teacherId by remember { mutableStateOf(editingEntry!!.teacherId) }
        var room by remember { mutableStateOf(editingEntry!!.room) }
        var copyToWeekdays by remember { mutableStateOf(selectedDay != "Friday") }
        var saving by remember { mutableStateOf(false) }
        var dialogError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!saving) showAddDialog = false },
            title = { Text(if (editingEntry!!.id.isBlank()) "Add Period" else "Edit Period", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Day: $selectedDay • ${editingEntry!!.className} ${editingEntry!!.section}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = periodNum,
                            onValueChange = {
                                periodNum = it
                                periodName = "Period $it"
                            },
                            label = { Text("Period #") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = periodName,
                            onValueChange = { periodName = it },
                            label = { Text("Title") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("Start Time") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("End Time") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Subject dropdown
                    PortalSelect(
                        label = "Subject",
                        value = subjectId,
                        options = listOf("" to "Select Subject") + activeSubjects,
                        enabled = !saving,
                        onSelect = { subjectId = it }
                    )

                    // Teacher dropdown
                    PortalSelect(
                        label = "Teacher",
                        value = teacherId,
                        options = listOf("" to "Select Teacher") + activeTeachers,
                        enabled = !saving,
                        onSelect = { teacherId = it }
                    )

                    OutlinedTextField(
                        value = room,
                        onValueChange = { room = it },
                        label = { Text("Room / Lab (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (selectedDay != "Friday") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = copyToWeekdays, onCheckedChange = { copyToWeekdays = it }, enabled = !saving)
                            Spacer(Modifier.width(6.dp))
                            Text("Apply to Mon, Tue, Wed, Thu, Sat", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    dialogError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val subName = activeSubjects.firstOrNull { it.first == subjectId }?.second.orEmpty()
                        val tName = activeTeachers.firstOrNull { it.first == teacherId }?.second.orEmpty()
                        if (subjectId.isBlank()) {
                            dialogError = "Please select a subject."
                            return@Button
                        }
                        saving = true
                        dialogError = null
                        scope.launch {
                            try {
                                val toSave = editingEntry!!.copy(
                                    day = selectedDay,
                                    periodNumber = periodNum.toIntOrNull() ?: 1,
                                    periodName = periodName.ifBlank { "Period $periodNum" },
                                    startTime = startTime,
                                    endTime = endTime,
                                    subjectId = subjectId,
                                    subjectName = subName,
                                    teacherId = teacherId,
                                    teacherName = tName,
                                    room = room
                                )
                                if (copyToWeekdays && selectedDay != "Friday") {
                                    repo.saveToWeekdays(toSave)
                                } else {
                                    repo.save(toSave)
                                }
                                saving = false
                                showAddDialog = false
                                refresh++
                            } catch (e: Exception) {
                                saving = false
                                dialogError = e.message ?: "Could not save timetable period."
                            }
                        }
                    },
                    enabled = !saving
                ) {
                    Text(if (saving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddDialog = false },
                    enabled = !saving
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
