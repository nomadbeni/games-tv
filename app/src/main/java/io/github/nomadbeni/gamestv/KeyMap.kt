package io.github.nomadbeni.gamestv

import org.json.JSONObject

/** A szabványos (W3C „standard”) gamepad gombjai, a Gamepad API-beli sorszámukkal. */
enum class PadButton(val index: Int) {
    A(0), B(1), X(2), Y(3), LB(4), RB(5), LT(6), RT(7),
    SELECT(8), START(9), L3(10), R3(11),
    UP(12), DOWN(13), LEFT(14), RIGHT(15), HOME(16);

    val isDirection: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT

    companion object {
        const val COUNT = 17
        fun byName(name: String): PadButton? = entries.firstOrNull { it.name == name }
    }
}

/** Egy billentyű, ahogy a weboldal `KeyboardEvent`-je látja (`key` és `code`). */
data class KeyStroke(val key: String, val code: String)

/**
 * A gombkiosztás (assets/keymap.json).
 *
 * @param deadzone a bal kar ennyi kitérésnél számít lenyomott iránynak
 * @param release a kar ennyi alatt engedi el az irányt (kis hiszterézis, hogy a határon ne villogjon)
 * @param androidKeys Android billentyűkód-név (pl. `KEYCODE_BUTTON_A`) → gamepad gomb
 * @param keys gamepad gomb → billentyű; ami nincs benne, az csak a virtuális gamepadon látszik
 * @param appBack ezek a gombok nem a játékhoz mennek, hanem az app „vissza” műveletét indítják
 */
data class KeyMap(
    val deadzone: Float,
    val release: Float,
    val androidKeys: Map<String, PadButton>,
    val keys: Map<PadButton, KeyStroke>,
    val appBack: Set<PadButton>,
) {
    companion object {
        fun parse(json: String): KeyMap {
            val o = JSONObject(json)
            val deadzone = o.optDouble("deadzone", 0.5).toFloat()
            require(deadzone > 0f && deadzone < 1f) { "deadzone 0 és 1 között legyen" }
            val release = o.optDouble("release", deadzone * 0.8).toFloat()
            require(release > 0f && release <= deadzone) { "release 0 és deadzone között legyen" }

            val androidKeys = LinkedHashMap<String, PadButton>()
            o.optJSONObject("androidKeys")?.let { m ->
                for (name in m.keys()) {
                    androidKeys[name] = PadButton.byName(m.getString(name))
                        ?: error("Ismeretlen gomb: ${m.getString(name)}")
                }
            }

            val keys = LinkedHashMap<PadButton, KeyStroke>()
            o.optJSONObject("keys")?.let { m ->
                for (name in m.keys()) {
                    val btn = PadButton.byName(name) ?: error("Ismeretlen gomb: $name")
                    if (m.isNull(name)) continue
                    val k = m.getJSONObject(name)
                    keys[btn] = KeyStroke(k.getString("key"), k.getString("code"))
                }
            }

            val appBack = LinkedHashSet<PadButton>()
            o.optJSONArray("appBack")?.let { a ->
                for (i in 0 until a.length()) {
                    appBack += PadButton.byName(a.getString(i)) ?: error("Ismeretlen gomb: ${a.getString(i)}")
                }
            }
            return KeyMap(deadzone, release, androidKeys, keys, appBack)
        }
    }
}
