package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.maplibre.compose.interaction.ClickResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EarlyTapsTest {

    private val offset = DpOffset(100.dp, 200.dp)

    private fun earlyTaps(result: ClickResult?, upTime: Long = 5000) = EarlyTaps().apply {
        last = EarlyTaps.Tap(offset, upTime).also { it.result = result }
    }

    @Test fun `claims nothing if no tap was looked at`() {
        assertNull(EarlyTaps().claim(offset, 5000))
    }

    @Test fun `claims the click for the tap that was looked at`() {
        assertEquals(ClickResult.Consume, earlyTaps(ClickResult.Consume).claim(offset, 5000))
        assertEquals(ClickResult.Pass, earlyTaps(ClickResult.Pass).claim(offset, 5000))
    }

    @Test fun `claims nothing if the early look had no use for the tap`() {
        assertNull(earlyTaps(null).claim(offset, 5000))
    }

    @Test fun `claims a tap only once`() {
        val earlyTaps = earlyTaps(ClickResult.Consume)
        assertEquals(ClickResult.Consume, earlyTaps.claim(offset, 5000))
        assertNull(earlyTaps.claim(offset, 5000))
    }

    @Test fun `tolerates rounding of the position`() {
        val nearby = DpOffset(100.4.dp, 199.6.dp)
        assertEquals(ClickResult.Consume, earlyTaps(ClickResult.Consume).claim(nearby, 5000))
    }

    @Test fun `does not claim a click elsewhere`() {
        val earlyTaps = earlyTaps(ClickResult.Consume)
        assertNull(earlyTaps.claim(DpOffset(102.dp, 200.dp), 5000))
        assertNull(earlyTaps.claim(DpOffset(100.dp, 198.dp), 5000))
        // and the tap is still there for its own click
        assertEquals(ClickResult.Consume, earlyTaps.claim(offset, 5000))
    }

    @Test fun `does not claim a click at another time`() {
        val earlyTaps = earlyTaps(ClickResult.Consume)
        assertNull(earlyTaps.claim(offset, 6001))
        assertNull(earlyTaps.claim(offset, 3999))
    }

    @Test fun `first tap is not a second tap`() {
        assertFalse(isSecondTap(null, 0, Offset(10f, 10f), 100, 300, 50f))
    }

    @Test fun `tap shortly after and close to the previous is a second tap`() {
        assertTrue(isSecondTap(Offset(10f, 10f), 1000, Offset(40f, 50f), 1300, 300, 50f))
    }

    @Test fun `tap after the double tap timeout is not a second tap`() {
        assertFalse(isSecondTap(Offset(10f, 10f), 1000, Offset(10f, 10f), 1301, 300, 50f))
    }

    @Test fun `tap beyond the double tap slop is not a second tap`() {
        assertFalse(isSecondTap(Offset(10f, 10f), 1000, Offset(40f, 51f), 1100, 300, 50f))
    }
}
