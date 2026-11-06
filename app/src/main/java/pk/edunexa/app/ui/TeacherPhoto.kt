package pk.edunexa.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TeacherPhotoAvatar(value: String?, name: String, size: Dp = 48.dp) {
    val source = value
    val bitmap by produceState<ImageBitmap?>(null, source) {
        this.value = null
        this.value = withContext(Dispatchers.IO) { decodeTeacherPhoto(source) }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, "$name photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Person, "$name photo placeholder", Modifier.size(size * 0.5f), tint = androidx.compose.material3.MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun TeacherPhotoEditor(value: String?, name: String, enabled: Boolean, onChanged: (String?) -> Unit, onProcessing: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val latestChanged by rememberUpdatedState(onChanged)
    val latestProcessing by rememberUpdatedState(onProcessing)
    val crop = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful && result.uriContent != null) {
            processing = true; latestProcessing(true); error = null
            scope.launch {
                try {
                    val encoded = withContext(Dispatchers.IO) { encodeTeacherPhoto(context, result.uriContent!!) }
                    latestChanged(encoded)
                } catch (failure: Exception) {
                    error = "Could not prepare the photo. Try another image."
                } finally { processing = false; latestProcessing(false) }
            }
        } else if (result.error != null) {
            error = "Could not crop the photo. Try again."
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            error = null
            crop.launch(CropImageContractOptions(uri, CropImageOptions(
                guidelines = CropImageView.Guidelines.ON,
                fixAspectRatio = true, aspectRatioX = 1, aspectRatioY = 1,
                outputCompressFormat = Bitmap.CompressFormat.JPEG,
                outputCompressQuality = 90, activityTitle = "Crop Teacher Photo",
                cropMenuCropButtonTitle = "Use Photo"
            )))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Teacher Photo", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TeacherPhotoAvatar(value, name, 84.dp)
            Column {
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = enabled && !processing) {
                    Text(if (value.isNullOrBlank()) "Choose & Crop Photo" else "Change & Crop Photo")
                }
                if (!value.isNullOrBlank()) TextButton(onClick = { latestChanged(null); error = null }, enabled = enabled && !processing) { Text("Remove Photo") }
            }
        }
        if (processing) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(" Preparing photo…", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Square crop • Saved with teacher profile", color = Color(0xFF64748B), style = MaterialTheme.typography.bodySmall)
    }
}

private fun encodeTeacherPhoto(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
    val original = context.contentResolver.openInputStream(uri).use {
        requireNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }))
    }
    var working = original
    try {
        var side = 512
        while (true) {
            val ratio = minOf(1f, side.toFloat() / maxOf(original.width, original.height))
            val scaled = Bitmap.createScaledBitmap(original, (original.width * ratio).toInt().coerceAtLeast(1), (original.height * ratio).toInt().coerceAtLeast(1), true)
            if (working !== original) working.recycle()
            working = scaled
            var quality = 84
            while (quality >= 48) {
                val bytes = ByteArrayOutputStream().use { output ->
                    check(working.compress(Bitmap.CompressFormat.JPEG, quality, output))
                    output.toByteArray()
                }
                if (bytes.size <= 100_000) return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                quality -= 12
            }
            check(side > 256) { "Photo too large" }
            side -= 128
        }
    } finally {
        if (working !== original) working.recycle()
        original.recycle()
    }
}

private fun decodeTeacherPhoto(source: String?): ImageBitmap? = runCatching {
    if (source.isNullOrBlank()) return@runCatching null
    val bytes = if (source.startsWith("data:image") && source.contains("base64,")) {
        Base64.decode(source.substringAfter("base64,"), Base64.DEFAULT)
    } else if (source.startsWith("https://") || source.startsWith("http://")) {
        val connection = URL(source).openConnection().apply { connectTimeout = 8_000; readTimeout = 8_000 }
        connection.getInputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= 1_000_000)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    } else return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()


