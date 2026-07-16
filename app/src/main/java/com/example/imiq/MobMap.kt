package com.example.imiq

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Reusable dark map — now MapLibre (GL vector tiles) instead of inverted raster
 * tiles. Uses Carto's free "Dark Matter" vector style (no API key) for a crisp,
 * professional dark basemap, and draws the route as a GPU polyline with a dark
 * casing under a bright coloured core (the Google-style two-tone line) plus
 * white-haloed endpoint dots.
 *
 * Public API is unchanged: [route] is a polyline of (lat, lon) pairs; when
 * present the camera fits it, otherwise it centres on [center]. The update block
 * re-runs on recompose, so changing the selected mode redraws the route live.
 */
private const val DARK_STYLE = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

@Composable
fun MobMap(
    modifier: Modifier = Modifier,
    route: List<Pair<Double, Double>> = emptyList(),
    routeColor: Color = Mob.primary,
    center: Pair<Double, Double> = MAGDEBURG,
    zoom: Double = 13.5,
    interactive: Boolean = true,
    bottomInsetPx: Int = 80
) {
    val holder = remember { MapHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = holder.view ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.view?.onStop()
            holder.view?.onDestroy()
            holder.view = null
            holder.map = null
            holder.style = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapLibre.getInstance(ctx)
            MapView(ctx).also { mv ->
                holder.view = mv
                // We are created while the host is already resumed.
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { map ->
                    holder.map = map
                    map.uiSettings.setAllGesturesEnabled(interactive)
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(center.first, center.second))
                        .zoom(zoom)
                        .build()
                    map.setStyle(Style.Builder().fromUri(DARK_STYLE)) { style ->
                        holder.style = style
                        applyRoute(map, style, route, routeColor, center, zoom, bottomInsetPx)
                    }
                }
            }
        },
        update = {
            val map = holder.map
            val style = holder.style
            if (map != null && style != null && style.isFullyLoaded) {
                applyRoute(map, style, route, routeColor, center, zoom, bottomInsetPx)
            }
        }
    )
}

/** Adds (once) and updates the route line, endpoint dots, and camera. */
private fun applyRoute(
    map: MapLibreMap,
    style: Style,
    route: List<Pair<Double, Double>>,
    routeColor: Color,
    center: Pair<Double, Double>,
    zoom: Double,
    bottomInsetPx: Int
) {
    val core = routeColor.toArgb()
    val casing = Mob.bg.toArgb()

    if (style.getSource(SRC_ROUTE) == null) {
        style.addSource(GeoJsonSource(SRC_ROUTE))
        style.addSource(GeoJsonSource(SRC_ENDS))
        style.addLayer(
            LineLayer(LYR_CASING, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(casing),
                PropertyFactory.lineWidth(11f),
                PropertyFactory.lineOpacity(0.9f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        style.addLayer(
            LineLayer(LYR_CORE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(core),
                PropertyFactory.lineWidth(6f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        style.addLayer(
            CircleLayer(LYR_ENDS, SRC_ENDS).withProperties(
                PropertyFactory.circleRadius(6.5f),
                PropertyFactory.circleColor(android.graphics.Color.WHITE),
                PropertyFactory.circleStrokeColor(core),
                PropertyFactory.circleStrokeWidth(3.5f)
            )
        )
    } else {
        (style.getLayer(LYR_CORE) as? LineLayer)?.setProperties(PropertyFactory.lineColor(core))
        (style.getLayer(LYR_ENDS) as? CircleLayer)?.setProperties(PropertyFactory.circleStrokeColor(core))
    }

    val routeSrc = style.getSourceAs<GeoJsonSource>(SRC_ROUTE)
    val endsSrc = style.getSourceAs<GeoJsonSource>(SRC_ENDS)

    if (route.size < 2) {
        routeSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        endsSrc?.setGeoJson(FeatureCollection.fromFeatures(emptyArray<Feature>()))
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(center.first, center.second)).zoom(zoom).build()
        return
    }

    // (lat, lon) -> Point(lon, lat)
    val pts = route.map { Point.fromLngLat(it.second, it.first) }
    routeSrc?.setGeoJson(Feature.fromGeometry(LineString.fromLngLats(pts)))
    endsSrc?.setGeoJson(
        FeatureCollection.fromFeatures(
            arrayOf(Feature.fromGeometry(pts.first()), Feature.fromGeometry(pts.last()))
        )
    )

    val bounds = LatLngBounds.Builder()
    route.forEach { bounds.include(LatLng(it.first, it.second)) }
    try {
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(bounds.build(), 70, 80, 70, bottomInsetPx),
            600
        )
    } catch (e: Exception) {
        // Degenerate bounds — fall back to centring on the start.
        map.cameraPosition = CameraPosition.Builder()
            .target(LatLng(route.first().first, route.first().second)).zoom(zoom).build()
    }
}

private const val SRC_ROUTE = "imiq-route-src"
private const val SRC_ENDS = "imiq-ends-src"
private const val LYR_CASING = "imiq-route-casing"
private const val LYR_CORE = "imiq-route-core"
private const val LYR_ENDS = "imiq-route-ends"

val MAGDEBURG = 52.1305 to 11.6276

class MapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var style: Style? = null
}
