package ro.fitnesshub.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ro.fitnesshub.BuildConfig
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Small in-memory log shown on the debug Log screen.
 *
 * Rule: log counts and statuses only. Never log health values, meals or AI prompts.
 * Logcat output is only produced in debug builds.
 */
object AppLog {
    private const val MAX_LINES = 200
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun i(tag: String, message: String) = add(Log.INFO, "I", tag, message, null)

    fun w(tag: String, message: String) = add(Log.WARN, "W", tag, message, null)

    fun e(tag: String, message: String, error: Throwable? = null) =
        add(Log.ERROR, "E", tag, message, error)

    fun clear() = _lines.update { emptyList() }

    private fun add(priority: Int, level: String, tag: String, message: String, error: Throwable?) {
        val detail = error?.let { " (${it::class.simpleName}: ${it.message})" }.orEmpty()
        val line = "${LocalTime.now().format(timeFormat)} $level/$tag: $message$detail"
        if (BuildConfig.DEBUG) {
            Log.println(priority, "FitnessHub/$tag", message + detail)
        }
        _lines.update { (it + line).takeLast(MAX_LINES) }
    }
}
