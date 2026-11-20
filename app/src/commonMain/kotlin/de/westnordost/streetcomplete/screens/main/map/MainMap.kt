package de.westnordost.streetcomplete.screens.main.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.map.CameraConstraints
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.overlay.MapOverlayScope

/** Presents the shared map. */
@Composable
fun MainMap(
    state: MapState,
    modifier: Modifier = Modifier,
    onPan: () -> Unit = {},
    onRotate: () -> Unit = {},
    onMapClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    onMapLongClick: (ClickEvent) -> ClickResult = { ClickResult.Pass },
    overlay: @Composable MapOverlayScope.() -> Unit = {},
) {
    // The map holds a tap back for the double tap timeout, because a second tap could still
    // make it a double tap or a quick zoom. It takes that timeout from the view configuration
    val viewConfiguration = LocalViewConfiguration.current
    val mapViewConfiguration = remember(viewConfiguration) {
        object : ViewConfiguration by viewConfiguration {
            override val doubleTapTimeoutMillis: Long get() = DOUBLE_TAP_TIMEOUT
        }
    }

    CompositionLocalProvider(LocalViewConfiguration provides mapViewConfiguration) {
        MaplibreMap(
            modifier = modifier,
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
                    click { onUnhandled(onMapClick) }
                    longClick { onEvent(onMapLongClick) }
                }
            },
            overlay = {
                // what is shown on top of the map keeps the usual timeout
                CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
                    overlay()
                }
            },
        )
    }
}

/** in milliseconds. The platform default is 300 ms */
private const val DOUBLE_TAP_TIMEOUT = 100L
