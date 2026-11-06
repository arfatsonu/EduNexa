package pk.edunexa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import pk.edunexa.app.attendance.CardAttendancePolicy
import pk.edunexa.app.attendance.CardAttendanceRepository
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.students.StudentDirectoryItem
import java.io.ByteArrayOutputStream

@Composable
fun CardAttendanceScanner(session: PortalSession, enabled: Boolean = true, onSaved: () -> Unit, onBusy: (Boolean) -> Unit = {}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val repo=remember(session.uid,session.schoolId,session.role,session.profileId) { CardAttendanceRepository(session) }
    var busy by remember(session.uid) { mutableStateOf(false) }
    var result by remember(session.uid) { mutableStateOf<String?>(null) }
    var error by remember(session.uid) { mutableStateOf<String?>(null) }
    var multiple by remember(session.uid) { mutableStateOf(false) }
    var scanned by remember(session.uid) { mutableIntStateOf(0) }
    val savedCallback by rememberUpdatedState(onSaved)
    LaunchedEffect(busy) { onBusy(busy) }
    DisposableEffect(Unit) { onDispose { onBusy(false) } }
    fun start(batch: Boolean) {
        if(busy) return
        busy=true;multiple=batch;scanned=0;result=null;error=null
        scope.launch {
            try {
                do {
                    // Back/cancel returns null and ends the batch without any write.
                    val value=scanStudentQr(context) ?: break
                    currentCoroutineContext().ensureActive()
                    val saved=repo.scan(value)
                    scanned++
                    val message=if(saved.alreadyMarked) "${saved.name} (${saved.admissionNo}) — already marked ${saved.status} for ${saved.date}." else "${saved.name} (${saved.admissionNo}) — ${saved.status} saved for ${saved.date}."
                    result=message
                    savedCallback()
                    if(batch) {
                        Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
                        // Let the result be seen and the previous scan activity finish.
                        delay(1800)
                    }
                } while(batch && multiple)
            } catch(e: Exception) {
                if(e is CancellationException) throw e
                error=e.message ?: "Could not save attendance. Scan again to verify."
                // Pause on failures so they cannot be hidden behind the next scan.
            } finally { busy=false;multiple=false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick={ start(false) },enabled=enabled && !busy,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.QrCodeScanner,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(busy) "Scanning / saving…" else "Scan Card · Auto Attendance")
        }
        if(busy && multiple) {
            OutlinedButton(onClick={ multiple=false },modifier=Modifier.fillMaxWidth()) { Text("Stop multiple scanning") }
            Text("$scanned scans processed. Remove this card before the next scan. Back in the scanner stops scanning.",style=MaterialTheme.typography.bodySmall)
        } else {
            OutlinedButton(onClick={ start(true) },enabled=enabled && !busy,modifier=Modifier.fillMaxWidth()) { Text("Scan Multiple Cards") }
        }
        Text("QR scan saves arrival time and attendance automatically. School Late rules apply; already marked records stay unchanged.",style=MaterialTheme.typography.bodySmall)
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        result?.let { PortalNotice(it) };error?.let { PortalNotice(it,true) }
    }
}

private fun cardBitmap(schoolId: String, studentId: String): Bitmap {
    val matrix=QRCodeWriter().encode(CardAttendancePolicy.encode(schoolId,studentId),BarcodeFormat.QR_CODE,640,640,mapOf(EncodeHintType.MARGIN to 4))
    val pixels=IntArray(640*640) { index -> if(matrix[index%640,index/640]) AndroidColor.BLACK else AndroidColor.WHITE }
    return Bitmap.createBitmap(pixels,640,640,Bitmap.Config.ARGB_8888)
}

@Composable
fun AttendanceCardDialog(schoolId: String, schoolName: String, student: StudentDirectoryItem, onClose: () -> Unit) {
    val context=LocalContext.current
    val generated=remember(schoolId,student.id) { runCatching { cardBitmap(schoolId,student.id) } }
    val bitmap=generated.getOrNull()
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest=onClose,title={ Text("Student Attendance Card") },text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(schoolName,fontWeight=FontWeight.Bold)
            Text(student.fullName,style=MaterialTheme.typography.titleMedium)
            Text("Reg. ${student.admissionNo} • ${student.className} ${student.section}")
            bitmap?.let { Image(it.asImageBitmap(),"Student attendance QR",Modifier.fillMaxWidth().height(240.dp)) }
            Text("Scan this card on the school's staff phone to mark attendance.",style=MaterialTheme.typography.bodySmall)
            (error ?: generated.exceptionOrNull()?.message)?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={ TextButton(enabled=bitmap != null,onClick={
        runCatching { printAttendanceCard(context,bitmap!!,schoolName,student) }.onFailure { error=it.message ?: "Could not open printing." }
    }) { Text("Print / Save PDF") } },dismissButton={ TextButton(onClick=onClose) { Text("Close") } })
}

private fun printAttendanceCard(context: Context, bitmap: Bitmap, schoolName: String, student: StudentDirectoryItem) {
    val png=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
    val qr="data:image/png;base64,"+Base64.encodeToString(png,Base64.NO_WRAP)
    fun esc(v: String)=v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    val html="""<!doctype html><html><head><meta charset="UTF-8"><style>
        @page { margin: 12mm; } body { font-family:sans-serif;color:#0A0E17; }
        .card { width:85.6mm;box-sizing:border-box;border:1px solid #cbd5e1;border-top:5px solid #E58A13;border-radius:10px;padding:6mm;text-align:center;break-inside:avoid; }
        h1{font-size:13pt;margin:0 0 3mm;}h2{font-size:14pt;margin:2mm 0;}p{font-size:10pt;margin:2mm 0;}img{width:45mm;height:45mm;image-rendering:pixelated;}small{font-size:8pt;}
        </style></head><body><div class="card"><h1>${esc(schoolName)}</h1><small>STUDENT ATTENDANCE CARD</small><h2>${esc(student.fullName)}</h2><p>Registration: ${esc(student.admissionNo)}</p><p>${esc(student.className)} · ${esc(student.section)} · Roll ${esc(student.rollNo)}</p><img src="$qr"><p><small>Scan at school to mark attendance</small></p></div></body></html>"""
    val view=WebView(context)
    view.settings.javaScriptEnabled=false
    view.webViewClient=object:WebViewClient() {
        private var printed=false
        override fun onPageFinished(webView: WebView,url: String?) {
            if(printed) return
            printed=true
            val manager=context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print("Student-Card-${student.admissionNo}",webView.createPrintDocumentAdapter("Student Attendance Card"),PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    view.loadDataWithBaseURL(null,html,"text/html","UTF-8",null)
}
