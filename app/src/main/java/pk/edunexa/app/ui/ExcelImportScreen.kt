package pk.edunexa.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.imports.*

@Composable
fun ExcelImportScreen(session: PortalSession, onImported: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val repo = remember(session.uid) { ExcelImportRepository(session) }
    var role by remember { mutableStateOf("student") }
    var sheet by remember { mutableStateOf<List<List<String>>?>(null) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var results by remember { mutableStateOf<List<ImportResult>>(emptyList()) }
    var working by remember { mutableStateOf(false) }; var importing by remember { mutableStateOf(false) }
    var stop by remember { mutableStateOf(false) }; var progress by remember { mutableStateOf(0) }; var currentName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExcelWorkbook.MIME)) { uri ->
        val bytes = pendingExport
        if (uri != null && bytes != null) scope.launch {
            working = true
            runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: kotlin.error("Could not open the selected destination.") } }
                .onSuccess { notice = "Excel file saved."; error = null }
                .onFailure { if(it is CancellationException) throw it; error = it.message ?: "Could not save Excel file." }
            pendingExport = null; working = false
        } else pendingExport = null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true; error = null; notice = null; preview = null; results = emptyList(); sheet = null
            runCatching {
                val rows = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { ExcelWorkbook.read(ExcelWorkbook.readLimited(it), role) } ?: kotlin.error("Could not open Excel file.") }
                val checked = repo.preview(role, rows)
                sheet = rows; preview = checked
            }.onFailure { if(it is CancellationException) throw it; error = it.message ?: "Could not read Excel file." }
            working = false
        }
    }
    Column(modifier.fillMaxSize().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Import from Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Download a template, fill it, then upload an .xlsx file. Up to 100 people per file.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("student" to "Students", "teacher" to "Teachers").forEach { (value, label) ->
                FilterChip(selected = role == value, enabled = !working && !importing, onClick = { role = value; preview = null; sheet = null; results = emptyList(); error = null; notice = null }, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch {
                working = true; error = null
                runCatching { repo.template(role) }.onSuccess { pendingExport = it; export.launch("EduNexa-${if(role == "student") "Students" else "Teachers"}-Template.xlsx") }
                    .onFailure { if(it is CancellationException) throw it; error = it.message ?: "Could not create the template." }
                working = false
            } }, enabled = !working && !importing, modifier = Modifier.weight(1f)) { Text("Template") }
            OutlinedButton(onClick = { picker.launch(arrayOf(ExcelWorkbook.MIME)) }, enabled = !working && !importing, modifier = Modifier.weight(1f)) { Text("Upload Excel") }
        }
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        val plan = preview
        if (plan != null) {
            val invalid = plan.rows.count { it.errors.isNotEmpty() }
            Text("${plan.rows.size} rows • $invalid with errors", fontWeight = FontWeight.Bold)
            if (invalid > 0) Text("Correct these rows in Excel and upload the file again.", style = MaterialTheme.typography.bodySmall)
            if (results.isNotEmpty()) {
                Text("${results.count { it.error.isBlank() }} logins verified • ${results.count { it.error.isNotBlank() }} failed", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    val outcomes = results.associateBy { it.row }
                    pendingExport = ExcelWorkbook.write(listOf("Login Report" to (listOf(listOf("ExcelRow", "FullName", "RegistrationOrEmployeeID", "PortalEmail", "TemporaryPassword", "Result")) + plan.rows.map { row ->
                        val result = outcomes[row.number]
                        listOf(row.number.toString(), row.name, result?.identifier.orEmpty(), result?.email.orEmpty(), result?.password.orEmpty(), result?.let { if(it.error.isBlank()) "Imported and login verified" else it.error } ?: "Not imported")
                    })))
                    export.launch("EduNexa-${if(role == "student") "Students" else "Teachers"}-Login-Report.xlsx")
                }, enabled = !working && !importing, modifier = Modifier.fillMaxWidth()) { Text("Download Login Report") }
                Text("Save the report before leaving. It contains login passwords; share it only with the intended recipients.", style = MaterialTheme.typography.bodySmall)
            }
            val outcomes = results.associateBy { it.row }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plan.rows, key = { it.number }) { row ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Row ${row.number} • ${row.name}", fontWeight = FontWeight.SemiBold)
                        Text(if (role == "student") "${row.values["Class"]} • ${row.values["Section"]} • Roll ${row.values["RollNo"]} • Fee ${row.values["MonthlyFee"]} / Fund ${row.values["AnnualFund"]} / Other ${row.values["OtherFunds"]}" else row.values["Phone"].orEmpty(), style = MaterialTheme.typography.bodySmall)
                        row.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        outcomes[row.number]?.let { result -> Text(if(result.error.isBlank()) "${result.identifier} • Login verified" else result.error, color = if(result.error.isBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    } }
                }
            }
            Button(onClick = { scope.launch {
                importing = true; stop = false; progress = 0; error = null; notice = null
                try {
                    // Recheck live duplicates/session immediately before starting. No writes during preview.
                    val fresh = repo.preview(role, sheet!!)
                    preview = fresh
                    check(fresh.rows.all { it.errors.isEmpty() }) { "School records changed. Review the updated errors before importing." }
                    results = emptyList()
                    for (row in fresh.rows) {
                        if (stop) break
                        currentName = row.name
                        val result = try { repo.importRow(fresh, row) } catch (e: Exception) {
                            if(e is CancellationException) throw e
                            ImportResult(row.number, row.name, error = e.message ?: "Import failed. Retry this file.")
                        }
                        results = results + result; progress++
                        if(result.error.isNotBlank()) { error = "Stopped at row ${row.number}. Earlier successful rows are saved. Fix the error and retry the same file."; break }
                    }
                    notice = if(stop) "Stopped after the current row. Successful rows remain saved." else if(results.size == fresh.rows.size && results.all { it.error.isBlank() }) "Import complete. Download the login report." else notice
                    if (results.isNotEmpty()) onImported()
                } catch (e: Exception) { if(e is CancellationException) throw e; error = e.message ?: "Import failed." }
                finally { importing = false }
            } }, enabled = !working && !importing && invalid == 0 && !(results.size == plan.rows.size && results.all { it.error.isBlank() }), modifier = Modifier.fillMaxWidth()) { Text(if(results.isEmpty()) "Import ${plan.rows.size} ${if(role == "student") "Students" else "Teachers"}" else "Retry Import") }
        } else Spacer(Modifier.weight(1f))
    }
    if (importing) AlertDialog(onDismissRequest = {}, title = { Text("Importing ${if(role == "student") "Students" else "Teachers"}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("$progress / ${preview?.rows?.size ?: 0} completed"); Text(currentName); Text("Keep the app open until the import finishes.", style = MaterialTheme.typography.bodySmall) } },
        confirmButton = { TextButton(onClick = { stop = true }, enabled = !stop) { Text(if(stop) "Stopping…" else "Stop after this row") } })
}
