package pk.edunexa.app.timetable

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

data class TimetableEntry(
    val id: String = "",
    val schoolId: String = "",
    val academicSessionId: String = "",
    val classId: String = "",
    val className: String = "",
    val sectionId: String = "",
    val section: String = "",
    val day: String = "Monday", // Monday, Tuesday, Wednesday, Thursday, Friday, Saturday
    val periodNumber: Int = 1,
    val periodName: String = "Period 1",
    val subjectId: String = "",
    val subjectName: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val startTime: String = "08:00 AM",
    val endTime: String = "08:45 AM",
    val room: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "schoolId" to schoolId,
        "academicSessionId" to academicSessionId,
        "classId" to classId,
        "className" to className,
        "sectionId" to sectionId,
        "section" to section,
        "day" to day,
        "periodNumber" to periodNumber,
        "periodName" to periodName,
        "subjectId" to subjectId,
        "subjectName" to subjectName,
        "teacherId" to teacherId,
        "teacherName" to teacherName,
        "startTime" to startTime,
        "endTime" to endTime,
        "room" to room,
        "updatedAt" to updatedAt
    )

    companion object {
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Saturday")

        val periods = listOf(
            Triple(1, "Period 1", "08:00 AM - 08:45 AM"),
            Triple(2, "Period 2", "08:45 AM - 09:30 AM"),
            Triple(3, "Period 3", "09:30 AM - 10:15 AM"),
            Triple(4, "Period 4", "10:15 AM - 11:00 AM"),
            Triple(5, "Period 5", "11:30 AM - 12:15 PM"),
            Triple(6, "Period 6", "12:15 PM - 01:00 PM"),
            Triple(7, "Period 7", "01:00 PM - 01:40 PM"),
            Triple(8, "Period 8", "01:40 PM - 02:20 PM")
        )

        fun fromDoc(id: String, d: Map<String, Any?>): TimetableEntry = TimetableEntry(
            id = id,
            schoolId = d["schoolId"]?.toString().orEmpty(),
            academicSessionId = d["academicSessionId"]?.toString().orEmpty(),
            classId = d["classId"]?.toString().orEmpty(),
            className = d["className"]?.toString().orEmpty(),
            sectionId = d["sectionId"]?.toString().orEmpty(),
            section = d["section"]?.toString().orEmpty(),
            day = d["day"]?.toString() ?: "Monday",
            periodNumber = (d["periodNumber"] as? Number)?.toInt() ?: 1,
            periodName = d["periodName"]?.toString() ?: "Period 1",
            subjectId = d["subjectId"]?.toString().orEmpty(),
            subjectName = d["subjectName"]?.toString().orEmpty(),
            teacherId = d["teacherId"]?.toString().orEmpty(),
            teacherName = d["teacherName"]?.toString().orEmpty(),
            startTime = d["startTime"]?.toString() ?: "08:00 AM",
            endTime = d["endTime"]?.toString() ?: "08:45 AM",
            room = d["room"]?.toString().orEmpty(),
            updatedAt = (d["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

class TimetableRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {
    private fun tableCol(schoolId: String) = db.collection("schools").document(schoolId).collection("timetables")
    private fun sessionsCol(schoolId: String) = db.collection("schools").document(schoolId).collection("examSessions")

    suspend fun loadForClass(schoolId: String, classId: String, sectionId: String = ""): List<TimetableEntry> = withContext(Dispatchers.IO) {
        // Try reading from examSessions timetable document first (universally allowed for student & teacher in Firestore rules)
        val fromSessions = runCatching {
            val doc = sessionsCol(schoolId).document("timetable_" + classId).get(Source.DEFAULT).await()
            if (doc.exists()) {
                val list = (doc.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
                list.map { TimetableEntry.fromDoc(it["id"]?.toString().orEmpty(), it) }
                    .filter { sectionId.isBlank() || it.sectionId == sectionId }
            } else emptyList()
        }.getOrDefault(emptyList())

        if (fromSessions.isNotEmpty()) {
            return@withContext fromSessions.sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }

        // Fallback to timetables collection
        val fromCol = runCatching {
            var q = tableCol(schoolId).whereEqualTo("classId", classId)
            if (sectionId.isNotBlank()) {
                q = q.whereEqualTo("sectionId", sectionId)
            }
            val snap = q.get(Source.DEFAULT).await()
            snap.documents.map { TimetableEntry.fromDoc(it.id, it.data.orEmpty()) }
        }.getOrDefault(emptyList())

        if (fromCol.isNotEmpty()) {
            return@withContext fromCol.sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }

        // Fallback to academicConfig document
        runCatching {
            val fallbackRef = db.collection("schools").document(schoolId)
                .collection("academicConfig").document("timetable_" + classId)
            val doc = fallbackRef.get(Source.DEFAULT).await()
            val list = (doc.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
            list.map { TimetableEntry.fromDoc(it["id"]?.toString().orEmpty(), it) }
                .filter { sectionId.isBlank() || it.sectionId == sectionId }
                .sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }.getOrDefault(emptyList())
    }

    suspend fun loadForTeacher(schoolId: String, teacherId: String, teacherName: String = ""): List<TimetableEntry> = withContext(Dispatchers.IO) {
        // Read from examSessions timetable documents (universally accessible)
        val fromSessions = runCatching {
            val snap = sessionsCol(schoolId).whereEqualTo("kind", "timetable").get(Source.DEFAULT).await()
            val all = snap.documents.flatMap { doc ->
                val list = (doc.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
                list.map { TimetableEntry.fromDoc(it["id"]?.toString().orEmpty(), it) }
            }
            all.filter { entry ->
                (teacherId.isNotBlank() && entry.teacherId == teacherId) ||
                (teacherName.isNotBlank() && entry.teacherName.equals(teacherName, ignoreCase = true)) ||
                (teacherName.isNotBlank() && entry.teacherName.contains(teacherName, ignoreCase = true))
            }
        }.getOrDefault(emptyList())

        if (fromSessions.isNotEmpty()) {
            return@withContext fromSessions.sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }

        // Fallback to timetables collection
        val fromCol = runCatching {
            val snap = tableCol(schoolId).get(Source.DEFAULT).await()
            snap.documents.map { TimetableEntry.fromDoc(it.id, it.data.orEmpty()) }.filter { entry ->
                (teacherId.isNotBlank() && entry.teacherId == teacherId) ||
                (teacherName.isNotBlank() && entry.teacherName.equals(teacherName, ignoreCase = true))
            }
        }.getOrDefault(emptyList())

        if (fromCol.isNotEmpty()) {
            return@withContext fromCol.sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }

        // Fallback to academicConfig
        runCatching {
            val cfgCol = db.collection("schools").document(schoolId).collection("academicConfig")
            val docs = cfgCol.get(Source.DEFAULT).await()
            val allEntries = docs.documents.filter { it.id.startsWith("timetable_") }.flatMap { d ->
                val list = (d.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
                list.map { TimetableEntry.fromDoc(it["id"]?.toString().orEmpty(), it) }
            }
            allEntries.filter { entry ->
                (teacherId.isNotBlank() && entry.teacherId == teacherId) ||
                (teacherName.isNotBlank() && entry.teacherName.equals(teacherName, ignoreCase = true))
            }.sortedWith(compareBy({ TimetableEntry.days.indexOf(it.day) }, { it.periodNumber }))
        }.getOrDefault(emptyList())
    }

    suspend fun save(entry: TimetableEntry): String = withContext(Dispatchers.IO) {
        val id = entry.id.ifBlank { "tt-" + UUID.randomUUID().toString() }
        val toSave = entry.copy(id = id, updatedAt = System.currentTimeMillis())

        // 1. Save in examSessions (universally accessible to all students and teachers)
        runCatching {
            val sessionDocRef = sessionsCol(entry.schoolId).document("timetable_" + entry.classId)
            val existing = runCatching { sessionDocRef.get(Source.DEFAULT).await() }.getOrNull()
            val list = (existing?.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
            val filtered = list.filterNot { it["id"] == id || (it["day"] == toSave.day && (it["periodNumber"] as? Number)?.toInt() == toSave.periodNumber && it["sectionId"] == toSave.sectionId) }
            sessionDocRef.set(
                mapOf(
                    "id" to ("timetable_" + entry.classId),
                    "kind" to "timetable",
                    "classId" to entry.classId,
                    "className" to entry.className,
                    "schoolId" to entry.schoolId,
                    "entries" to (filtered + toSave.toMap()),
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }

        // 2. Also save into academicConfig fallback
        runCatching {
            val fallbackRef = db.collection("schools").document(entry.schoolId)
                .collection("academicConfig").document("timetable_" + entry.classId)
            val existing = runCatching { fallbackRef.get(Source.DEFAULT).await() }.getOrNull()
            val list = (existing?.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
            val filtered = list.filterNot { it["id"] == id || (it["day"] == toSave.day && (it["periodNumber"] as? Number)?.toInt() == toSave.periodNumber && it["sectionId"] == toSave.sectionId) }
            fallbackRef.set(mapOf("entries" to (filtered + toSave.toMap())), SetOptions.merge()).await()
        }

        // 3. Also try primary timetables collection
        runCatching {
            tableCol(entry.schoolId).document(id).set(toSave.toMap()).await()
        }

        id
    }

    suspend fun saveToWeekdays(entry: TimetableEntry): Unit = withContext(Dispatchers.IO) {
        val targets = if (entry.day == "Friday") listOf("Friday") else TimetableEntry.weekdays
        targets.forEach { targetDay ->
            val periodId = "tt-" + entry.classId + "-" + (if (entry.sectionId.isNotBlank()) entry.sectionId + "-" else "") + targetDay.lowercase() + "-p" + entry.periodNumber
            save(entry.copy(id = periodId, day = targetDay))
        }
    }

    suspend fun copyDayToWeekdays(schoolId: String, classId: String, sectionId: String, sourceDay: String = "Monday"): Int = withContext(Dispatchers.IO) {
        val currentEntries = loadForClass(schoolId, classId, sectionId)
        val sourceEntries = currentEntries.filter { it.day.equals(sourceDay, ignoreCase = true) }
        if (sourceEntries.isEmpty()) return@withContext 0

        val targetDays = listOf("Tuesday", "Wednesday", "Thursday", "Saturday")
        var count = 0
        targetDays.forEach { targetDay ->
            sourceEntries.forEach { src ->
                val newId = "tt-" + classId + "-" + (if (sectionId.isNotBlank()) sectionId + "-" else "") + targetDay.lowercase() + "-p" + src.periodNumber
                save(src.copy(id = newId, day = targetDay))
                count++
            }
        }
        count
    }

    suspend fun delete(schoolId: String, id: String) = withContext(Dispatchers.IO) {
        runCatching { tableCol(schoolId).document(id).delete().await() }
        runCatching {
            val snap = sessionsCol(schoolId).whereEqualTo("kind", "timetable").get(Source.DEFAULT).await()
            snap.documents.forEach { doc ->
                val list = (doc.get("entries") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.entries?.associate { e -> e.key.toString() to e.value } }.orEmpty()
                if (list.any { it["id"] == id }) {
                    doc.reference.update("entries", list.filterNot { it["id"] == id }).await()
                }
            }
        }
    }
}
