package pk.edunexa.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.operations.*
import pk.edunexa.app.settings.SchoolSettingsPolicy

@Composable
fun OwnerSchoolEditor(session: PortalSession, school: DiaryItem, initialTab: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope(); val context = LocalContext.current
    val repo = remember(session.uid) { OwnerRepository(session) }
    @Suppress("UNCHECKED_CAST") val original = school.raw["settings"] as? Map<String, Any?> ?: emptyMap()
    var values by remember(school.id) { mutableStateOf<Map<String, Any?>>(SchoolSettingsPolicy.profileFields.keys.associateWith { original[it] ?: "" } + ("schoolLogo" to (original["schoolLogo"] ?: ""))) }
    var tab by remember { mutableStateOf(initialTab) }; var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var notice by remember { mutableStateOf<String?>(null) }
    var dirty by remember { mutableStateOf(false) }; var discard by remember { mutableStateOf(false) }
    val currentValues by rememberUpdatedState(values)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) { busy = true; error = null; scope.launch {
            try { val logo = withContext(Dispatchers.IO) { encodeSchoolLogo(context, uri) }; values = currentValues + ("schoolLogo" to logo); dirty = true }
            catch(e: Exception) { if(e is CancellationException) throw e; error = e.message ?: "Logo could not be opened." }
            finally { busy = false }
        } }
    }
    fun close() { if(!busy) { if(dirty) discard = true else onClose() } }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
        BackHandler { close() }
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Surface(color = Color(0xFF101C30)) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Manage school", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(school.id, color = Color(0xFFBAC7DC)) }
                    TextButton(onClick = { close() }, enabled = !busy) { Text("Close", color = Color.White) }
                } }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Profile", "Admin login").forEach { label -> FilterChip(selected = tab == label, onClick = { tab = label; error = null; notice = null }, enabled = !busy, label = { Text(label) }) }
                }
                if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                notice?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = Color(0xFF166534)) }
                if(tab == "Profile") Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("School identity", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SchoolLogo(Modifier.size(72.dp), values["schoolLogo"]?.toString() ?: "")
                                Column(Modifier.padding(start = 12.dp)) {
                                    OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy) { Text("Choose logo") }
                                    TextButton(onClick = { values = values + ("schoolLogo" to ""); dirty = true }, enabled = !busy && values["schoolLogo"] != "") { Text("Remove logo") }
                                }
                            }
                            Text("Used in this school's portal, splash screen and documents.", style = MaterialTheme.typography.bodySmall)
                            Text("School ID: ${school.id} • Permanent", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SchoolSettingsPolicy.profileFields.forEach { (key, label) ->
                        OutlinedTextField(values[key]?.toString() ?: "", { values = values + (key to it); dirty = true }, Modifier.fillMaxWidth(), enabled = !busy, label = { Text(label) }, singleLine = key != "address", minLines = if(key == "address") 3 else 1, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = when(key) { "email" -> KeyboardType.Email; "phone", "whatsapp" -> KeyboardType.Phone; else -> KeyboardType.Text }))
                    }
                    Text("Changing the short form affects future generated logins. Existing student and teacher logins stay the same.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { busy = true; error = null; scope.launch {
                        try { repo.saveProfile(school, values); dirty = false; onSaved() }
                        catch(e: Exception) { if(e is CancellationException) throw e; error = ownerEditError(e, "Could not save school profile.") }
                        finally { busy = false }
                    } }, enabled = !busy && dirty, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Save school profile") }
                } else OwnerAdminEditor(session, school.id, busy, { busy = it }, { error = it }, { notice = it }, Modifier.weight(1f))
            }
            if(discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("Discard profile edits?") }, text = { Text("Your unsaved school details will be discarded.") }, confirmButton = { TextButton(onClick = onClose) { Text("Discard") } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } })
        }
    }
}

@Composable
private fun OwnerAdminEditor(session: PortalSession, schoolId: String, busy: Boolean, setBusy: (Boolean) -> Unit, onError: (String?) -> Unit, onNotice: (String?) -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope(); val repo = remember(session.uid) { OwnerRepository(session) }
    val accounts = remember(session.uid) { OwnerAdminAccountRepository(session) }
    var admins by remember { mutableStateOf<List<DiaryItem>>(emptyList()) }; var selected by remember { mutableStateOf<DiaryItem?>(null) }
    var reload by remember { mutableIntStateOf(0) }; var loading by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }; var loginEmail by remember { mutableStateOf("") }; var newEmail by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }; var newPassword by remember { mutableStateOf("") }; var confirmPassword by remember { mutableStateOf("") }
    var operation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(schoolId, reload) {
        loading = true
        try { admins = repo.admins(schoolId); selected = admins.firstOrNull { it.id == selected?.id } ?: admins.firstOrNull(); onError(null) }
        catch(e: Exception) { if(e is CancellationException) throw e; onError(ownerEditError(e, "Could not load Admin accounts.")) }
        finally { loading = false }
    }
    LaunchedEffect(selected) { name = selected?.text("name") ?: ""; loginEmail = selected?.text("email") ?: ""; newEmail = ""; currentPassword = ""; newPassword = ""; confirmPassword = "" }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Admin account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("School profile email and Admin login email are separate. Changes here apply only to the selected Admin.", style = MaterialTheme.typography.bodySmall)
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!loading && admins.isEmpty()) Text("No Admin account could be loaded for this school.")
        OutlinedButton(onClick = { reload++ }, enabled = !busy && !loading) { Text("Reload Admin details") }
        admins.forEach { admin -> FilterChip(selected = selected?.id == admin.id, onClick = { selected = admin }, enabled = !busy && !loading, label = { Text(admin.text("email").ifBlank { admin.text("name") }) }) }
        selected?.let { admin ->
            val enabled = !busy && !loading
            Text(if(admin.raw["active"] == true && admin.raw["portalDisabled"] != true) "Active Admin" else "Admin access disabled", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Admin name") }, singleLine = true)
            OutlinedButton(onClick = { operation = "name" }, enabled = enabled && name.trim().length in 2..150 && name.trim() != admin.text("name"), modifier = Modifier.fillMaxWidth()) { Text("Save Admin name") }
            HorizontalDivider()
            Text("Forgot or mistyped password?", fontWeight = FontWeight.SemiBold)
            Text("Send a reset link to ${admin.text("email")}. The old password is not needed.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { operation = "reset" }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Send password reset email") }
            HorizontalDivider()
            Text("Edit login credentials", fontWeight = FontWeight.SemiBold)
            Text("Verify the Admin's current login first. Your Owner account stays signed in. Passwords are never stored in the school profile.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(loginEmail, { loginEmail = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("Current / verified Admin login email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OwnerPasswordField(currentPassword, { currentPassword = it }, "Current Admin password", enabled)
            OutlinedTextField(newEmail, { newEmail = it }, Modifier.fillMaxWidth(), enabled = enabled, label = { Text("New Admin email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedButton(onClick = { operation = "email" }, enabled = enabled && currentPassword.isNotBlank() && runCatching { OwnerSchoolPolicy.email(newEmail) }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Verify new email address") }
            Text("Open the link in the new inbox to change the login email. Then enter that new email above with the current password and sync it below. Until verified, the old email remains active.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { operation = "sync" }, enabled = enabled && currentPassword.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Verify login & sync saved email") }
            OwnerPasswordField(newPassword, { newPassword = it }, "New password (12+ characters)", enabled)
            OwnerPasswordField(confirmPassword, { confirmPassword = it }, "Confirm new password", enabled)
            Button(onClick = { operation = "password" }, enabled = enabled && currentPassword.isNotBlank() && runCatching { OwnerSchoolPolicy.password(newPassword, confirmPassword) }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text("Update Admin password") }
            Text("If the email is inaccessible and the current password is unknown, recover the account through Firebase Authentication. School records and the Admin UID must be retained.", style = MaterialTheme.typography.bodySmall)
        }
    }
    val admin = selected; val action = operation
    if(admin != null && action != null) AlertDialog(onDismissRequest = { if(!busy) operation = null }, title = { Text(when(action) { "name" -> "Save Admin name?"; "reset" -> "Send reset email?"; "email" -> "Send email verification?"; "sync" -> "Sync verified login email?"; else -> "Change Admin password?" }) }, text = { Text("School: $schoolId\nAdmin: ${admin.text("email")}\n" + when(action) { "email" -> "Verification will be sent to ${newEmail.trim()}."; "password" -> "The Admin will need the new password for future sign-ins."; "sync" -> "Only the email returned by a verified sign-in for this same Admin UID will be saved."; else -> "Confirm this change for the selected school Admin." }) }, confirmButton = { TextButton(onClick = {
        operation = null; setBusy(true); onError(null); onNotice(null)
        scope.launch {
            try {
                when(action) {
                    "name" -> repo.saveAdmin(schoolId, admin, name)
                    "reset" -> accounts.reset(schoolId, admin)
                    "email" -> accounts.requestEmail(schoolId, admin, loginEmail, currentPassword, newEmail)
                    "sync" -> accounts.syncEmail(schoolId, admin, loginEmail, currentPassword)
                    else -> accounts.changePassword(schoolId, admin, loginEmail, currentPassword, newPassword, confirmPassword)
                }
                currentPassword = ""; newPassword = ""; confirmPassword = ""
                onNotice(when(action) { "reset" -> "Reset email requested. Check the Admin inbox and spam folder."; "email" -> "Verification email sent. Complete the link, then verify login & sync saved email."; "password" -> "Admin password updated."; "sync" -> "Verified Admin email saved."; else -> "Admin name saved." })
                if(action in setOf("name", "sync")) reload++
            } catch(e: Exception) { if(e is CancellationException) throw e; onError(ownerEditError(e, "Admin account update could not be completed.")) }
            finally { setBusy(false) }
        }
    }, enabled = !busy) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { operation = null }, enabled = !busy) { Text("Cancel") } })
}

@Composable
private fun OwnerPasswordField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), enabled = enabled, label = { Text(label) }, singleLine = true, visualTransformation = if(visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if(visible) "Hide" else "Show") } })
}

private fun ownerEditError(error: Exception, fallback: String): String =
    if(error is com.google.firebase.firestore.FirebaseFirestoreException && error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
        "Owner edit permission is unavailable. Publish the EduNexa 0.3.6 Firestore rules, then reload this school."
    else error.message ?: fallback
