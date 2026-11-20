package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.Job
import org.maplibre.compose.interaction.ClickResult
import kotlin.math.abs

/** Remembers the last tap that was looked at before the map delivered its click, to recognize
 *  that click when it arrives after the double tap timeout. */
internal class EarlyTaps {
    class Tap(val offset: DpOffset, val upTime: Long) {
        /** the early look at this tap, as long as it is running */
        var job: Job? = null
        /** what the early look came to, null if it had no use for the tap or is not finished */
        var result: ClickResult? = null
    }

    var last: Tap? = null

    /** Returns what the early look at the tap behind the click at [offset], released at
     *  [upTime], came to. Null if that tap was not looked at early or the early look had no use
     *  for it. A tap can only be claimed once. */
    fun claim(offset: DpOffset, upTime: Long): ClickResult? {
        val tap = last ?: return null
        if (abs(upTime - tap.upTime) > MAX_CLICK_DELAY) return null
        if (abs((offset.x - tap.offset.x).value) > MAX_CLICK_DISTANCE) return null
        if (abs((offset.y - tap.offset.y).value) > MAX_CLICK_DISTANCE) return null
        last = null
        // an early look that is still not finished by now loses to the usual route
        tap.job?.cancel()
        return tap.result
    }

    companion object {
        /** in milliseconds. The click is for the same tap, so only clock differences count */
        private const val MAX_CLICK_DELAY = 1000
        /** in dp. The click is at the same position, so only rounding differences count */
        private const val MAX_CLICK_DISTANCE = 1f
    }
}

/** Whether a finger put down at [down] at [downTime] continues the tap that was at [previous]
 *  and ended at [previousUpTime], i.e. whether the map takes the two for a double tap or the
 *  start of a quick zoom. */
internal fun isSecondTap(
    previous: Offset?,
    previousUpTime: Long,
    down: Offset,
    downTime: Long,
    doubleTapTimeoutMillis: Long,
    doubleTapSlop: Float,
): Boolean =
    previous != null &&
    downTime - previousUpTime <= doubleTapTimeoutMillis &&
    (down - previous).getDistance() <= doubleTapSlop
