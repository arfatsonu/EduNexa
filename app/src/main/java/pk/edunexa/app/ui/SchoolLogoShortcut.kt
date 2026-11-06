package pk.edunexa.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Base64
import pk.edunexa.app.MainActivity

object SchoolLogoShortcut {
    private fun id(schoolId: String) = "school-" + java.security.MessageDigest.getInstance("SHA-256")
        .digest(schoolId.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun supported(context: Context) = Build.VERSION.SDK_INT >= 26 &&
        context.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    private fun shortcut(context: Context, schoolId: String, name: String, logo: String): ShortcutInfo {
        require(Build.VERSION.SDK_INT >= 26 && schoolId.isNotBlank() && logo.startsWith("data:image/png;base64,")) { "Save a school logo first." }
        val bytes = Base64.decode(logo.substringAfter("base64,"), Base64.DEFAULT)
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("School logo could not be opened.")
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val scale = 124f / maxOf(source.width, source.height)
        val width = source.width * scale; val height = source.height * scale
        canvas.drawBitmap(source, null, RectF((192-width)/2, (192-height)/2, (192+width)/2, (192+height)/2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        val label = name.trim().ifBlank { "School Portal" }.take(80)
        val builder = ShortcutInfo.Builder(context, id(schoolId))
            .setShortLabel(label.take(30)).setLongLabel(label)
            .setIcon(Icon.createWithAdaptiveBitmap(bitmap))
            .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component?.let { builder.setActivity(it) }
        return builder.build()
    }

    fun request(context: Context, schoolId: String, name: String, logo: String): Boolean {
        check(supported(context)) { "This phone's launcher does not support adding a school logo icon." }
        val manager = context.getSystemService(ShortcutManager::class.java)
        val entry = shortcut(context, schoolId, name, logo)
        if(manager.pinnedShortcuts.any { it.id == entry.id }) return manager.updateShortcuts(listOf(entry))
        return manager.requestPinShortcut(entry, null)
    }

    fun refresh(context: Context, schoolId: String, name: String, logo: String) {
        if(Build.VERSION.SDK_INT < 26 || logo.isBlank()) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        if(manager.pinnedShortcuts.any { it.id == id(schoolId) }) manager.updateShortcuts(listOf(shortcut(context, schoolId, name, logo)))
    }
}
