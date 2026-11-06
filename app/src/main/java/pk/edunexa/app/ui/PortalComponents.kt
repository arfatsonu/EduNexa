package pk.edunexa.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val PortalInk: Color @androidx.compose.runtime.Composable get() = androidx.compose.material3.MaterialTheme.colorScheme.primary
private val PortalMuted = Color(0xFF64748B)

@Composable
internal fun PortalTabs(options: List<String>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { name ->
                Surface(onClick = { onSelect(name) }, enabled = enabled, shape = RoundedCornerShape(10.dp),
                    color = if(name == selected) PortalInk else Color.Transparent) {
                    Text(name, Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                        color = if(name == selected) Color.White else PortalMuted,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun PortalSelect(label: String, value: String, options: List<Pair<String, String>>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Surface(onClick = { expanded = true }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            shape = RoundedCornerShape(12.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelSmall, color = PortalMuted)
                    Text(options.firstOrNull { it.first == value }?.second ?: "Select $label", style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, color = if(enabled) PortalInk else PortalMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ExpandMore, null, tint = PortalMuted)
            }
        }
        DropdownMenu(expanded && enabled, { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            options.forEach { (id, name) -> DropdownMenuItem(text = { Text(name, style = MaterialTheme.typography.bodyMedium) }, onClick = { expanded = false; onSelect(id) }) }
        }
    }
}

@Composable
internal fun PortalEmptyState(icon: ImageVector, title: String, description: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE2E8F0))) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, null, Modifier.padding(12.dp).size(24.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary) }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PortalMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun PortalStatusBadge(value: String) {
    val color = when(value) { "Present", "Active", "Completed", "Paid" -> Color(0xFF15803D); "Absent", "Inactive", "Failed", "Overdue" -> Color(0xFFB91C1C); "Late", "Leave", "Pending", "Partial", "Unpaid" -> Color(0xFFB45309); else -> PortalMuted }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.08f)) { Text(value, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color) }
}

@Composable
internal fun PortalNotice(message: String, failure: Boolean = false) {
    val color = if(failure) Color(0xFFB91C1C) else Color(0xFF15803D)
    Surface(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.08f)) {
        Text(message, Modifier.padding(12.dp), color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PortalSummaryCard(title: String, subtitle: String, first: Pair<String, String>, second: Pair<String, String>) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = PortalInk) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("$title • $subtitle", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(first, second).forEachIndexed { index, item -> Column(Modifier.weight(1f)) {
                    Text(item.first, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.labelMedium)
                    Text(item.second, color = if(index == 0) Color.White else Color(0xFFFFCD87), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } }
            }
        }
    }
}


/** Clear transient success feedback without dismissing persistent error messages. */
@Composable
internal fun AutoDismissSuccess(message: String?, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(message) {
        if(!message.isNullOrBlank()) {
            kotlinx.coroutines.delay(3500)
            dismiss()
        }
    }
}


/** Preserve the original WhatsApp colour rather than applying the app theme tint. */
@Composable
internal fun WhatsAppIcon(modifier: Modifier = Modifier, contentDescription: String? = "WhatsApp") {
    Icon(androidx.compose.ui.res.painterResource(pk.edunexa.app.R.drawable.ic_whatsapp),
        contentDescription, modifier, tint = Color.Unspecified)
}

/** In-app preview for attached images and data URIs without requiring external apps. */
@Composable
internal fun AttachmentPreviewDialog(attachment: Map<String, Any?>, onDismiss: () -> Unit) {
    val name = attachment["name"]?.toString() ?: "Attachment"
    val url = attachment["url"]?.toString().orEmpty()
    val bitmap = remember(url) {
        if (url.startsWith("data:image")) {
            runCatching {
                val bytes = android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                } else {
                    Text("Attachment: $name", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
