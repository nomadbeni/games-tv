package io.github.nomadbeni.gamestv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ControllerCoreTest {

    private val map: KeyMap = KeyMap.parse(File("src/main/assets/keymap.json").readText())
    private fun core() = ControllerCore(map)

    private fun List<KeyOut>.str() = joinToString(" ") { (if (it.down) "+" else "-") + it.stroke.code }

    // ------------------------------------------------------------ gombkiosztás

    @Test fun `a kiosztás a tervben és a játékokban szereplő billentyűket adja`() {
        assertEquals(KeyStroke("Enter", "Enter"), map.keys[PadButton.A])
        assertEquals(KeyStroke("Escape", "Escape"), map.keys[PadButton.B])
        assertEquals(KeyStroke(" ", "Space"), map.keys[PadButton.X])
        assertEquals(KeyStroke("Escape", "Escape"), map.keys[PadButton.START])
        assertEquals(KeyStroke("ArrowUp", "ArrowUp"), map.keys[PadButton.UP])
        assertEquals(KeyStroke("ArrowLeft", "ArrowLeft"), map.keys[PadButton.LEFT])
        assertTrue(PadButton.SELECT in map.appBack)
    }

    @Test fun `az Android billentyűkódok a megfelelő gombra mutatnak`() {
        assertEquals(PadButton.A, map.androidKeys["KEYCODE_BUTTON_A"])
        assertEquals(PadButton.A, map.androidKeys["KEYCODE_DPAD_CENTER"])
        assertEquals(PadButton.B, map.androidKeys["KEYCODE_BUTTON_B"])
        assertEquals(PadButton.UP, map.androidKeys["KEYCODE_DPAD_UP"])
        assertEquals(PadButton.START, map.androidKeys["KEYCODE_BUTTON_START"])
        assertFalse("A Vissza a rendszeré", "KEYCODE_BACK" in map.androidKeys)
    }

    @Test fun `gomb lenyomása és felengedése egy keydown és egy keyup`() {
        val c = core()
        assertEquals("+Enter", c.button(PadButton.A, true).str())
        assertTrue(c.isPressed(PadButton.A))
        assertEquals(1, c.buttonMask)
        assertEquals("", c.button(PadButton.A, true).str()) // ismételt lenyomás nem küld újat
        assertEquals("-Enter", c.button(PadButton.A, false).str())
        assertEquals(0, c.buttonMask)
    }

    @Test fun `a kiosztás nélküli gomb csak a gamepad állapotában látszik`() {
        val c = core()
        assertEquals("", c.button(PadButton.RB, true).str())
        assertEquals(1 shl 5, c.buttonMask)
    }

    @Test fun `az app-vissza gomb nem ad billentyűt`() {
        val c = core()
        assertEquals("", c.button(PadButton.SELECT, true).str())
        assertEquals("", c.button(PadButton.SELECT, false).str())
    }

    @Test fun `ismeretlen gombnév hibát ad`() {
        val r = runCatching { KeyMap.parse("""{"keys":{"Z9":{"key":"a","code":"KeyA"}}}""") }
        assertTrue(r.isFailure)
    }

    // ------------------------------------------------------------ holtsáv

    @Test fun `a kar a holtsávon belül nem küld semmit`() {
        val c = core()
        for (v in listOf(0f, 0.1f, 0.3f, 0.49f, -0.49f)) {
            assertEquals("", c.motion(v, v, 0f, 0f).str())
        }
        assertEquals(0, c.buttonMask)
    }

    @Test fun `a küszöb átlépésekor egy keydown, visszatéréskor egy keyup`() {
        val c = core()
        val seq = listOf(0f, 0.3f, 0.5f, 0.7f, 1f, 0.9f, 0.6f, 0.45f, 0.41f, 0.39f, 0.2f, 0f)
        val out = seq.flatMap { c.motion(it, 0f, 0f, 0f) }
        assertEquals("+ArrowRight -ArrowRight", out.str())
    }

    @Test fun `a határon remegő kar nem okoz eseményzáport`() {
        val c = core()
        val out = (0 until 50).flatMap { c.motion(if (it % 2 == 0) 0.51f else 0.47f, 0f, 0f, 0f) }
        assertEquals("+ArrowRight", out.str())
    }

    @Test fun `átlós kar két irányt nyom le`() {
        val c = core()
        assertEquals("+ArrowUp +ArrowLeft", c.motion(-0.8f, -0.8f, 0f, 0f).str())
        assertEquals("-ArrowUp", c.motion(-0.8f, 0f, 0f, 0f).str())
        assertEquals("-ArrowLeft", c.motion(0f, 0f, 0f, 0f).str())
    }

    @Test fun `iránybillentyű és D-pad tengely együtt sem duplikál`() {
        val c = core()
        // A Mylox D-padja jöhet billentyűként és HAT tengelyként is, akár egyszerre.
        assertEquals("+ArrowDown", c.button(PadButton.DOWN, true).str())
        assertEquals("", c.motion(0f, 0f, 0f, 1f).str())
        assertEquals("", c.button(PadButton.DOWN, false).str()) // a HAT még lenyomva tartja
        assertEquals("-ArrowDown", c.motion(0f, 0f, 0f, 0f).str())
    }

    @Test fun `D-pad tengely egyedül is működik`() {
        val c = core()
        assertEquals("+ArrowLeft", c.motion(0f, 0f, -1f, 0f).str())
        assertEquals("-ArrowLeft +ArrowRight", c.motion(0f, 0f, 1f, 0f).str())
        assertEquals(1 shl PadButton.RIGHT.index, c.buttonMask)
    }

    @Test fun `ravaszok analóg értéke és gombállapota`() {
        val c = core()
        c.motion(0f, 0f, 0f, 0f, lt = 0.3f, rt = 0.8f)
        assertEquals(0.3f, c.buttonValue(PadButton.LT), 1e-6f)
        assertFalse(c.isPressed(PadButton.LT))
        assertTrue(c.isPressed(PadButton.RT))
    }

    @Test fun `releaseAll minden lenyomott gombot felenged`() {
        val c = core()
        c.button(PadButton.A, true)
        c.motion(1f, 0f, 0f, 0f)
        assertEquals("-ArrowRight -Enter", c.releaseAll().str().split(" ").sorted().joinToString(" "))
        assertEquals(0, c.buttonMask)
        assertEquals(0f, c.axes[0], 0f)
    }

    @Test fun `a tengelyek a Gamepad API sorrendjében vannak`() {
        val c = core()
        c.motion(0.25f, -0.5f, 0f, 0f, rx = 0.1f, ry = -0.2f)
        assertEquals(listOf(0.25f, -0.5f, 0.1f, -0.2f), c.axes.toList())
    }
}
