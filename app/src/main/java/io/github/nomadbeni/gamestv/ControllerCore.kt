package io.github.nomadbeni.gamestv

import kotlin.math.abs

/** Egy billentyűesemény, amelyet a weboldal kap. */
data class KeyOut(val down: Boolean, val stroke: KeyStroke) {
    val type: String get() = if (down) "keydown" else "keyup"
}

/**
 * A kontroller állapota, Android nélkül (JVM-en tesztelhető).
 *
 * Három forrásból jöhet irány: D-pad billentyű (`KEYCODE_DPAD_*`), D-pad tengely (`AXIS_HAT_X/Y`)
 * és a bal kar (`AXIS_X/Y`). Egy irány akkor lenyomott, ha bármelyik forrás lenyomja; a weboldal
 * irányonként egyetlen `keydown`-t és egyetlen `keyup`-ot kap, eseményzápor nélkül.
 */
class ControllerCore(private val map: KeyMap) {
    private val pressed = BooleanArray(PadButton.COUNT)
    private val dirFromKey = BooleanArray(PadButton.COUNT)
    private val dirFromHat = BooleanArray(PadButton.COUNT)
    private val dirFromStick = BooleanArray(PadButton.COUNT)

    /** A virtuális gamepad tengelyei: bal kar X, Y, jobb kar X, Y. */
    val axes = FloatArray(4)
    private var triggerL = 0f
    private var triggerR = 0f

    fun isPressed(b: PadButton) = pressed[b.index]

    /** A lenyomott gombok bitmaszkja (bit i = Gamepad API `buttons[i]`). */
    val buttonMask: Int
        get() = pressed.foldIndexed(0) { i, acc, p -> if (p) acc or (1 shl i) else acc }

    /** A gombok analóg értéke (a ravaszoknál 0–1, máshol 0 vagy 1). */
    fun buttonValue(b: PadButton): Float = when (b) {
        PadButton.LT -> triggerL
        PadButton.RT -> triggerR
        else -> if (pressed[b.index]) 1f else 0f
    }

    /** Gomb lenyomása vagy felengedése. Az ismétlődő (auto-repeat) eseményeket a hívó szűri. */
    fun button(b: PadButton, down: Boolean): List<KeyOut> {
        val out = ArrayList<KeyOut>(1)
        if (b.isDirection) {
            dirFromKey[b.index] = down
            updateDirection(b, out)
        } else {
            set(b, down, out)
        }
        return out
    }

    /**
     * Tengelyek. [hatX]/[hatY] a D-pad (-1, 0, 1), [x]/[y] a bal kar, [rx]/[ry] a jobb kar,
     * [lt]/[rt] a ravaszok (0–1).
     */
    fun motion(
        x: Float, y: Float, hatX: Float, hatY: Float,
        rx: Float = 0f, ry: Float = 0f, lt: Float = 0f, rt: Float = 0f,
    ): List<KeyOut> {
        val out = ArrayList<KeyOut>(2)
        axes[0] = x; axes[1] = y; axes[2] = rx; axes[3] = ry

        dirFromHat[PadButton.LEFT.index] = hatX < -0.5f
        dirFromHat[PadButton.RIGHT.index] = hatX > 0.5f
        dirFromHat[PadButton.UP.index] = hatY < -0.5f
        dirFromHat[PadButton.DOWN.index] = hatY > 0.5f

        stick(PadButton.LEFT, -x)
        stick(PadButton.RIGHT, x)
        stick(PadButton.UP, -y)
        stick(PadButton.DOWN, y)

        for (d in DIRECTIONS) updateDirection(d, out)

        triggerL = lt.coerceIn(0f, 1f)
        triggerR = rt.coerceIn(0f, 1f)
        set(PadButton.LT, triggerL > 0.5f, out)
        set(PadButton.RT, triggerR > 0.5f, out)
        return out
    }

    /** Minden lenyomott gomb felengedése (pl. ha a kontroller lekapcsolódott, vagy az app háttérbe ment). */
    fun releaseAll(): List<KeyOut> {
        val out = ArrayList<KeyOut>()
        dirFromKey.fill(false); dirFromHat.fill(false); dirFromStick.fill(false)
        axes.fill(0f); triggerL = 0f; triggerR = 0f
        for (b in PadButton.entries) set(b, false, out)
        return out
    }

    private fun stick(dir: PadButton, value: Float) {
        val i = dir.index
        dirFromStick[i] = if (dirFromStick[i]) value > map.release else value >= map.deadzone && abs(value) <= 1.01f
    }

    private fun updateDirection(d: PadButton, out: MutableList<KeyOut>) {
        val i = d.index
        set(d, dirFromKey[i] || dirFromHat[i] || dirFromStick[i], out)
    }

    private fun set(b: PadButton, down: Boolean, out: MutableList<KeyOut>) {
        if (pressed[b.index] == down) return
        pressed[b.index] = down
        if (b in map.appBack) return
        map.keys[b]?.let { out += KeyOut(down, it) }
    }

    companion object {
        private val DIRECTIONS = listOf(PadButton.UP, PadButton.DOWN, PadButton.LEFT, PadButton.RIGHT)
    }
}
