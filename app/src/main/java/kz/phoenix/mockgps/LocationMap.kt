package kz.phoenix.mockgps

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context ->
                    MapWebView(
                        context, interactive, initialCamera,
                        onCameraChanged = { latestCameraChanged.value(it) },
                        onReady = { latestReady.value(it) }
                    )
                },
                update = { it.render(position, mocked, selecting) },
                onRelease = { it.release() },
                modifier = Modifier.matchParentSize()
            )
            previewOverlay()
        }
        TextButton(
            onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("© OpenStreetMap contributors")
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
                onReady(this@MapWebView)
            }
        }
        loadUrl(MAP_URL)
    }

    fun render(position: Coordinates?, mocked: Boolean, selecting: Boolean) {
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

    private fun updatePosition() {
        val point = position?.let { "[${it.latitude}, ${it.longitude}]" } ?: "null"
        evaluateJavascript("showPosition($point, $mocked)", null)
    }

    private fun updateSelection() {
        evaluateJavascript("showSelection($selecting)", null)
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
