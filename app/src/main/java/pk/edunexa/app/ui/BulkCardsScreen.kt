package pk.edunexa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.diary.DiaryItem
import pk.edunexa.app.diary.DiaryPolicy
import pk.edunexa.app.operations.SchoolOperationsRepository
import java.io.ByteArrayOutputStream

@Composable
fun BulkCardsScreen(session: PortalSession,schoolName: String,modifier: Modifier=Modifier) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val repo=remember(session.uid,session.schoolId) { SchoolOperationsRepository(session) }
    var data by remember { mutableStateOf<List<DiaryItem>>(emptyList()) };var config by remember { mutableStateOf<Map<String,Any?>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) };var retry by remember { mutableIntStateOf(0) }
    var academic by remember { mutableStateOf("") };var cls by remember { mutableStateOf("") };var sec by remember { mutableStateOf("") };var kind by remember { mutableStateOf("QR cards") }
    LaunchedEffect(session.uid,session.schoolId,retry) { loading=true;error=null;try { val loaded=repo.students();data=loaded.first;config=loaded.second;if(academic.isBlank()) academic=pk.edunexa.app.students.EnrollmentPolicy.currentSession(config)["id"]?.toString().orEmpty() } catch(e:Exception) { if(e is CancellationException)throw e;error=e.message } finally { loading=false } }
    val selectedSession=DiaryPolicy.maps(config["sessions"]).firstOrNull { it["id"]==academic }
    val aliases=setOf(academic,selectedSession?.get("name")?.toString().orEmpty()).filter { it.isNotBlank() }
    val rows=data.filter { it.text("status") in setOf("Active","On Leave") && it.text("academicSessionId").ifBlank { it.text("academicSession") } in aliases && (cls.isBlank()||it.text("classId")==cls) && (sec.isBlank()||it.text("sectionId")==sec) }.sortedWith(compareBy({it.text("className")},{it.text("section")},{it.text("rollNo").toIntOrNull()?:Int.MAX_VALUE},{it.text("fullName")}))
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Cards & Login Slips",style=MaterialTheme.typography.titleLarge)
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { PortalNotice(it,true);TextButton(onClick={retry++}){Text("Reload") } }
        if(!loading && error==null) {
            PortalSelect("Academic Session",academic,DiaryPolicy.maps(config["sessions"]).map { it["id"].toString() to it["name"].toString() },!busy) { academic=it }
            PortalSelect("Class",cls,listOf("" to "All classes")+DiaryPolicy.maps(config["classes"]).map { it["id"].toString() to it["name"].toString() },!busy) {cls=it;sec=""}
            PortalSelect("Section",sec,listOf("" to "All sections")+DiaryPolicy.maps(config["sections"]).filter { it["classId"]==cls }.map {it["id"].toString() to it["name"].toString()},!busy){sec=it}
            PortalTabs(listOf("QR cards","Login slips"),kind,!busy){kind=it}
            Text("${rows.size} active students selected • Print up to 100 per batch.",style=MaterialTheme.typography.bodySmall)
            if(kind=="Login slips") Text("Shows each saved portal email. Existing passwords cannot be retrieved; use the assigned password or an Admin reset. New import passwords are in its Login Report.",style=MaterialTheme.typography.bodySmall)
            Button(onClick={busy=true;scope.launch { try {repo.authorize();val html=withContext(Dispatchers.Default){BulkCardPrinter.html(schoolName,session.schoolId,rows,kind=="QR cards",cachedSchoolLogo(context,session.schoolId))};printOperationsHtml(context,"EduNexa-${kind}",html)}catch(e:Exception){if(e is CancellationException)throw e;error=e.message}finally{busy=false}}},enabled=rows.isNotEmpty()&&rows.size<=100&&!busy,modifier=Modifier.fillMaxWidth()){Text(if(busy)"Preparing…" else "Print / Save PDF")}
            LazyColumn(Modifier.weight(1f)) {items(rows,key={it.id}) {row->ListItem(headlineContent={Text(row.text("fullName"))},supportingContent={Text("${row.text("admissionNo")} • ${row.text("className")} ${row.text("section")}")})}}
        }
    }
}
object BulkCardPrinter {
    private fun esc(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
    internal fun html(schoolName:String,schoolId:String,rows:List<DiaryItem>,cards:Boolean,logo:String=""):String {
        require(rows.size in 1..100)
        val content=rows.joinToString("") { r ->
            val body=if(cards) {
                val matrix=QRCodeWriter().encode(pk.edunexa.app.attendance.CardAttendancePolicy.encode(schoolId,r.id),BarcodeFormat.QR_CODE,320,320,mapOf(EncodeHintType.MARGIN to 4))
                val pixels=IntArray(320*320){i->if(matrix[i%320,i/320])android.graphics.Color.BLACK else android.graphics.Color.WHITE}
                val bitmap=Bitmap.createBitmap(pixels,320,320,Bitmap.Config.ARGB_8888)
                val bytes=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle()
                "<img class='qr' src='data:image/png;base64,${Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP)}'><p>Scan at school to mark attendance</p>"
            } else "<p><b>Student portal login</b></p><p>${esc(r.text("portalEmail").ifBlank { "Login has not been created" })}</p><p>Use your assigned password. Contact the school Admin if a reset is needed.</p>"
            "<article>${if(logo.isNotBlank()) "<img class='logo' src='data:image/png;base64,${esc(logo)}'>" else ""}<h2>${esc(schoolName)}</h2><h3>${esc(r.text("fullName"))}</h3><p>Registration: ${esc(r.text("admissionNo"))} • Roll: ${esc(r.text("rollNo"))}</p><p>${esc(r.text("className"))} • ${esc(r.text("section"))}</p>$body</article>"
        }
        return "<!doctype html><html><head><meta charset='utf-8'><style>@page{size:A4;margin:10mm}body{font:11px Arial;color:#0a0e17}.grid{display:flex;flex-wrap:wrap;gap:8mm}article{width:85mm;border:1px solid #cbd5e1;border-top:4px solid #e58a13;border-radius:8px;padding:5mm;box-sizing:border-box;text-align:center;break-inside:avoid;overflow-wrap:anywhere}h2{font-size:14px}h3{font-size:16px}.qr{width:40mm;height:40mm}.logo{width:12mm;height:12mm;object-fit:contain}</style></head><body><div class='grid'>$content</div></body></html>"
    }
}
internal fun printOperationsHtml(context:Context,title:String,html:String) {
    val web=WebView(context);web.settings.javaScriptEnabled=false
    web.webViewClient=object:WebViewClient(){var opened=false;override fun onPageFinished(view:WebView,url:String?){if(opened)return;opened=true;(context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(title,view.createPrintDocumentAdapter(title),PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())}}
    web.loadDataWithBaseURL(null,html,"text/html","UTF-8",null)
}
