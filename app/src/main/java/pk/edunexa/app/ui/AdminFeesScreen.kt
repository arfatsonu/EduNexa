package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.outlined.Message
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.fees.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private fun feeMoney(value: Double) = "Rs. " + java.text.NumberFormat.getNumberInstance(Locale.US).format(value)
private fun Map<String, Any?>.feeText(key: String) = this[key]?.toString().orEmpty()

@Composable
fun AdminFeesScreen(
    session: PortalSession, onSaved: () -> Unit, modifier: Modifier = Modifier,
    studentId: String? = null, studentName: String = "", initialSessionId: String = "",
    onBack: (() -> Unit)? = null,
    onBusy: (Boolean)->Unit = {}
) {
    val repo = remember(session.uid) { FeeRepository(session) }
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val currentSchoolName = LocalSchoolBranding.current.schoolName.ifBlank { "EduNexa" }
    var data by remember { mutableStateOf<FeeData?>(null) }
    var loading by remember { mutableStateOf(true) }; var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }
    AutoDismissSuccess(notice) { notice = null }
    LaunchedEffect(busy){onBusy(busy)}
    DisposableEffect(Unit){onDispose{onBusy(false)}}
    var sessionId by rememberSaveable(studentId) { mutableStateOf(initialSessionId) }
    var month by rememberSaveable(studentId) { mutableStateOf(if(studentId == null) FeePolicy.month() else "") }
    var classId by rememberSaveable { mutableStateOf("") }; var sectionId by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable(studentId) { mutableStateOf(if(studentId == null) FeePolicy.TUITION else "") }
    var status by rememberSaveable { mutableStateOf("") }; var query by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }; var tab by rememberSaveable { mutableStateOf("Collection") }
    var receiving by remember { mutableStateOf<DiaryItem?>(null) }; var history by remember { mutableStateOf<DiaryItem?>(null) }; var generate by remember { mutableStateOf(false) }
    var receiptDialog by remember { mutableStateOf<Pair<DiaryItem, Map<String, Any?>>?>(null) }
    LaunchedEffect(session.uid, studentId, refresh) {
        loading = true
        runCatching { repo.load(studentId) }.onSuccess {
            data = it; error = null
            it.records.forEach { bill ->
                val store=pk.edunexa.app.workflow.LocalDraftStore(context,session.uid,session.schoolId,"payment",bill.id)
                val request=store.load()["requestId"]
                if(request!=null && DiaryPolicy.maps(bill.raw["paymentHistory"]).any { p -> p["id"]==request }) { store.clear();history=bill;notice="Previous payment verified. Its saved receipt is ready." }
            }
            if(sessionId.isBlank()) sessionId = it.currentSession.feeText("id")
            else sessionId = it.options("sessions").firstOrNull { row -> row["id"] == sessionId || row["name"] == sessionId }?.feeText("id") ?: sessionId
        }.onFailure { if(it is CancellationException)throw it; error = it.message ?: "Could not load fees." }
        loading = false
    }
    var scannedStudent by remember { mutableStateOf<DiaryItem?>(null) }
    if(studentId == null && scannedStudent != null) {
        val pupil=scannedStudent!!
        key(pupil.id) { AdminFeesScreen(session,onSaved,modifier,pupil.id,pupil.text("fullName"),pupil.text("academicSessionId").ifBlank { pupil.text("academicSession") },onBack={scannedStudent=null},onBusy=onBusy) }
        return
    }
    val current = data
    BackHandler(enabled = receiving != null || onBack != null) { if(!busy) { if(receiving != null) { receiving = null; error = null } else onBack?.invoke() } }
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
        if(receiving != null) FeeReceiveForm(session,receiving!!, busy, error, { receiving = null; error = null }) { record, amount, method, notes, id ->
            if(!busy) { busy = true; error = null
            scope.launch {
                runCatching { repo.receive(record, amount, method, notes, id) }.onSuccess { saved ->
                    pk.edunexa.app.workflow.LocalDraftStore(context,session.uid,session.schoolId,"payment",saved.id).clear();busy = false; receiving = null; val ps = DiaryPolicy.maps(saved.raw["paymentHistory"]); receiptDialog = saved to (ps.lastOrNull() ?: emptyMap()); history = null; notice = "Payment received. Receipt is ready."; refresh += 1; onSaved()
                }.onFailure { if(it is CancellationException)throw it;busy = false; error = it.message ?: "Could not save the payment. You can safely retry the same request." }
            } }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if(onBack != null) IconButton(onClick = onBack, enabled = !busy) { Icon(Icons.Outlined.ArrowBack, "Back to students") }
                Column(Modifier.weight(1f)) {
                    Text(if(studentId == null) "Fee Collection" else "Student Fees", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if(studentId == null) "Payments, dues & receipts" else studentName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                }
                IconButton(onClick = { refresh += 1 }, enabled = !busy && !loading) { Icon(Icons.Outlined.Refresh, "Refresh fees") }
            }
            if(studentId == null) ScanFeeButton(!busy && !loading) {
                if(!busy) { busy=true;error=null;scope.launch {
                    try { val raw=scanStudentQr(context); if(raw!=null) scannedStudent=CardFeeRepository(session).lookup(raw) }
                    catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: "Could not open student fees. Try again."}
                    finally {busy=false}
                } }
            }
            PortalTabs(listOf("Collection", "Overview", "Archived / Old Dues"), tab, !busy) { tab = it }
            notice?.let { PortalNotice(it) }
            error?.let { PortalNotice(it, failure = true) }
            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            if(current != null) {
                val studentsById=remember(current){current.students.associateBy{it.id}}
                val archived = tab == "Archived / Old Dues"
                val currentStudentIds = FeeVisibility.currentStudentIds(current.students)
                val scopeRows = current.records.filter { FeeVisibility.isCurrent(it.raw, currentStudentIds) != archived }.filter { (studentId == null || it.text("studentId") == studentId) && current.inSession(it, sessionId) && (classId.isBlank() || it.text("classId") == classId) && (sectionId.isBlank() || it.text("sectionId") == sectionId) }
                val displayMonth = month.ifBlank { FeePolicy.month() }
                val monthly = scopeRows.filter { it.text("feeType") == FeePolicy.TUITION && it.text("month") == displayMonth }
                val eligible = current.students.filter { it.raw["isDeleted"] != true && (studentId == null || it.id == studentId) && current.inSession(it, sessionId) && it.text("status") in setOf("Active", "On Leave") && (classId.isBlank() || it.text("classId") == classId) && (sectionId.isBlank() || it.text("sectionId") == sectionId) }
                val records = scopeRows.map { FeeIdentity.display(it, current.students, current.options("sessions").map { row -> setOf(row.feeText("id"), row.feeText("name")) }) }.filter { (type.isBlank() || it.text("feeType") == type) && (it.text("feeType") != FeePolicy.TUITION || month.isBlank() || it.text("month") == month) && (status.isBlank() || FeePolicy.status(it.raw) == status) && (listOf("studentName", "rollNo", "admissionNo").map { key -> it.text(key) } + studentsById[it.text("studentId")]?.text("guardianPhone").orEmpty()).any { value -> value.contains(query.trim(), true) } }.sortedWith(compareBy<DiaryItem> { FeePolicy.balance(it.raw) <= 0 }.thenBy { it.text("studentName") }.thenByDescending { runCatching { FeePolicy.monthKey(it.text("month")) }.getOrDefault("") })
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                    if(archived) item { PortalNotice("Removed or missing student profiles. These bills are excluded from current fee totals. Saved receipts remain available here.") }
                    item {
                        PortalSummaryCard(if(archived) "Archived tuition" else "Monthly tuition", displayMonth,
                            "Collected" to feeMoney(monthly.sumOf { FeePolicy.paid(it.raw) }),
                            "Outstanding" to feeMoney(monthly.sumOf { FeePolicy.balance(it.raw) }))
                    }
                    item {
                        FeeCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${records.size} bills", fontWeight = FontWeight.Bold)
                                    Text(listOf(displayMonth, current.options("sessions").firstOrNull { it["id"] == sessionId }?.feeText("name").orEmpty()).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                                }
                                TextButton(onClick = { filters = !filters }, enabled = !busy) { Text(if(filters) "Hide filters" else "Filters") }
                            }
                            if(filters) {
                                val calendar = Calendar.getInstance()
                                val recent = (0..11).map { n -> (calendar.clone() as Calendar).apply { add(Calendar.MONTH, -n) }.time }.map { SimpleDateFormat("MMMM yyyy", Locale.US).format(it) }
                                FeeSelect("Month", month, listOf("" to "All Months") + (recent + scopeRows.map { it.text("month") }.filter { it.isNotBlank() }).distinct().sortedByDescending { runCatching { FeePolicy.monthKey(it) }.getOrDefault("") }.map { it to it }, !busy) { month = it }
                                FeeSelect("Academic Session", sessionId, current.options("sessions").map { it.feeText("id") to it.feeText("name") }, !busy) { sessionId = it; classId = ""; sectionId = "" }
                                if(studentId == null) {
                                    FeeSelect("Class", classId, listOf("" to "All Classes") + current.options("classes").map { it.feeText("id") to it.feeText("name") }, !busy) { classId = it; sectionId = "" }
                                    FeeSelect("Section", sectionId, listOf("" to "All Sections") + current.options("sections").filter { classId.isBlank() || it["classId"] == classId }.map { it.feeText("id") to it.feeText("name") }, !busy) { sectionId = it }
                                }
                                FeeSelect("Fee Type", type, listOf("" to "All Charges") + (FeePolicy.structureTypes + scopeRows.map { it.text("feeType") }).distinct().map { it to if(it == "Other") "Other Funds" else it }, !busy) { type = it }
                                FeeSelect("Payment Status", status, listOf("" to "All Statuses") + listOf("Paid", "Partial", "Unpaid", "Overdue").map { it to it }, !busy) { status = it }
                            }
                            if(studentId == null && tab == "Collection") OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Name, registration, roll or guardian phone") }, shape = RoundedCornerShape(12.dp))
                            if(type != FeePolicy.TUITION) Text("Annual and other funds cover the whole academic session.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            if(!archived && (month.isBlank() || month == FeePolicy.month()) && sessionId == current.currentSession.feeText("id")) OutlinedButton(onClick = { generate = true }, enabled = !busy && !loading && error == null && eligible.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text(if(busy) "Syncing…" else "Generate / Sync Current Bills") }
                        }
                    }
                    if(tab == "Overview") {
                        item { FeeCard { Text("Tuition • $displayMonth", fontWeight = FontWeight.Bold); Text("Billed: ${feeMoney(monthly.sumOf { FeePolicy.amount(it.raw, "netPayable") })}"); Text("Paid ${monthly.count { FeePolicy.status(it.raw) == "Paid" }}  •  Partial ${monthly.count { FeePolicy.status(it.raw) == "Partial" }}  •  Unpaid ${monthly.count { FeePolicy.status(it.raw) == "Unpaid" }}  •  Overdue ${monthly.count { FeePolicy.status(it.raw) == "Overdue" }}", style = MaterialTheme.typography.bodySmall) } }
                        val oldDues = scopeRows.filter { it.text("feeType") == FeePolicy.TUITION && runCatching { FeePolicy.monthKey(it.text("month")) < FeePolicy.monthKey(displayMonth) }.getOrDefault(false) }.sumOf { FeePolicy.balance(it.raw) }
                        item { FeeCard { Text("Earlier tuition outstanding", fontWeight = FontWeight.Bold); Text(feeMoney(oldDues), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error) } }
                        items(scopeRows.filter { it.text("feeType") != FeePolicy.TUITION }.groupBy { it.text("feeType") }.toList(), key = { it.first }) { (name, rows) -> FeeCard { Text("$name • Whole Session", fontWeight = FontWeight.Bold); Text("Billed: ${feeMoney(rows.sumOf { FeePolicy.amount(it.raw,"netPayable") })}"); Text("Collected: ${feeMoney(rows.sumOf { FeePolicy.paid(it.raw) })}"); Text("Outstanding: ${feeMoney(rows.sumOf { FeePolicy.balance(it.raw) })}") } }
                    } else {
                        if(records.isEmpty()) item { PortalEmptyState(Icons.Outlined.CreditCard, "No bills in this selection", "Choose another month or fee type. Generate current bills from the student's saved fee structure.") }
                        items(records, key = { it.id }) { record -> FeeCard {
                            val savedBill = current.records.first { it.id == record.id }
                            val linkedProfile = FeeIdentity.profile(savedBill, current.students)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(record.text("studentName"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                PortalStatusBadge(FeePolicy.status(record.raw))
                            }
                            Text("${record.text("className")} • ${record.text("section")} • Roll ${record.text("rollNo")}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            if(record.text("rollNo") != savedBill.text("rollNo") || record.text("admissionNo") != savedBill.text("admissionNo")) Text("Current student details shown. Printed bills retain their issued details.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text("Admission: ${record.text("admissionNo").ifBlank { "Unavailable" }}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            if(linkedProfile == null || linkedProfile.raw["isDeleted"] == true) Text(if(linkedProfile == null) "Student profile unavailable" else "Removed student", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text("${record.text("feeType")} • ${record.text("month")}", style = MaterialTheme.typography.labelLarge)
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text("Outstanding", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)); Text(feeMoney(FeePolicy.balance(record.raw)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                                Column(horizontalAlignment = Alignment.End) { Text("Paid ${feeMoney(FeePolicy.paid(record.raw))}", style = MaterialTheme.typography.bodySmall); Text("Bill ${feeMoney(FeePolicy.amount(record.raw,"netPayable"))}", style = MaterialTheme.typography.bodySmall) }
                            }
                            Text("Due: ${pk.edunexa.app.fees.FeeReference.date(record.text("dueDate"))}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            Text("Voucher: ${pk.edunexa.app.fees.FeeReference.display(record.text("voucherNo"))}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                            val credit = (FeePolicy.paid(record.raw) - FeePolicy.amount(record.raw,"netPayable")).coerceAtLeast(0.0)
                            if(credit > 0) Text("Overpayment retained: ${feeMoney(credit)}")
                            if(!archived && FeePolicy.balance(record.raw) > 0) Button(onClick = { receiving = savedBill; error = null }, enabled = !busy && !loading && error == null, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Receive Fee") }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { FeePrinter.print(context, current.schoolName, savedBill, null,session.schoolId) }) { Text("Print Saved Bill") }
                                TextButton(onClick = { history = savedBill }) { Text("Receipts & History") }
                            }
                        } }
                    }
                }
                if(generate) AlertDialog(onDismissRequest = { if(!busy) generate = false }, title = { Text("Sync Current Bills?") }, text = { Column { Text("${eligible.size} active students. Saved Monthly Fee, Annual Fund and Other Funds will create/update current bills. Existing payments remain saved. Historical months keep their saved rates."); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = {
                    busy = true; error = null
                    scope.launch { runCatching { repo.syncCurrentMonth(eligible.map { it.id }, sessionId) }.onSuccess { busy = false; generate = false; notice = "Current bills synced."; refresh += 1; onSaved() }.onFailure { busy = false; error = (it.message ?: "Sync failed") + " Refresh; completed bills are preserved and can be safely retried."; refresh += 1 } }
                }, enabled = !busy) { Text(if(busy) "Syncing…" else "Confirm") } }, dismissButton = { TextButton(onClick = { generate = false }, enabled = !busy) { Text("Cancel") } })
            } else if(!loading) TextButton(onClick = { refresh += 1 }) { Text("Retry") }
        }
    }
        receiptDialog?.let { (bill, payment) ->
        val linkedStudent = data?.students?.firstOrNull { it.id == bill.text("studentId") }
        val studentPhone = linkedStudent?.text("guardianPhone")?.ifBlank { linkedStudent?.text("phone") }.orEmpty()
            .ifBlank { bill.text("guardianPhone") }
            .ifBlank { bill.text("phone") }
        var phoneInput by rememberSaveable(bill.id) { mutableStateOf(studentPhone) }

        val sName = data?.schoolName?.ifBlank { currentSchoolName } ?: currentSchoolName
        val amt = feeMoney(FeePolicy.amount(payment, "amount"))
        val dt = payment.feeText("paymentDate")
        val rcpt = payment.feeText("receiptNo")
        val cleanRcpt = pk.edunexa.app.fees.FeeReference.display(rcpt)
        val bal = feeMoney(FeePolicy.balance(bill.raw))
        val msg = pk.edunexa.app.notifications.DirectMessageHelper.feeReceiptText(
            schoolName = sName,
            studentName = bill.text("studentName").ifBlank { bill.text("fullName") },
            rollNo = bill.text("rollNo"),
            amount = amt,
            date = dt,
            voucherNo = cleanRcpt,
            balance = bal
        )
        AlertDialog(
            onDismissRequest = { receiptDialog = null },
            title = { Text("Fee Payment Received ✔", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bill.text("studentName") + " • Roll " + bill.text("rollNo"), fontWeight = FontWeight.Bold)
                    Text("Amount Received: $amt", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Receipt #: $cleanRcpt • Date: $dt", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    if (bal.isNotBlank() && bal != "0") Text("Remaining Balance: $bal", color = MaterialTheme.colorScheme.secondary)

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Guardian Phone (WhatsApp / SMS)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Enter 03xxxxxxxxx") }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val target = phoneInput.trim()
                                if (target.isNotBlank()) {
                                    pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, target, msg)
                                } else {
                                    pk.edunexa.app.notifications.DirectMessageHelper.shareWhatsAppBroadcast(context, sName, "Fee Receipt", msg)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            WhatsAppIcon(Modifier.size(16.dp), contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                        OutlinedButton(
                            onClick = {
                                val target = phoneInput.trim()
                                if (target.isNotBlank()) {
                                    pk.edunexa.app.notifications.DirectMessageHelper.sendSms(context, target, msg)
                                } else {
                                    pk.edunexa.app.notifications.DirectMessageHelper.shareSmsBroadcast(context, sName, "Fee Receipt", msg)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SMS")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { FeePrinter.print(context, sName, bill, payment, session.schoolId) }) {
                    Text("Print PDF")
                }
            },
            dismissButton = {
                TextButton(onClick = { receiptDialog = null }) {
                    Text("Done")
                }
            }
        )
    }
    history?.let { record -> AlertDialog(onDismissRequest = { history = null }, title = { Text("Payment History") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(record.text("studentName"), fontWeight = FontWeight.Bold)
            val payments = DiaryPolicy.maps(record.raw["paymentHistory"])
            if(payments.isEmpty()) Text("No detailed payment history available. Ledger paid total: ${feeMoney(FeePolicy.paid(record.raw))}.")
            payments.asReversed().forEach { payment ->
                Text("${payment.feeText("paymentDate")} • ${feeMoney(FeePolicy.amount(payment,"amount"))}", fontWeight = FontWeight.Bold)
                Text("${payment.feeText("receiptNo")} • ${payment.feeText("paymentMethod")}")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { FeePrinter.print(context, data?.schoolName.orEmpty(), record, payment, session.schoolId) }) {
                        Text("Print PDF Receipt")
                    }
                    val guardianPhone = record.text("guardianPhone").ifBlank { record.text("phone") }
                    if (guardianPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val sName = data?.schoolName?.ifBlank { currentSchoolName } ?: currentSchoolName
                                val msg = pk.edunexa.app.notifications.DirectMessageHelper.feeReceiptText(
                                    schoolName = sName,
                                    studentName = record.text("studentName").ifBlank { record.text("fullName") },
                                    rollNo = record.text("rollNo"),
                                    amount = feeMoney(FeePolicy.amount(payment, "amount")),
                                    date = payment.feeText("paymentDate"),
                                    voucherNo = payment.feeText("receiptNo"),
                                    balance = feeMoney(FeePolicy.balance(record.raw))
                                )
                                pk.edunexa.app.notifications.DirectMessageHelper.sendWhatsApp(context, guardianPhone, msg)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            WhatsAppIcon(Modifier.size(20.dp), contentDescription = "WhatsApp Receipt")
                        }
                        IconButton(
                            onClick = {
                                val sName = data?.schoolName?.ifBlank { currentSchoolName } ?: currentSchoolName
                                val msg = pk.edunexa.app.notifications.DirectMessageHelper.feeReceiptText(
                                    schoolName = sName,
                                    studentName = record.text("studentName").ifBlank { record.text("fullName") },
                                    rollNo = record.text("rollNo"),
                                    amount = feeMoney(FeePolicy.amount(payment, "amount")),
                                    date = payment.feeText("paymentDate"),
                                    voucherNo = payment.feeText("receiptNo"),
                                    balance = feeMoney(FeePolicy.balance(record.raw))
                                )
                                pk.edunexa.app.notifications.DirectMessageHelper.sendSms(context, guardianPhone, msg)
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Message, contentDescription = "SMS Receipt", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }, confirmButton = { TextButton(onClick = { history = null }) { Text("Close") } }) }
}

@Composable
private fun FeeReceiveForm(session:PortalSession,record: DiaryItem, busy: Boolean, error: String?, onBack: () -> Unit, onSave: (DiaryItem, Double, String, String, String) -> Unit) {
    val context=LocalContext.current
    val store=remember(session.uid,session.schoolId,record.id){pk.edunexa.app.workflow.LocalDraftStore(context,session.uid,session.schoolId,"payment",record.id)}
    val recovered=remember(record.id){store.load()}
    var amount by rememberSaveable(record.id) { mutableStateOf(recovered["amount"] ?: FeePolicy.balance(record.raw).toString()) }
    var method by rememberSaveable(record.id) { mutableStateOf(recovered["method"] ?: "Cash (Bursar Counter)") }
    var notes by rememberSaveable(record.id) { mutableStateOf(recovered["notes"].orEmpty()) }
    val requestId=rememberSaveable(record.id){recovered["requestId"] ?: UUID.randomUUID().toString()}
    var journalError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(amount,method,notes,requestId){kotlinx.coroutines.delay(300);runCatching {store.save(mapOf("amount" to amount,"method" to method,"notes" to notes,"requestId" to requestId))}.onFailure {journalError=it.message}}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("Back") }; Text("Receive Payment", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        FeeCard { Text(record.text("studentName"), fontWeight = FontWeight.Bold); Text("${record.text("feeType")} • ${record.text("month")}"); Text("Voucher: ${pk.edunexa.app.fees.FeeReference.display(record.text("voucherNo"))}"); Text("Outstanding: ${feeMoney(FeePolicy.balance(record.raw))}", color = androidx.compose.material3.MaterialTheme.colorScheme.secondary) }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Amount Received (PKR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        FeeSelect("Payment Method", method, listOf("Cash (Bursar Counter)", "Bank of Punjab Challan", "Habib Bank Online", "EasyPaisa / JazzCash").map { it to it }, !busy) { method = it }
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text("Reference / Notes") })
        Text("Receipt number is generated only after payment is saved.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        journalError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(recovered.isNotEmpty()) Text("Recovered payment request. Retry uses the same request ID to prevent duplicates.",style=MaterialTheme.typography.bodySmall)
        val parsed = amount.toDoubleOrNull(); val valid = parsed != null && runCatching { FeePolicy.validatePayment(record.raw, parsed) }.isSuccess
        Button(onClick = { runCatching { store.save(mapOf("amount" to amount,"method" to method,"notes" to notes,"requestId" to requestId));onSave(record, parsed!!, method, notes, requestId) }.onFailure {journalError=it.message} }, enabled = !busy && valid, modifier = Modifier.fillMaxWidth()) { Text(if(busy) "Saving…" else "Confirm & Receive") }
        OutlinedButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}
@Composable private fun FeeCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE2E8F0)), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content) } }
@Composable private fun FeeSelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    PortalSelect(label, value, options, enabled, onSelect)
}


