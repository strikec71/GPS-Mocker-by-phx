package kz.phoenix.mockgps

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray

@Composable
internal fun LocationMap(
    position: Coordinates?,
    mocked: Boolean,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    selecting: Boolean = false,
    initialCamera: DoubleArray? = null,
    onCameraChanged: (DoubleArray) -> Unit = {},
    onReady: (MapWebView) -> Unit = {},
    previewOverlay: @Composable () -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val latestCameraChanged = rememberUpdatedState(onCameraChanged)
    val latestReady = rememberUpdatedState(onReady)

    val dark = isSystemInDarkTheme()
    Box(modifier) {
        AndroidView(
            factory = { context ->
                MapWebView(context, interactive, initialCamera,
                    onCameraChanged = { latestCameraChanged.value(it) },
                    onReady = { latestReady.value(it) })
            },
            update = { it.render(position, mocked, selecting, dark) },
            onRelease = { it.release() }, modifier = Modifier.matchParentSize()
        )
        previewOverlay()
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()
            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), Color.Transparent))))
        // Keep a 48 dp hit target while painting only the compact credit label.
        Box(modifier = Modifier.align(Alignment.BottomEnd)
            .then(if (interactive) Modifier.navigationBarsPadding() else Modifier)
            .padding(end = 6.dp, bottom = 2.dp).heightIn(min = 48.dp)
            .clickable(onClickLabel = "Об OpenStreetMap") {
                uriHandler.openUri("https://www.openstreetmap.org/copyright")
            }, contentAlignment = Alignment.BottomEnd) {
            Text("© OpenStreetMap", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp))
        }

    }

}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class MapWebView(
    context: Context,
    interactive: Boolean,
    camera: DoubleArray?,
    private val onCameraChanged: (DoubleArray) -> Unit,
    private val onReady: (MapWebView) -> Unit
) : WebView(context) {
    private var ready = false
    private var released = false
    private var position: Coordinates? = null
    private var mocked = false
    private var selecting = false
    private var dark = false

    init {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/map/", WebViewAssetLoader.PathHandler { path ->
                val type = when (path) {
                    "index.html" -> "text/html"
                    "map.js", "leaflet/leaflet.js" -> "text/javascript"
                    "map.css", "leaflet/leaflet.css" -> "text/css"
                    else -> return@PathHandler null
                }
                WebResourceResponse(type, "UTF-8", context.assets.open("map/$path"))
            })
            .build()

        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.userAgentString += " GPS-Mocker-by-phx/1.0 (+https://github.com/strikec71/GPS-Mocker-by-phx)"
        if (interactive) {
            addJavascriptInterface(CameraBridge(), "AndroidMap")
        } else {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onPageFinished(view: WebView, url: String) {
                if (ready || released || url != MAP_URL) return
                val initial = camera?.joinToString(",", "[", "]") ?: "null"
                evaluateJavascript("setupMap($interactive, $initial)", null)
                ready = true
                updatePosition()
                updateSelection()
                updateAppearance()
                onReady(this@MapWebView)
            }
        }
        loadUrl(MAP_URL)
    }

    fun render(position: Coordinates?, mocked: Boolean, selecting: Boolean, dark: Boolean) {
        if (this.dark != dark) {
            this.dark = dark
            if (ready) updateAppearance()
        }
        if (this.position != position || this.mocked != mocked) {
            this.position = position
            this.mocked = mocked
            if (ready) updatePosition()
        }
        if (this.selecting != selecting) {
            this.selecting = selecting
            if (ready) updateSelection()
        }
    }

    private fun updateAppearance() {
        evaluateJavascript("setAppearance($dark)", null)
    }

    private fun updatePosition() {
        val point = position?.let { "[${it.latitude}, ${it.longitude}]" } ?: "null"
        evaluateJavascript("showPosition($point, $mocked)", null)
    }

    private fun updateSelection() {
        evaluateJavascript("showSelection($selecting)", null)
    }

    fun zoomBy(delta: Int) {
        if (ready && !released) evaluateJavascript("map.setZoom(map.getZoom() + ${delta.coerceIn(-1, 1)}, {animate: false})", null)
    }

    fun centerOn(point: Coordinates) {
        if (ready && !released) {
            evaluateJavascript("centerOn(${point.latitude}, ${point.longitude})", null)
        }
    }

    fun readCenter(onResult: (Coordinates?) -> Unit) {
        if (!ready || released) return
        evaluateJavascript("selectedPosition()") { result ->
            if (!released) {
                val coordinates = runCatching {
                    val array = JSONArray(result)
                    val lat = Prefs.parseLat(array.getString(0)) ?: return@runCatching null
                    val lon = Prefs.parseLon(array.getString(1)) ?: return@runCatching null
                    Coordinates(lat, lon)
                }.getOrNull()
                onResult(coordinates)
            }
        }
    }

    fun release() {
        released = true
        stopLoading()
        removeJavascriptInterface("AndroidMap")
        destroy()
    }

    private inner class CameraBridge {
        @JavascriptInterface
        fun cameraChanged(latitude: Double, longitude: Double, zoom: Double) {
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 || zoom !in 2.0..19.0) return
            post {
                if (!released) onCameraChanged(doubleArrayOf(latitude, longitude, zoom))
            }
        }
    }

    private companion object {
        const val MAP_URL = "https://appassets.androidplatform.net/assets/map/index.html"
    }
}
