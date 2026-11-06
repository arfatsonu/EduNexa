package pk.edunexa.app.diagnostics

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import pk.edunexa.app.BuildConfig

/** Local test-build diagnostics. Nothing is uploaded or sent automatically. */
object CrashDiagnostics {
    private var installed = false
    private const val KEY = "last_crash"
    @Synchronized fun install(context: Context) {
        if (!BuildConfig.DEBUG || installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                app.getSharedPreferences("test_diagnostics", Context.MODE_PRIVATE).edit()
                    .putString(KEY, CrashReport.format(error, BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT))
                    .commit()
            } catch (_: Throwable) {
                // The original exception must still reach Android's crash handler.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }
    }

    fun showPreviousCrash(activity: Activity, onContinue: () -> Unit): Boolean {
        if (!BuildConfig.DEBUG) return false
        val prefs = activity.getSharedPreferences("test_diagnostics", Context.MODE_PRIVATE)
        val report = prefs.getString(KEY, null) ?: return false
        val text = TextView(activity).apply {
            textSize = 12f
            setPadding(32, 16, 32, 16)
            setTextIsSelectable(true)
            this.text = report
        }
        AlertDialog.Builder(activity)
            .setTitle("Previous app error")
            .setMessage("Copy this report and share it to help fix the closing problem.")
            .setView(ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Copy report & continue") { _, _ ->
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("EduNexa error report", report))
            }
            .setNegativeButton("Continue", null)
            .setOnDismissListener {
                prefs.edit().remove(KEY).apply()
                onContinue()
            }
            .show()
        return true
    }
}
