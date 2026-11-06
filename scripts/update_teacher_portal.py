# Update TeacherPortalScreen.kt with full academic report & print
p = "app/src/main/java/pk/edunexa/app/ui/TeacherPortalScreen.kt"
with open(p, "r", encoding="utf-8") as f:
    c = f.read()

# Make sure print imports are present
if "import android.print.PrintManager" not in c:
    c = c.replace("import android.app.DatePickerDialog", "import android.app.DatePickerDialog\nimport android.print.PrintManager\nimport android.print.PrintAttributes\nimport android.webkit.WebView\nimport android.webkit.WebViewClient")

old_dialog = """    inspectingStudent?.let { student ->
        val photo = student.text("profilePhoto").ifBlank { student.text("photoUrl") }
        val phone = student.text("guardianPhone").ifBlank { student.text("phone") }
        val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
        AlertDialog(
            onDismissRequest = { inspectingStudent = null },
            title = { Text("Student Profile & Academic Record", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeacherPhotoAvatar(photo, student.text("fullName"), size = 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(student.text("fullName"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Roll: " + student.text("rollNo") + " • " + student.text("admissionNo"), color = TeacherMuted)
                            Text(student.text("className") + " • " + student.text("section"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("Status: " + student.text("status"), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    HorizontalDivider()

                    val father = student.text("fatherName").ifBlank { student.text("guardianName") }
                    if (father.isNotBlank()) Text("Guardian: $father", fontWeight = FontWeight.Medium)
                    if (phone.isNotBlank()) Text("Phone / WhatsApp: $phone")
                    val addr = student.text("address")
                    if (addr.isNotBlank()) Text("Address: $addr", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                    val dob = student.text("dateOfBirth").ifBlank { student.text("dob") }
                    if (dob.isNotBlank()) Text("Date of Birth: $dob", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)

                    if (phone.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = "Assalam-o-Alaikum,\\nTeacher ${session.name} ($schoolName) regarding student ${student.text("fullName")} (Roll ${student.text("rollNo")})."
                                    pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, phone, msg)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("WhatsApp")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone)))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Call")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingStudent = null }) {
                    Text("Close")
                }
            }
        )
    }"""

new_dialog = """    inspectingStudent?.let { student ->
        val photo = student.text("profilePhoto").ifBlank { student.text("photoUrl") }
        val phone = student.text("guardianPhone").ifBlank { student.text("phone") }
        val schoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
        var studentMarks by remember(student.id) { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
        var studentAttendancePct by remember(student.id) { mutableStateOf("Calculating...") }

        LaunchedEffect(student.id) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val mSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("marks").whereEqualTo("studentId", student.id).get()
                    )
                    val mList = mSnap.documents.map { it.data.orEmpty() }
                    withContext(Dispatchers.Main) { studentMarks = mList }

                    val aSnap = com.google.android.gms.tasks.Tasks.await(
                        db.collection("schools").document(session.schoolId)
                            .collection("attendance").whereEqualTo("studentId", student.id).get()
                    )
                    val total = aSnap.size()
                    val pres = aSnap.documents.count { it.getString("status") in setOf("Present", "Late") }
                    val pct = if (total > 0) "${((pres * 100) / total)}%" else "100%"
                    withContext(Dispatchers.Main) { studentAttendancePct = pct }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { inspectingStudent = null },
            title = { Text("Student Academic Profile & Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeacherPhotoAvatar(photo, student.text("fullName"), size = 64.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(student.text("fullName"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Roll: " + student.text("rollNo") + " • " + student.text("admissionNo"), color = TeacherMuted)
                            Text(student.text("className") + " • " + student.text("section"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("Attendance: $studentAttendancePct", color = Color(0xFF15803D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider()

                    val father = student.text("fatherName").ifBlank { student.text("guardianName") }
                    if (father.isNotBlank()) Text("Guardian: $father", fontWeight = FontWeight.Medium)
                    if (phone.isNotBlank()) Text("Phone / WhatsApp: $phone")
                    val addr = student.text("address")
                    if (addr.isNotBlank()) Text("Address: $addr", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)

                    Text("Test Performance & Marks:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    if (studentMarks.isEmpty()) {
                        Text("No test records yet.", color = TeacherMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        studentMarks.take(6).forEach { m ->
                            val obt = m["marksObtained"]?.toString() ?: "—"
                            val tot = m["totalMarks"]?.toString() ?: "—"
                            val st = m["status"]?.toString() ?: "Present"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(m["subject"]?.toString().orEmpty() + " • " + (m["testTitle"] ?: "Test"), fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text("$obt / $tot ($st)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (st == "Absent") Color.Red else MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            printStudentAcademicReport(context, schoolName, student, studentMarks, studentAttendancePct, session.schoolId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print Full Academic Report")
                    }

                    if (phone.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val msg = "Assalam-o-Alaikum,\\nTeacher ${session.name} ($schoolName) regarding student ${student.text("fullName")} (Roll ${student.text("rollNo")}).\\nAttendance: $studentAttendancePct"
                                    pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, phone, msg)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("WhatsApp")
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + phone)))
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Call")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingStudent = null }) {
                    Text("Close")
                }
            }
        )
    }"""

assert old_dialog in c, "old_dialog not found"
c = c.replace(old_dialog, new_dialog, 1)

print_helper = '''
private fun printStudentAcademicReport(context: android.content.Context, schoolName: String, student: DiaryItem, marks: List<Map<String, Any?>>, attendancePct: String, schoolId: String) {
    fun esc(value: String) = value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    val rows = if (marks.isEmpty()) "<tr><td colspan=\\"4\\">No tests recorded yet.</td></tr>"
    else marks.joinToString("") { m ->
        val sub = esc(m["subject"]?.toString().orEmpty())
        val title = esc(m["testTitle"]?.toString().orEmpty().ifBlank { "Test" })
        val obt = esc(m["marksObtained"]?.toString() ?: "—")
        val tot = esc(m["totalMarks"]?.toString() ?: "—")
        val st = esc(m["status"]?.toString() ?: "Present")
        "<tr><td>$sub</td><td>$title</td><td>$obt / $tot</td><td>$st</td></tr>"
    }

    val html = """<!doctype html><html><head><meta charset="utf-8"><style>
    @page { size: A4 portrait; margin: 15mm; }
    body { font-family: Arial, sans-serif; font-size: 11px; color: #1e293b; line-height: 1.5; }
    h1 { text-align: center; font-size: 20px; color: #0f172a; margin: 4px 0; }
    h2 { text-align: center; font-size: 14px; color: #b8860b; margin: 2px 0 16px; }
    .card { background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 12px; margin-bottom: 14px; }
    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
    th, td { border: 1px solid #cbd5e1; padding: 6px 8px; text-align: left; }
    th { background: #e2e8f0; font-weight: bold; }
    .sig { margin-top: 40px; display: flex; justify-content: space-between; font-size: 11px; }
    </style></head><body>
    <h1>${esc(schoolName)}</h1>
    <h2>Student Academic Progress & Performance Report</h2>
    <div class="card">
        <b>Name: ${esc(student.text("fullName"))}</b> &bull; Roll No: ${esc(student.text("rollNo"))} &bull; Admission: ${esc(student.text("admissionNo"))}<br>
        Class: ${esc(student.text("className"))} &bull; Section: ${esc(student.text("section"))}<br>
        Guardian: ${esc(student.text("fatherName").ifBlank { student.text("guardianName") })} &bull; Phone: ${esc(student.text("guardianPhone").ifBlank { student.text("phone") })}<br>
        <b>Overall Attendance: $attendancePct</b> &bull; Date: ${FeePolicy.today()}
    </div>
    <h3>Assessment & Test Results</h3>
    <table><thead><tr><th>Subject</th><th>Test Name</th><th>Marks Obtained</th><th>Status</th></tr></thead>
    <tbody>$rows</tbody></table>
    <div class="sig">
        <span>Class Incharge: ______________________</span>
        <span>Principal Signature: ______________________</span>
    </div>
    </body></html>"""

    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        private var printed = false
        override fun onPageFinished(view: WebView, url: String?) {
            if (printed) return
            printed = true
            val mgr = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
            mgr.print("Student-Report-${student.text("rollNo")}", view.createPrintDocumentAdapter("Academic Report"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asPortrait()).build())
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}
'''

c = c + print_helper

with open(p, "w", encoding="utf-8") as f:
    f.write(c)
print("TeacherPortalScreen.kt updated successfully with student report.")
