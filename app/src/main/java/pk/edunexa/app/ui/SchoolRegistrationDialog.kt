package pk.edunexa.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.operations.*

@Composable
fun SchoolRegistrationDialog(session:PortalSession,onClose:()->Unit,onCreated:()->Unit) {
    val scope=rememberCoroutineScope();val clipboard=LocalClipboardManager.current
    var id by rememberSaveable{mutableStateOf("")};var name by rememberSaveable{mutableStateOf("")};var campus by rememberSaveable{mutableStateOf("")};var short by rememberSaveable{mutableStateOf("")};var admin by rememberSaveable{mutableStateOf("")};var email by rememberSaveable{mutableStateOf("")}
    var password by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)};var created by remember{mutableStateOf<SchoolRegistration?>(null)}
    val input=SchoolRegistration(id,name,campus,short,admin,email)
    val valid=runCatching{SchoolRegistrationPolicy.validate(input,password)}
    val result=created
    if(result!=null) AlertDialog(onDismissRequest={},title={Text("School registered")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(result.schoolName);Text("School ID: ${result.schoolId}");Text("Admin email: ${result.adminEmail}");Text("Temporary password: $password")
        Text("Save these credentials. The Admin can now sign in and follow School Setup; academic data is empty.")
    }},confirmButton={TextButton(onClick={password="";onCreated()}){Text("Done")}},dismissButton={TextButton(onClick={clipboard.setText(AnnotatedString("${result.schoolName}\nAdmin: ${result.adminEmail}\nTemporary password: $password"))}){Text("Copy login")}})
    else AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Register a school")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Creates a separate school workspace and its Admin login.",style=MaterialTheme.typography.bodySmall)
        listOf(Triple("School ID",id,{v:String->id=v}),Triple("School name",name,{v:String->name=v}),Triple("Campus / city",campus,{v:String->campus=v}),Triple("Short form (ANS / ANPS)",short,{v:String->short=v}),Triple("Admin name",admin,{v:String->admin=v}),Triple("Admin real email",email,{v:String->email=v})).forEach{(label,value,change)->OutlinedTextField(value,change,Modifier.fillMaxWidth(),enabled=!busy,label={Text(label)},singleLine=true)}
        OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),enabled=!busy,label={Text("Admin password (12+ characters)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        Text("Keep the password for recovery. If registration is interrupted, retry the same school ID, email and password; the Owner stays signed in.",style=MaterialTheme.typography.bodySmall)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let{PortalNotice(it,true)}
        if(!busy&&valid.isFailure)Text(valid.exceptionOrNull()?.message.orEmpty(),style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={if(!busy){busy=true;error=null;scope.launch{try{created=SchoolRegistrationRepository(session).register(input,password)}catch(e:Exception){if(e is CancellationException)throw e;error=(e.message ?: "Registration could not be confirmed.")+" Retry the same details; existing accounts and records are retained."}finally{busy=false}}}},enabled=valid.isSuccess&&!busy){Text(if(busy)"Registering…" else "Register school")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}})
}
