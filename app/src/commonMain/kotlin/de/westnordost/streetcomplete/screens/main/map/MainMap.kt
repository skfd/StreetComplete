package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
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
 *  finger lifts. If it returns true, the tap is done with: the click the map delivers for it
 *  later is dropped, and a second tap on the same spot within the double tap timeout is kept
 *  from the map, so that it does not zoom. */
@Composable
fun MainMap(
    state: MapState,
    modifier: Modifier = Modifier,
    onPan: () -> Unit = {},
    onRotate: () -> Unit = {},
    onEarlyTap: suspend (DpOffset) -> Boolean = { false },
    onMapClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    onMapLongClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    overlay: @Composable MapOverlayScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val currentOnEarlyTap by rememberUpdatedState(onEarlyTap)
    var earlyTapJob by remember { mutableStateOf<Job?>(null) }
    // the tap that onEarlyTap is done with
    var handledTap by remember { mutableStateOf<Tap?>(null) }

    MaplibreMap(
        // watches every event before the map sees it
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // the map would take a second tap on what is selected already for a double tap
                // and zoom. It ignores a tap that is consumed
                val isSecondTap = handledTap?.let {
                    down.uptimeMillis - it.upTime <= viewConfiguration.doubleTapTimeoutMillis &&
                    (down.position - it.position).getDistance() <= DoubleTapSlop.toPx()
                } == true
                if (isSecondTap) down.consume()

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
                if (isTap && !isSecondTap &&
                    upTime - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis
                ) {
                    val offset = DpOffset(down.position.x.toDp(), down.position.y.toDp())
                    earlyTapJob = scope.launch {
                        if (currentOnEarlyTap(offset)) handledTap = Tap(down.position, upTime)
                    }
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
                        // an early look that is still not finished by now loses to the usual route
                        earlyTapJob?.cancel()
                        if (event.uptimeMillis == handledTap?.upTime) ClickResult.Consume
                        else ClickResult.Pass
                    }
                    onUnhandled(onMapClick)
                }
                longClick { onEvent(onMapLongClick) }
            }
        },
        overlay = overlay,
    )
}

private class Tap(val position: Offset, val upTime: Long)

/** the distance within which the map takes two taps for a double tap */
private val DoubleTapSlop = 100.dp
