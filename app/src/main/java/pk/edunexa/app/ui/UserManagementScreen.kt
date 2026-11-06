package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.users.*

@Composable
fun UserManagementScreen(session: PortalSession, onOpenModule: (String) -> Unit, modifier: Modifier = Modifier) {
    val repo = remember(session.uid) { UserManagementRepository(session) }; val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ManagedAccount>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var retry by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }; var message by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }
    AutoDismissSuccess(message) { message = null }
    var role by rememberSaveable { mutableStateOf("All") }; var access by rememberSaveable { mutableStateOf("") }; var query by rememberSaveable { mutableStateOf("") }
    var target by remember { mutableStateOf<Pair<ManagedAccount,Boolean>?>(null) }
    LaunchedEffect(session.uid,retry) { loading = true; runCatching { repo.load() }.onSuccess { rows = it; error = null }.onFailure { if(it is CancellationException) throw it; error = it.message ?: "Accounts load failed." }; loading = false }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) { Column(Modifier.weight(1f)) { Text("User Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Linked logins & portal access", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)) }; TextButton(onClick = { retry += 1 }, enabled = !busy && !loading) { Text("Refresh") } }
        PortalTabs(listOf("All", "Teachers", "Students", "Admin"),role,!busy) { role = it }
        error?.let { PortalNotice(it,true) }; message?.let { PortalNotice(it) }
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        val filtered = rows.filter { (role == "All" || it.role == when(role) { "Teachers" -> "teacher"; "Students" -> "student"; else -> "admin" }) && (access.isBlank() || (if(access == "Portal Disabled") it.disabled || (it.manageable && UserAccessPolicy.eligible(it.role,it.profileStatus) && !it.active) else it.status == access)) && listOf(it.name,it.idNumber,it.email,it.detail).any { text -> text.contains(query.trim(),true) } }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AccountCard {
                Text("${rows.size} school records", fontWeight = FontWeight.Bold)
                Text("Active ${rows.count { it.status == "Portal Active" }} • Disabled ${rows.count { it.disabled || (it.manageable && UserAccessPolicy.eligible(it.role,it.profileStatus) && !it.active) }} • Missing ${rows.count { it.status == "Missing Login" }}",style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search name, ID, email or class")},shape=RoundedCornerShape(12.dp))
                PortalSelect("Access Status",access,listOf("" to "All Access") + listOf("Portal Active","Portal Disabled","Record Inactive","Missing Login","Link needs review","Admin Active","Admin Inactive").map { it to it },!busy){access=it}
            } }
            if(!loading && error == null && filtered.isEmpty()) item { PortalEmptyState(Icons.Outlined.AdminPanelSettings,"No accounts in this selection","Change filters or search to view actual school login links.") }
            items(filtered,key={it.key}) { account -> AccountCard {
                Text(account.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                PortalStatusBadge(account.status)
                Text("${account.role.uppercase()} • ${account.idNumber}",style=MaterialTheme.typography.bodySmall)
                Text(account.email.ifBlank { "No saved portal email" },style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B))
                Text(account.detail,style=MaterialTheme.typography.bodySmall)
                Text("Academic status: ${account.profileStatus}",style=MaterialTheme.typography.bodySmall)
                account.uid?.let { Text("Login linked",style=MaterialTheme.typography.labelSmall,color=Color(0xFF64748B)) }
                if(account.role == "admin") Text("Admin account access is protected.",style=MaterialTheme.typography.bodySmall)
                else if(account.manageable) {
                    val disable = !account.disabled && (account.active || !UserAccessPolicy.eligible(account.role,account.profileStatus))
                    val label = if(disable) "Disable Portal Login" else if(UserAccessPolicy.eligible(account.role,account.profileStatus)) "Enable Portal Login" else "Remove Portal Restriction"
                    OutlinedButton(onClick={target=account to disable;error=null},enabled=!busy&&!loading&&error==null,modifier=Modifier.fillMaxWidth()) { Text(label) }
                    if(!UserAccessPolicy.eligible(account.role,account.profileStatus)) Text("Academic status still prevents login. Update the record in its module to reactivate it.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B))
                } else Text(if(account.broken) "Login link needs review before access can be changed." else "No Firestore login link is saved for this profile.",style=MaterialTheme.typography.bodySmall,color=Color(0xFF64748B))
                if(account.role in setOf("teacher","student")) TextButton(onClick={onOpenModule(if(account.role=="teacher") "Teachers" else "Students")},enabled=!busy) { Text(if(account.role=="teacher") "Open Teachers" else "Open Students") }
            } }
        }
    }
    target?.let { (account,disable) -> AlertDialog(onDismissRequest={if(!busy)target=null},title={Text(if(disable) "Disable Portal Login?" else "Enable Portal Access?")},text={Column { Text(account.name,fontWeight=FontWeight.Bold);Text(account.email);Text(if(disable) "This login will be restricted from school portal access, including after academic reactivation. Academic records, fees, attendance and results stay saved." else if(UserAccessPolicy.eligible(account.role,account.profileStatus)) "Restore this linked login's portal access." else "Remove the portal restriction. The inactive academic record will continue to prevent login.");error?.let { Text(it,color=MaterialTheme.colorScheme.error) } }},confirmButton={TextButton(onClick={busy=true;error=null;scope.launch { runCatching { repo.setDisabled(account,disable) }.onSuccess { busy=false;target=null;message=if(disable) "Portal login disabled." else "Portal restriction removed.";retry+=1 }.onFailure { if(it is CancellationException)throw it;busy=false;error=it.message ?: "Access update failed." } }},enabled=!busy){Text(if(busy)"Saving…" else "Confirm")}},dismissButton={TextButton(onClick={target=null},enabled=!busy){Text("Cancel")}}) }
}
@Composable private fun AccountCard(content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,Color(0xFFE2E8F0)),colors=CardDefaults.cardColors(containerColor=Color.White)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content) } }

