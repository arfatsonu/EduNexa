package pk.edunexa.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import pk.edunexa.app.auth.GeneratedLogin

@Composable
fun GeneratedLoginDialog(login: GeneratedLogin, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(onDismissRequest = {}, title = { Text("Login created") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Email: ${login.email}")
            Text("Temporary password: ${login.password}")
            if (login.email.endsWith("@teacher.a2campus.app") || login.email.substringBefore("@").contains(".t")) {
                Text("Select Teacher on the login screen. The password is case-sensitive.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(login.email)) }) { Text("Copy Email") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(login.password)) }) { Text("Copy Password") }
            }
            Text("Copy these details before closing. The password is shown only now and is never stored in the profile.")
        }
    }, confirmButton = { TextButton(onClick = onDone) { Text("Done") } }, dismissButton = {
        TextButton(onClick = { clipboard.setText(AnnotatedString("Email: ${login.email}\nPassword: ${login.password}")) }) { Text("Copy login") }
    })
}

