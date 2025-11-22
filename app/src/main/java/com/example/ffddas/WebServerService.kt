package com.example.ffddas

import android.graphics.Bitmap
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Embedded HTTP server for serving processed camera frames to web clients
 */
class WebServerService(port: Int = 8080) : NanoHTTPD(port) {
    
    companion object {
        private const val TAG = "WebServerService"
    }
    
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
    
    /**
     * Update the latest frame to be served to web clients
     */
    fun updateFrame(bitmap: Bitmap) {
        latestFrame.set(bitmap)
    }
    
    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        
        return try {
            when {
                uri == "/" || uri == "/index.html" -> {
                    // Serve the viewer HTML page
                    Log.d(TAG, "Serving viewer page")
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "text/html",
                        viewerHtml
                    )
                }
                
                uri.startsWith("/frame") -> {
                    // Serve the latest frame as JPEG
                    val frame = latestFrame.get()
                    
                    if (frame != null) {
                        val outputStream = ByteArrayOutputStream()
                        frame.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                        val imageBytes = outputStream.toByteArray()
                        
                        Log.d(TAG, "Serving frame: ${frame.width}x${frame.height}, ${imageBytes.size} bytes")
                        
                        val response = newFixedLengthResponse(
                            Response.Status.OK,
                            "image/jpeg",
                            ByteArrayInputStream(imageBytes),
                            imageBytes.size.toLong()
                        )
                        
                        // Add headers to prevent caching
                        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate")
                        response.addHeader("Pragma", "no-cache")
                        response.addHeader("Expires", "0")
                        
                        response
                    } else {
                        Log.w(TAG, "No frame available")
                        newFixedLengthResponse(
                            Response.Status.NOT_FOUND,
                            "text/plain",
                            "No frame available"
                        )
                    }
                }
                
                else -> {
                    Log.w(TAG, "Unknown URI: $uri")
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "Not found"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error serving request: ${e.message}", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "Internal server error: ${e.message}"
            )
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
