package io.github.nomadbeni.gamestv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale

/**
 * Csak debug buildben: kapcsolható átfedés (bal kar gomb, L3), amely a beérkező
 * billentyűkódokat és tengelyértékeket mutatja. Ezzel kalibrálható a keymap.json a valódi kontrolleren.
 */
class DebugOverlay(context: Context) {
    val view = TextView(context).apply {
        setBackgroundColor(Color.argb(200, 0, 0, 0))
        setTextColor(Color.WHITE)
        typeface = Typeface.MONOSPACE
        textSize = 11f
        setPadding(18, 12, 18, 12)
        visibility = View.GONE
        isFocusable = false
    }
    private val lines = ArrayDeque<String>()
    private var axesLine = "tengelyek: —"
    private var lastAxesLog = 0L

    val isShown: Boolean get() = view.visibility == View.VISIBLE

    fun layoutParams() = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
        topMargin = 16; rightMargin = 16
    }

    fun toggle() = show(!isShown)

    fun show(on: Boolean) {
        view.visibility = if (on) View.VISIBLE else View.GONE
        render()
    }

    fun log(text: String) {
        Log.d(TAG, text)
        lines.addFirst(text)
        while (lines.size > MAX_LINES) lines.removeLast()
        render()
    }

    fun logKey(e: KeyEvent, mapped: PadButton?) {
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) return
        val dir = when (e.action) { KeyEvent.ACTION_DOWN -> "▼"; KeyEvent.ACTION_UP -> "▲"; else -> "·" }
        val name = KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")
        val target = mapped?.name ?: "–"
        log("$dir $name (${e.keyCode}) → $target  [${sources(e.source)}] ${e.device?.name ?: "?"}")
    }

    fun logAxes(x: Float, y: Float, hx: Float, hy: Float, rx: Float, ry: Float, lt: Float, rt: Float) {
        axesLine = String.format(
            Locale.ROOT, "X%+.2f Y%+.2f  HAT %+.0f %+.0f  Z%+.2f RZ%+.2f  LT%.2f RT%.2f",
            x, y, hx, hy, rx, ry, lt, rt,
        )
        val now = SystemClock.uptimeMillis()
        if (now - lastAxesLog > 100) { lastAxesLog = now; Log.d(TAG, axesLine) }
        render()
    }

    private fun render() {
        if (!isShown) return
        view.text = buildString {
            append("DEBUG – L3: átfedés ki/be, R3: próbaoldal\n")
            append(axesLine).append('\n')
            lines.forEach { append(it).append('\n') }
        }
    }

    private fun sources(s: Int): String = buildList {
        if (s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) add("gamepad")
        if (s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK) add("joystick")
        if (s and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) add("dpad")
        if (s and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD) add("keyboard")
    }.joinToString("+").ifEmpty { "0x" + Integer.toHexString(s) }

    companion object {
        private const val MAX_LINES = 14
        private const val TAG = "GamesTvInput"
    }
}
