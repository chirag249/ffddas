package com.example.ffddas

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLImageRenderer - Custom OpenGL ES 2.0 renderer that can display static and live
 * processed images coming from the OpenCV pipeline.
 * Requirements implemented:
 * 1. Shader implementation (compilation, multiple programs for modes)
 * 2. Texture management (volatile updates for live feed)
 * 3. Matrix transformations (MVP with aspect ratio + orientation)
 * 4. Performance monitoring (FPS)
 * 5. Resource management (cleanup of GL objects)
 */
class GLImageRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "GLImageRenderer"

        // Simple textured quad vertex shader
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;\n
            attribute vec2 aTexCoord;\n
            uniform mat4 uMVP;\n
            varying vec2 vTexCoord;\n
            void main(){\n
                gl_Position = uMVP * aPosition;\n
                vTexCoord = aTexCoord;\n
            }\n
        """

        // Fragment shader (normal mode)
        private const val FRAGMENT_SHADER_NORMAL = """
            precision mediump float;\n
            varying vec2 vTexCoord;\n
            uniform sampler2D uTexture;\n
            void main(){\n
                gl_FragColor = texture2D(uTexture, vTexCoord);\n
            }\n
        """

        // Fragment shader (edge highlight - expects RGBA where edges are white)
        private const val FRAGMENT_SHADER_EDGE = """
            precision mediump float;\n
            varying vec2 vTexCoord;\n
            uniform sampler2D uTexture;\n
            void main(){\n
                vec4 c = texture2D(uTexture, vTexCoord);\n
                // simple contrast/edge emphasis
                float intensity = (c.r + c.g + c.b) / 3.0;\n
                if(intensity > 0.9) {\n
                   gl_FragColor = vec4(1.0,1.0,1.0,1.0);\n
                } else {\n
                   gl_FragColor = c;\n
                }\n
            }\n
        """
    }

    // Quad geometry (X,Y,Z; U,V)
    private val vertexData = floatArrayOf(
        -1f, -1f, 0f, 0f, 1f,
        1f, -1f, 0f, 1f, 1f,
        -1f, 1f, 0f, 0f, 0f,
        1f, 1f, 0f, 1f, 0f
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply {
            put(vertexData)
            position(0)
        }

    private var programNormal = 0
    private var programEdge = 0
    @Volatile private var activeProgram = 0

    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uMvpLoc = 0
    private var uTexLoc = 0

    private var textureId = 0
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0

    // Matrices
    private val mvp = FloatArray(16)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)

    // Orientation flag (0,90,180,270 degrees)
    @Volatile var deviceRotationDegrees: Int = 0

    // FPS tracking
    private var lastTimeNs = 0L
    private var frameCount = 0
    @Volatile var currentFps = 0f

    // Pending texture update
    private val pendingUpdate = AtomicBoolean(false)
    private var pendingPixelBuffer: ByteBuffer? = null
    private var pendingFormat: Int = GLES20.GL_RGBA // default

    // Visualization modes
    enum class Mode { NORMAL, EDGE }
    @Volatile var mode: Mode = Mode.NORMAL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f,0f,0f,1f)
        programNormal = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_NORMAL)
        programEdge = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_EDGE)
        activeProgram = programNormal
        setupBuffers()
        createTexture()
        Matrix.setIdentityM(model,0)
        Matrix.setIdentityM(view,0)
        Matrix.setLookAtM(view,0,0f,0f,1f,0f,0f,0f,0f,1f,0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0,0,width,height)
        updateMvp()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Update FPS
        val now = System.nanoTime()
        if (lastTimeNs == 0L) lastTimeNs = now
        frameCount++
        if (now - lastTimeNs >= 1_000_000_000L) { // 1 second
            currentFps = frameCount * 1_000_000_000f / (now - lastTimeNs)
            frameCount = 0
            lastTimeNs = now
            Log.d(TAG, "FPS: $currentFps")
        }

        // Switch program if mode changed
        activeProgram = when(mode) {
            Mode.NORMAL -> programNormal
            Mode.EDGE -> programEdge
        }
        GLES20.glUseProgram(activeProgram)
        resolveLocations(activeProgram)

        // Upload pending texture update
        if (pendingUpdate.compareAndSet(true, false)) {
            pendingPixelBuffer?.let { buf ->
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    pendingFormat,
                    imageWidth,
                    imageHeight,
                    0,
                    pendingFormat,
                    GLES20.GL_UNSIGNED_BYTE,
                    buf
                )
                updateMvp() // aspect ratio may change
            }
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        // Set attributes
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc,3,GLES20.GL_FLOAT,false,5*4,vertexBuffer)
        vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        GLES20.glVertexAttribPointer(aTexCoordLoc,2,GLES20.GL_FLOAT,false,5*4,vertexBuffer)

        GLES20.glUniformMatrix4fv(uMvpLoc,1,false,mvp,0)
        GLES20.glUniform1i(uTexLoc,0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
    }

    private fun setupBuffers() {
        // No VBO use for brevity; could be added for performance
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val vsId = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val fsId = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vsId)
        GLES20.glAttachShader(program, fsId)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus,0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG,"Program link error: $log")
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val id = GLES20.glCreateShader(type)
        GLES20.glShaderSource(id, source)
        GLES20.glCompileShader(id)
        val status = IntArray(1)
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status,0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(id)
            Log.e(TAG, "Shader compile error: $log")
            GLES20.glDeleteShader(id)
            return 0
        }
        return id
    }

    private fun resolveLocations(program: Int) {
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uMvpLoc = GLES20.glGetUniformLocation(program, "uMVP")
        uTexLoc = GLES20.glGetUniformLocation(program, "uTexture")
    }

    private fun createTexture() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids,0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun updateMvp() {
        if (surfaceWidth == 0 || surfaceHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            Matrix.setIdentityM(mvp,0)
            return
        }
        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
        Matrix.setIdentityM(model,0)
        Matrix.setIdentityM(view,0)
        Matrix.setLookAtM(view,0,0f,0f,1f,0f,0f,0f,0f,1f,0f)
        if (surfaceAspect > imageAspect) {
            val scaleX = imageAspect / surfaceAspect
            Matrix.scaleM(model,0,scaleX,1f,1f)
        } else {
            val scaleY = surfaceAspect / imageAspect
            Matrix.scaleM(model,0,1f,scaleY,1f)
        }
        when(deviceRotationDegrees) {
            90 -> Matrix.rotateM(model,0,90f,0f,0f,1f)
            180 -> Matrix.rotateM(model,0,180f,0f,0f,1f)
            270 -> Matrix.rotateM(model,0,270f,0f,0f,1f)
        }
        Matrix.orthoM(proj,0,-1f,1f,-1f,1f,0.1f,10f)
        val temp = FloatArray(16)
        Matrix.multiplyMM(temp,0,view,0,model,0)
        Matrix.multiplyMM(mvp,0,proj,0,temp,0)
    }

    /**
     * Update texture with RGBA data coming from OpenCV pipeline.
     * Thread-safe; call from any thread. Data copied into direct ByteBuffer.
     */
    fun updateTexture(rgbaBytes: ByteArray, width: Int, height: Int) {
        if (width <=0 || height <=0 || rgbaBytes.size < width*height*4) {
            Log.e(TAG, "updateTexture: invalid dimensions or buffer")
            return
        }
        imageWidth = width
        imageHeight = height
        if (pendingPixelBuffer == null || pendingPixelBuffer!!.capacity() < rgbaBytes.size) {
            pendingPixelBuffer = ByteBuffer.allocateDirect(rgbaBytes.size).order(ByteOrder.nativeOrder())
        }
        pendingPixelBuffer!!.position(0)
        pendingPixelBuffer!!.put(rgbaBytes)
        pendingPixelBuffer!!.position(0)
        pendingFormat = GLES20.GL_RGBA
        pendingUpdate.set(true)
    }

    /** Switch visualization mode */
    fun setVisualizationMode(newMode: Mode) { mode = newMode }

    /** Set device rotation in degrees (0,90,180,270) */
    fun setDeviceRotation(deg: Int) { deviceRotationDegrees = deg }

    fun cleanup() {
        if (programNormal != 0) GLES20.glDeleteProgram(programNormal)
        if (programEdge != 0) GLES20.glDeleteProgram(programEdge)
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, IntBuffer.wrap(intArrayOf(textureId)))
        }
        programNormal = 0
        programEdge = 0
        textureId = 0
        pendingPixelBuffer = null
    }
}
