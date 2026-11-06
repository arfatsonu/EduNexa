package pk.edunexa.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun scanStudentQr(context: android.content.Context): String? = suspendCancellableCoroutine { c ->
    fun activity(value: android.content.Context): androidx.activity.ComponentActivity? = when (value) {
        is androidx.activity.ComponentActivity -> value
        is android.content.ContextWrapper -> activity(value.baseContext)
        else -> null
    }
    val host = activity(context)
    if (host == null) { c.resumeWithException(IllegalStateException("Open the scanner from the school app.")); return@suspendCancellableCoroutine }
    lateinit var launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    launcher = host.activityResultRegistry.register("student-qr-${java.util.UUID.randomUUID()}", androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        launcher.unregister()
        if (c.isActive) {
            val error = result.data?.getStringExtra("error")
            if (error != null) c.resumeWithException(IllegalStateException(error))
            else c.resume(if (result.resultCode == android.app.Activity.RESULT_OK) result.data?.getStringExtra("qr") else null)
        }
    }
    c.invokeOnCancellation { host.runOnUiThread { launcher.unregister() } }
    try { launcher.launch(android.content.Intent(host, StudentQrScannerActivity::class.java)) }
    catch (error: Exception) { launcher.unregister(); if (c.isActive) c.resumeWithException(error) }
}
@Composable
internal fun ScanFeeButton(enabled:Boolean,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.QrCodeScanner,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Scan Student Card • Open Fees")
    }
    Text("Opens the student's bills. Review the bill and confirm payment separately.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
