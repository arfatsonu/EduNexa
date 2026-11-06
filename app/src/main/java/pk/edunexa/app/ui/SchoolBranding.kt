package pk.edunexa.app.ui
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pk.edunexa.app.R
import pk.edunexa.app.auth.PortalSession
import pk.edunexa.app.settings.BrandingPolicy
import pk.edunexa.app.settings.SchoolBranding
import java.io.ByteArrayOutputStream

val LocalSchoolBranding = staticCompositionLocalOf { SchoolBranding() }
private fun brandColor(hex: String) = Color(android.graphics.Color.parseColor(hex))
fun schoolColorScheme(brand: SchoolBranding): ColorScheme {
    val primary = brandColor(brand.primary); val accent = brandColor(brand.accent)
    val soft = lerp(Color.White, accent, 0.12f)
    val accentText = if(BrandingPolicy.contrast(brand.accent, "#FFFFFF") >= 4.5) Color.White else Color.Black
    return lightColorScheme(primary = primary, secondary = accent, background = brandColor(brand.background),
        surface = Color.White, onPrimary = Color.White, onSecondary = accentText,
        onBackground = primary, onSurface = primary, primaryContainer = soft,
        onPrimaryContainer = primary, secondaryContainer = soft, onSecondaryContainer = primary,
        surfaceVariant = lerp(Color.White, primary, 0.04f), onSurfaceVariant = Color(0xFF64748B),
        outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0), surfaceTint = Color.Transparent)
}
private fun brandPreferences(context: Context) = context.getSharedPreferences("school_branding", Context.MODE_PRIVATE)
private fun cachedSettings(context: Context, schoolId: String? = null): Map<String, Any?> = runCatching {
    val prefs = brandPreferences(context); val id = schoolId ?: prefs.getString("last_school", "").orEmpty()
    val json = JSONObject(prefs.getString("school_$id", "{}").orEmpty())
    json.keys().asSequence().associateWith { json.opt(it) }
}.getOrDefault(emptyMap())
fun cachedSchoolLogo(context: Context,schoolId:String?=null) = BrandingPolicy.read(cachedSettings(context,schoolId)).logo.substringAfter("base64,", "")

@Composable
fun rememberSchoolBranding(session: PortalSession?): SchoolBranding {
    val context = LocalContext.current.applicationContext
    // Checking, signed-out and Owner sessions never inherit the last school's cache.
    // Keep a stable composition through session restoration instead of returning early.
    val schoolId = pk.edunexa.app.settings.BrandingSessionPolicy.schoolId(
        session?.role?.value, session?.isPlatformOwner == true, session?.schoolId)
    var branding by remember(schoolId) { mutableStateOf(
        if (schoolId == null) SchoolBranding() else BrandingPolicy.read(cachedSettings(context, schoolId))) }
    DisposableEffect(schoolId) {
        val registration = schoolId?.let { id ->
            FirebaseFirestore.getInstance().collection("schools").document(id).addSnapshotListener { document, failure ->
                if(failure == null && document?.exists() == true && !document.metadata.hasPendingWrites()) {
                    @Suppress("UNCHECKED_CAST") val raw = document.get("settings") as? Map<String, Any?> ?: emptyMap()
                    branding = BrandingPolicy.read(raw)
                    val cached = BrandingPolicy.editable(raw) + mapOf("schoolName" to branding.schoolName, "campusName" to branding.campus)
                    brandPreferences(context).edit().putString("last_school", id).putString("school_$id", JSONObject(cached).toString()).apply()
                }
            }
        }
        onDispose { registration?.remove() }
    }
    // Keep school logo shortcut in sync without disruptive launcher component toggles on login
    LaunchedEffect(branding.launcherIcon, branding.logo, branding.schoolName, schoolId) {
        if (schoolId == null) return@LaunchedEffect
        runCatching {
            SchoolLogoShortcut.refresh(context, schoolId, branding.schoolName, branding.logo)
        }
    }
    return branding
}

@Composable
fun SchoolLogo(modifier: Modifier = Modifier, source: String = LocalSchoolBranding.current.logo) {
    val bitmap by produceState<Bitmap?>(null, source) {
        value = null
        value = withContext(Dispatchers.IO) { runCatching {
            if(source.isBlank()) null else {
                val bytes = Base64.decode(source.substringAfter("base64,"), Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while(maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        }.getOrNull() }
    }
    if(bitmap != null) Image(bitmap!!.asImageBitmap(), "School logo", modifier, contentScale = ContentScale.Fit)
    else Image(painterResource(R.drawable.campus_launcher_foreground), "EduNexa logo", modifier, contentScale = ContentScale.Fit)
}

internal fun encodeSchoolLogo(context: Context, uri: android.net.Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Select a valid image." }
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while(maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 1024) inSampleSize *= 2
    }
    val original = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("Image could not be opened.")
    try {
        for(limit in listOf(512, 384, 256, 192, 128)) {
            val ratio = minOf(1f, limit.toFloat() / maxOf(original.width, original.height))
            val resized = Bitmap.createScaledBitmap(original, maxOf(1,(original.width * ratio).toInt()), maxOf(1,(original.height * ratio).toInt()), true)
            val bytes = ByteArrayOutputStream().use { output -> resized.compress(Bitmap.CompressFormat.PNG,100,output); output.toByteArray() }
            if(resized !== original) resized.recycle()
            if(bytes.size <= 80_000) return "data:image/png;base64," + Base64.encodeToString(bytes,Base64.NO_WRAP)
        }
        error("Choose a simpler logo image.")
    } finally { original.recycle() }
}

@Composable
fun BrandingControls(values: Map<String, Any?>, enabled: Boolean, onChange: (Map<String, Any?>) -> Unit, onProcessing: (Boolean) -> Unit, schoolId: String, savedBranding: Map<String, Any?>, canPinLogo: Boolean) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var iconNotice by remember { mutableStateOf<String?>(null) }
    val currentChange by rememberUpdatedState(onChange)
    val currentValues by rememberUpdatedState(values)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri != null) {
            onProcessing(true); error = null
            scope.launch {
                try {
                    val logo = withContext(Dispatchers.IO) { encodeSchoolLogo(context,uri) }
                    currentChange(currentValues + ("schoolLogo" to logo))
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { error = e.message ?: "Logo could not be opened." }
                finally { onProcessing(false) }
            }
        }
    }
    SchoolLogo(Modifier.fillMaxWidth().height(110.dp), values["schoolLogo"]?.toString().orEmpty())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker.launch("image/*") }, enabled = enabled) { Text("Choose Logo") }
        TextButton(onClick = { onChange(values + ("schoolLogo" to "")) }, enabled = enabled) { Text("Original Logo") }
    }
    error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
    Text("Your logo appears across the portal, on reports and on the loading screen.", style = MaterialTheme.typography.bodySmall)
    Text("Portal Colors", style = MaterialTheme.typography.titleMedium)
    BrandingPolicy.presets.forEach { (name, colors) ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(values + mapOf("brandPrimary" to colors[0], "brandAccent" to colors[1], "brandBackground" to colors[2])) },enabled = enabled,modifier = Modifier.weight(1f)) { Text(name) }
            colors.forEach { Box(Modifier.padding(top = 10.dp).size(22.dp).background(brandColor(it))) }
        }
    }
    var activeColorPicker by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    activeColorPicker?.let { (key, label, hex) ->
        ColorPickerDialog(
            title = label,
            initialColorHex = hex,
            onDismiss = { activeColorPicker = null },
            onColorSelected = { newHex ->
                onChange(values + (key to newHex))
                activeColorPicker = null
            }
        )
    }

    listOf("brandPrimary" to "Primary (dark)", "brandAccent" to "Accent", "brandBackground" to "Background (light)").forEach { (key,label) ->
        val currentHex = values[key]?.toString().orEmpty()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = currentHex,
                onValueChange = { onChange(values + (key to it)) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                label = { Text("$label · #RRGGBB") },
                singleLine = true
            )
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(runCatching { brandColor(currentHex) }.getOrDefault(Color.Gray))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) {
                        activeColorPicker = Triple(key, label, currentHex)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Pick Color",
                    tint = if (runCatching { BrandingPolicy.contrast(currentHex, "#FFFFFF") >= 4.5 }.getOrDefault(true)) Color.White else Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    val preview = BrandingPolicy.read(values)
    MaterialTheme(colorScheme = schoolColorScheme(preview)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Color Preview",color = MaterialTheme.colorScheme.primary)
                Button(onClick = {}, enabled = true) { Text("Primary Button") }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer) { Text("Accent",Modifier.padding(8.dp),color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
    }
    Text("Phone App Icon",style = MaterialTheme.typography.titleMedium)
    listOf("default" to "Campus Icon", "navy" to "Navy Campus", "gold" to "Gold Campus").forEach { (key,label) ->
        Row {
            RadioButton(selected = values["launcherIcon"] == key,onClick = { onChange(values + ("launcherIcon" to key)) },enabled = enabled)
            Image(painterResource(when(key) { "navy" -> R.drawable.ic_campus_navy; "gold" -> R.drawable.ic_campus_gold; else -> R.drawable.ic_campus_navy }), label,Modifier.padding(8.dp).size(30.dp))
            Text(label,Modifier.padding(top = 14.dp))
        }
    }
    val savedLogo = savedBranding["schoolLogo"]?.toString().orEmpty()
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SchoolLogo(Modifier.padding(8.dp).size(36.dp), values["schoolLogo"]?.toString().orEmpty())
        Column(Modifier.weight(1f)) {
            Text("Uploaded School Logo")
            Text("Home-screen icon", style = MaterialTheme.typography.bodySmall)
        }
    }
    OutlinedButton(onClick = {
        error = null; iconNotice = null
        runCatching { SchoolLogoShortcut.request(context, schoolId, savedBranding["schoolName"]?.toString().orEmpty(), savedLogo) }
            .onSuccess { accepted -> iconNotice = if(accepted) "Confirm Add on your phone, or check the existing school logo icon." else "Your launcher could not add the school icon." }
            .onFailure { error = it.message ?: "School icon could not be added." }
    }, enabled = canPinLogo && savedLogo.isNotBlank() && SchoolLogoShortcut.supported(context), modifier = Modifier.fillMaxWidth()) {
        Text("Use School Logo on Home Screen")
    }
    iconNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    Text(if(savedLogo.isBlank() || !canPinLogo) "Save your logo and branding first, then use the school logo on your home screen." else "Adds a school-logo shortcut that opens EduNexa. Your phone asks you to confirm Add. The app drawer keeps the selected included icon.", style = MaterialTheme.typography.bodySmall)
    if(!SchoolLogoShortcut.supported(context)) Text("School-logo home-screen icons are unavailable on this launcher.", style = MaterialTheme.typography.bodySmall)
}


@Composable
fun ColorPickerDialog(
    title: String,
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var selectedHex by remember { mutableStateOf(if(initialColorHex.startsWith("#")) initialColorHex.uppercase() else "#012B7C") }
    val palettes = listOf(
        // Dark / Primaries
        listOf("#0A0E17", "#012B7C", "#172554", "#0F172A", "#1E293B", "#334155"),
        // Greens & Teals
        listOf("#064E3B", "#059669", "#10B981", "#134E4A", "#0D9488", "#14B8A6"),
        // Reds, Burgundy & Oranges
        listOf("#4C0519", "#881337", "#BE123C", "#E11D48", "#C2410C", "#EA580C"),
        // Golds, Purples & Accents
        listOf("#E58A13", "#D97706", "#D4AF37", "#3B0764", "#7E22CE", "#9333EA"),
        // Light / Backgrounds
        listOf("#F7F9FC", "#FFFFFF", "#F0FDF4", "#EFF6FF", "#FFF1F2", "#FAF5FF")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose $title", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Live preview banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(runCatching { brandColor(selectedHex) }.getOrDefault(Color.DarkGray))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sample Text",
                        color = if (runCatching { BrandingPolicy.contrast(selectedHex, "#FFFFFF") >= 4.5 }.getOrDefault(true)) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        selectedHex,
                        color = if (runCatching { BrandingPolicy.contrast(selectedHex, "#FFFFFF") >= 4.5 }.getOrDefault(true)) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text("Tap any color to choose:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palettes.forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { hex ->
                                val isSelected = selectedHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(runCatching { brandColor(hex) }.getOrDefault(Color.Gray))
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCBD5E1),
                                            shape = CircleShape
                                        )
                                        .clickable { selectedHex = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = if (runCatching { BrandingPolicy.contrast(hex, "#FFFFFF") >= 4.5 }.getOrDefault(true)) Color.White else Color.Black,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedHex) }) {
                Text("Select Color")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
