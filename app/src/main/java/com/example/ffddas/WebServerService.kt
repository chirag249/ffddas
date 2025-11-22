package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Embedded HTTP server for serving processed camera frames to web clients
 */
class WebServerService(port: Int = 8080) : NanoHTTPD("0.0.0.0", port) {
    
    companion object {
        private const val TAG = "WebServerService"
        enum class FilterMode { NONE, GRAYSCALE, EDGE_DETECTION }
        private var filterMode: FilterMode = FilterMode.NONE
    }

    // Callback hooks provided by MainActivity
    private var captureCallback: (() -> Boolean)? = null
    private var switchCameraCallback: (() -> Boolean)? = null
    private var statusCallback: (() -> Map<String, Any>)? = null
    private var setFilterCallback: ((String) -> Boolean)? = null
    private var listGalleryCallback: (() -> List<String>)? = null

    fun setCaptureCallback(cb: (() -> Boolean)?) { captureCallback = cb }
    fun setSwitchCameraCallback(cb: (() -> Boolean)?) { switchCameraCallback = cb }
    fun setStatusCallback(cb: (() -> Map<String, Any>)?) { statusCallback = cb }
    fun setSetFilterCallback(cb: ((String) -> Boolean)?) { setFilterCallback = cb }
    fun setListGalleryCallback(cb: (() -> List<String>)?) { listGalleryCallback = cb }
    
    // HTML viewer content
    private val viewerHtml = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FFDDAS Live Viewer</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            background: #1a1a1a;
            color: #ffffff;
            display: flex;
            flex-direction: column;
            height: 100vh;
            overflow: hidden;
        }
        
        .header {
            background: #2d2d2d;
            padding: 15px 20px;
            border-bottom: 2px solid #444;
            display: flex;
            justify-content: space-between;
            align-items: center;
            flex-wrap: wrap;
            gap: 10px;
        }
        
        .title {
            font-size: 1.5em;
            font-weight: bold;
            color: #00bcd4;
        }
        
        .status-container {
            display: flex;
            align-items: center;
            gap: 15px;
        }
        
        .status-indicator {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 15px;
            background: #333;
            border-radius: 20px;
        }
        
        .status-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            animation: pulse 2s infinite;
        }
        
        .status-dot.connected {
            background: #4caf50;
        }
        
        .status-dot.disconnected {
            background: #f44336;
            animation: none;
        }
        
        .status-dot.processing {
            background: #ff9800;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        .controls {
            display: flex;
            gap: 10px;
            align-items: center;
        }
        
        button {
            padding: 10px 20px;
            background: #00bcd4;
            border: none;
            border-radius: 5px;
            color: white;
            font-size: 14px;
            cursor: pointer;
            transition: background 0.3s;
        }
        
        button:hover {
            background: #0097a7;
        }
        
        button:active {
            background: #006978;
        }
        
        select {
            padding: 10px;
            background: #333;
            border: 1px solid #555;
            border-radius: 5px;
            color: white;
            font-size: 14px;
            cursor: pointer;
        }
        
        .viewer-container {
            flex: 1;
            display: flex;
            justify-content: center;
            align-items: center;
            padding: 20px;
            overflow: hidden;
        }
        
        canvas {
            max-width: 100%;
            max-height: 100%;
            border: 2px solid #444;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5);
        }
        
        .stats {
            background: #2d2d2d;
            padding: 10px 20px;
            border-top: 1px solid #444;
            display: flex;
            justify-content: space-around;
            font-size: 12px;
        }
        
        .stat-item {
            display: flex;
            flex-direction: column;
            align-items: center;
        }
        
        .stat-label {
            color: #888;
            margin-bottom: 5px;
        }
        
        .stat-value {
            font-size: 16px;
            font-weight: bold;
            color: #00bcd4;
        }
        
        @media (max-width: 768px) {
            .header {
                flex-direction: column;
                align-items: flex-start;
            }
            
            .controls {
                width: 100%;
                justify-content: space-between;
            }
            
            .stats {
                flex-wrap: wrap;
            }
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="title">🎥 FFDDAS Live Viewer</div>
        <div class="status-container">
            <div class="status-indicator">
                <div class="status-dot disconnected" id="statusDot"></div>
                <span id="statusText">Disconnected</span>
            </div>
        </div>
        <div class="controls">
            <select id="refreshRate">
                <option value="100">Fast (100ms)</option>
                <option value="250" selected>Normal (250ms)</option>
                <option value="500">Slow (500ms)</option>
                <option value="1000">Very Slow (1s)</option>
            </select>
            <button id="refreshBtn">Refresh Now</button>
        </div>
    </div>
    
    <div class="viewer-container">
        <canvas id="canvas"></canvas>
    </div>
    
    <div class="stats">
        <div class="stat-item">
            <div class="stat-label">FPS</div>
            <div class="stat-value" id="fps">0</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">Frames</div>
            <div class="stat-value" id="frameCount">0</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">Errors</div>
            <div class="stat-value" id="errorCount">0</div>
        </div>
        <div class="stat-item">
            <div class="stat-label">Resolution</div>
            <div class="stat-value" id="resolution">-</div>
        </div>
    </div>

    <script>
        // TypeScript-style class-based implementation in vanilla JS
        class FrameViewer {
            constructor() {
                this.canvas = document.getElementById('canvas');
                this.ctx = this.canvas.getContext('2d');
                this.statusDot = document.getElementById('statusDot');
                this.statusText = document.getElementById('statusText');
                this.fpsElement = document.getElementById('fps');
                this.frameCountElement = document.getElementById('frameCount');
                this.errorCountElement = document.getElementById('errorCount');
                this.resolutionElement = document.getElementById('resolution');
                this.refreshRateSelect = document.getElementById('refreshRate');
                this.refreshBtn = document.getElementById('refreshBtn');
                
                this.frameCount = 0;
                this.errorCount = 0;
                this.lastFrameTime = Date.now();
                this.fps = 0;
                this.intervalId = null;
                this.currentImage = null;
                this.isConnected = false;
                
                this.init();
            }
            
            init() {
                this.refreshBtn.addEventListener('click', () => this.fetchFrame());
                this.refreshRateSelect.addEventListener('change', () => this.updateRefreshRate());
                
                // Initial canvas size
                this.resizeCanvas();
                window.addEventListener('resize', () => this.resizeCanvas());
                
                this.updateRefreshRate();
            }
            
            resizeCanvas() {
                const container = this.canvas.parentElement;
                const maxWidth = container.clientWidth - 40;
                const maxHeight = container.clientHeight - 40;
                
                if (this.currentImage) {
                    const aspectRatio = this.currentImage.width / this.currentImage.height;
                    let width = maxWidth;
                    let height = width / aspectRatio;
                    
                    if (height > maxHeight) {
                        height = maxHeight;
                        width = height * aspectRatio;
                    }
                    
                    this.canvas.width = width;
                    this.canvas.height = height;
                    this.drawImage();
                } else {
                    this.canvas.width = Math.min(640, maxWidth);
                    this.canvas.height = Math.min(480, maxHeight);
                }
            }
            
            updateRefreshRate() {
                if (this.intervalId) {
                    clearInterval(this.intervalId);
                }
                
                const rate = parseInt(this.refreshRateSelect.value);
                this.intervalId = setInterval(() => this.fetchFrame(), rate);
                this.fetchFrame(); // Immediate fetch
            }
            
            async fetchFrame() {
                try {
                    const response = await fetch('/frame?' + Date.now(), {
                        cache: 'no-store',
                        signal: AbortSignal.timeout(5000)
                    });
                    
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status);
                    }
                    
                    const blob = await response.blob();
                    const img = await this.loadImage(blob);
                    
                    this.currentImage = img;
                    this.frameCount++;
                    this.updateStatus(true);
                    this.updateStats();
                    this.resizeCanvas();
                    
                } catch (error) {
                    console.error('Frame fetch error:', error);
                    this.errorCount++;
                    this.updateStatus(false);
                    this.updateStats();
                }
            }
            
            loadImage(blob) {
                return new Promise((resolve, reject) => {
                    const img = new Image();
                    img.onload = () => resolve(img);
                    img.onerror = reject;
                    img.src = URL.createObjectURL(blob);
                });
            }
            
            drawImage() {
                if (!this.currentImage) return;
                
                this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
                this.ctx.drawImage(this.currentImage, 0, 0, this.canvas.width, this.canvas.height);
            }
            
            updateStatus(connected) {
                this.isConnected = connected;
                
                if (connected) {
                    this.statusDot.className = 'status-dot connected';
                    this.statusText.textContent = 'Connected';
                } else {
                    this.statusDot.className = 'status-dot disconnected';
                    this.statusText.textContent = 'Disconnected';
                }
            }
            
            updateStats() {
                const now = Date.now();
                const elapsed = (now - this.lastFrameTime) / 1000;
                
                if (elapsed > 0) {
                    this.fps = Math.round(1 / elapsed);
                    this.lastFrameTime = now;
                }
                
                this.fpsElement.textContent = this.fps;
                this.frameCountElement.textContent = this.frameCount;
                this.errorCountElement.textContent = this.errorCount;
                
                if (this.currentImage) {
                    this.resolutionElement.textContent = 
                        this.currentImage.width + 'x' + this.currentImage.height;
                }
            }
        }
        
        // Initialize viewer when DOM is ready
        document.addEventListener('DOMContentLoaded', () => {
            new FrameViewer();
        });
    </script>
</body>
</html>
"""
    
    // Store the latest frame
    private val latestFrame = AtomicReference<Bitmap?>(null)
    private var servedFrames = 0L

    private fun toJson(map: Map<String, Any?>): String = buildString {
        append("{")
        var first = true
        for ((k,v) in map) {
            if (!first) append(",")
            append("\"").append(k).append("\":")
            when (v) {
                null -> append("null")
                is Number, is Boolean -> append(v.toString())
                else -> append("\"").append(v.toString().replace("\"","\\\"")).append("\"")
            }
            first = false
        }
        append("}")
    }
    
    /**
     * Update the latest frame to be served to web clients
     */
    fun updateFrame(bitmap: Bitmap) {
        // Apply simple filter transformations similar to app (lightweight)
        val processed = when(filterMode) {
            FilterMode.NONE -> bitmap
            FilterMode.GRAYSCALE -> bitmap.toGrayscale()
            FilterMode.EDGE_DETECTION -> bitmap.toEdge()
        }
        latestFrame.set(processed)
    }

    // Simple bitmap filters (avoid heavy OpenCV in server thread)
    private fun Bitmap.toGrayscale(): Bitmap {
        val w = width
        val h = height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val gray = (0.299*r + 0.587*g + 0.114*b).toInt()
                out.setPixel(x, y, (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)
            }
        }
        return out
    }

    private fun Bitmap.toEdge(): Bitmap {
        // Very lightweight Sobel approximation
        val w = width
        val h = height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Precompute grayscale
        val gray = IntArray(w*h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                gray[y*w + x] = (0.299*r + 0.587*g + 0.114*b).toInt()
            }
        }
        val gx = intArrayOf(-1,0,1,-2,0,2,-1,0,1)
        val gy = intArrayOf(-1,-2,-1,0,0,0,1,2,1)
        for (y in 1 until h-1) {
            for (x in 1 until w-1) {
                var sx = 0
                var sy = 0
                var k = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = gray[(y+dy)*w + (x+dx)]
                        sx += v * gx[k]
                        sy += v * gy[k]
                        k++
                    }
                }
                val mag = kotlin.math.min(255, kotlin.math.abs(sx) + kotlin.math.abs(sy))
                out.setPixel(x, y, (0xFF shl 24) or (mag shl 16) or (mag shl 8) or mag)
            }
        }
        return out
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return try {
            when {
                uri == "/" || uri == "/index.html" -> {
                    val htmlWithFilters = viewerHtml.replace("</div>\n    <div class=\"viewer-container\">", "</div>\n    <div style=\"padding:10px;background:#222;display:flex;gap:10px;\">Filters: <button onclick=\"setFilter('NONE')\">None</button><button onclick=\"setFilter('GRAYSCALE')\">Grayscale</button><button onclick=\"setFilter('EDGE_DETECTION')\">Edge</button><button onclick=\"capturePhoto()\">Capture</button><button onclick=\"switchCam()\">Switch Cam</button></div>\n    <div class=\"viewer-container\">")
                    val augmented = htmlWithFilters.replace("</script>", "function setFilter(m){fetch('/api/setFilter?mode='+m).then(()=>console.log('filter '+m));}\nfunction capturePhoto(){fetch('/api/capture').then(r=>r.json()).then(j=>console.log(j));}\nfunction switchCam(){fetch('/api/switchCamera').then(r=>r.json()).then(j=>console.log(j));}\nfunction refreshStatus(){fetch('/api/status').then(r=>r.json()).then(j=>console.log(j));}\n</script>")
                    Log.d(TAG, "Serving viewer page")
                    newFixedLengthResponse(Response.Status.OK, "text/html", augmented)
                }
                uri.startsWith("/api/capture") -> {
                    val ok = captureCallback?.invoke() ?: false
                    newFixedLengthResponse(Response.Status.OK, "application/json", toJson(mapOf("started" to ok)))
                }
                uri.startsWith("/api/switchCamera") -> {
                    val ok = switchCameraCallback?.invoke() ?: false
                    newFixedLengthResponse(Response.Status.OK, "application/json", toJson(mapOf("switched" to ok)))
                }
                uri.startsWith("/api/status") -> {
                    val base = statusCallback?.invoke() ?: emptyMap()
                    val extra = mapOf("serverFilter" to filterMode.name, "servedFrames" to servedFrames)
                    newFixedLengthResponse(Response.Status.OK, "application/json", toJson(base + extra))
                }
                uri.startsWith("/api/gallery") -> {
                    val list = listGalleryCallback?.invoke() ?: emptyList()
                    val json = "[\"" + list.joinToString("\",\"") { it.replace("\"", "\\\"") } + "\"]"
                    newFixedLengthResponse(Response.Status.OK, "application/json", json)
                }
                uri.startsWith("/api/setFilter") || uri.startsWith("/setFilter") -> {
                    val params = session.parameters["mode"]?.firstOrNull()
                    val mode = params ?: ""
                    val callbackAccepted = setFilterCallback?.invoke(mode) ?: false
                    if (callbackAccepted) {
                        filterMode = when(mode.uppercase()) {
                            "GRAYSCALE", "GRAY" -> FilterMode.GRAYSCALE
                            "EDGE_DETECTION", "EDGE" -> FilterMode.EDGE_DETECTION
                            else -> FilterMode.NONE
                        }
                    } else {
                        filterMode = when(mode.uppercase()) {
                            "GRAYSCALE" -> FilterMode.GRAYSCALE
                            "EDGE_DETECTION" -> FilterMode.EDGE_DETECTION
                            else -> filterMode
                        }
                    }
                    Log.i(TAG, "Filter mode changed to $filterMode via web")
                    newFixedLengthResponse(Response.Status.OK, "application/json", toJson(mapOf("mode" to filterMode.name, "accepted" to callbackAccepted)))
                }
                uri.startsWith("/frame") -> {
                    val frame = latestFrame.get()
                    if (frame != null) {
                        val outputStream = ByteArrayOutputStream()
                        frame.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val imageBytes = outputStream.toByteArray()
                        servedFrames++
                        Log.d(TAG, "Serving frame: ${frame.width}x${frame.height}, ${imageBytes.size} bytes (served=$servedFrames)")
                        val response = newFixedLengthResponse(
                            Response.Status.OK,
                            "image/jpeg",
                            ByteArrayInputStream(imageBytes),
                            imageBytes.size.toLong()
                        )
                        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        response.addHeader("Pragma", "no-cache")
                        response.addHeader("Expires", "0")
                        response
                    } else {
                        Log.w(TAG, "No frame available")
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "No frame available")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown URI: $uri")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request: ${e.message}", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Internal server error: ${e.message}")
        }
    }
    
    /**
     * Start the web server
     */
    fun startServer(): Boolean {
        return try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.i(TAG, "Web server started on port $listeningPort")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start web server: ${e.message}", e)
            false
        }
    }
    
    /**
     * Stop the web server
     */
    fun stopServer() {
        try {
            stop()
            Log.i(TAG, "Web server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping web server: ${e.message}", e)
        }
    }
}
