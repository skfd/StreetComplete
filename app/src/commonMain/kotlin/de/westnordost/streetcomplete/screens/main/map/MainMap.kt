package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.MapOverlayScope

/** Presents the shared map.
 *
 *  The map holds a tap back for the double tap timeout (300 ms), because a second tap could
 *  still make it a double tap or a quick zoom. [onEarlyTap] is asked about a tap the moment the
 *  finger lifts. It returns null if it has no use for the tap, which then takes the usual route.
 *  Otherwise the tap is done with: the click the map delivers for it later skips the layers and
 *  only still goes to [onMapClick], and only if [onEarlyTap] returned [ClickResult.Pass]. A
 *  second tap on the same spot within the double tap timeout is then kept from the map, so
 *  that it does not zoom. */
@Composable
fun MainMap(
    state: MapState,
    modifier: Modifier = Modifier,
    onPan: () -> Unit = {},
    onRotate: () -> Unit = {},
    onEarlyTap: suspend (DpOffset) -> ClickResult? = { null },
    onMapClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    onMapLongClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    overlay: @Composable MapOverlayScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val earlyTaps = remember { EarlyTaps() }
    val currentOnEarlyTap by rememberUpdatedState(onEarlyTap)

    MaplibreMap(
        // watches every event before the map sees it
        modifier = modifier.pointerInput(Unit) {
            var previousDown: Offset? = null
            var previousUpTime = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // the second tap of a double tap or the start of a quick zoom belongs to the map
                val isSecondTap = isSecondTap(
                    previous = previousDown,
                    previousUpTime = previousUpTime,
                    down = down.position,
                    downTime = down.uptimeMillis,
                    doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
                    doubleTapSlop = DoubleTapSlop.toPx(),
                )
                // ... unless the first tap is done with already: the map ignores a consumed tap
                val previousTap = earlyTaps.last?.takeIf { it.upTime == previousUpTime }
                if (isSecondTap && previousTap?.result != null) down.consume()

                var isTap = true
                var upTime: Long
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.singleOrNull()?.takeIf { it.id == down.id }
                    if (change == null ||
                        (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                    ) {
                        isTap = false
                    }
                    upTime = event.changes.maxOf { it.uptimeMillis }
                    if (event.changes.none { it.pressed }) break
                }
                if (upTime - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) {
                    isTap = false
                }
                previousDown = down.position
                previousUpTime = upTime

                if (isTap && !isSecondTap) {
                    val offset = DpOffset(down.position.x.toDp(), down.position.y.toDp())
                    val tap = EarlyTaps.Tap(offset, upTime)
                    earlyTaps.last = tap
                    tap.job = scope.launch { tap.result = currentOnEarlyTap(offset) }
                }
            }
        },
        state = state,
        cameraConstraints = CameraConstraints {
            minZoom = 0.0
            maxZoom = 22.0
        },
        interactions = MapInteractions {
            camera {
                pan { onStart(onPan) }
                rotate { onStart(onRotate) }
            }
            callbacks {
                click {
                    // runs before the layers are asked what is under the click
                    onEvent { event ->
                        when (earlyTaps.claim(event.screenOffset, event.uptimeMillis)) {
                            null -> ClickResult.Pass
                            ClickResult.Pass -> {
                                onMapClick(event)
                                ClickResult.Consume
                            }
                            ClickResult.Consume -> ClickResult.Consume
                        }
                    }
                    onUnhandled(onMapClick)
                }
                longClick { onEvent(onMapLongClick) }
            }
        },
        overlay = overlay,
    )
}

/** the distance within which the map takes two taps for a double tap */
private val DoubleTapSlop = 100.dp
