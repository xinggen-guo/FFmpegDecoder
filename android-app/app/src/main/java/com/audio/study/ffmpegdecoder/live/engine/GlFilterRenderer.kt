package com.audio.study.ffmpegdecoder.live.engine

import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Camera → cameraSurface (SurfaceTexture / OES)
 *
 * GlFilterRenderer:
 *   - waits for camera frames (SurfaceTexture.OnFrameAvailableListener)
 *   - updates the OES texture
 *   - draws a filtered frame into:
 *       1) encoderSurface (MediaCodec input)
 *       2) previewSurface (TextureView surface)
 *
 * If something goes wrong, it logs & continues instead of killing the loop,
 * to avoid "invisible" preview.
 */
class GlFilterRenderer(
    private val encoderSurface: Surface,
    private val previewSurface: Surface?,   // can be null if no preview
    private val width: Int,
    private val height: Int
) {

    // Surface that Camera2 will target
    lateinit var cameraSurface: Surface
        private set

    private lateinit var surfaceTexture: SurfaceTexture

    private val running = AtomicBoolean(false)
    private var renderThread: Thread? = null

    // Signal when cameraSurface is ready
    private val cameraSurfaceReady = CountDownLatch(1)

    // Frame sync objects
    private val frameSyncObject = Object()
    @Volatile
    private var frameAvailable = false

    // EGL objects
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglEncoderSurface: EGLSurface? = null
    private var eglPreviewSurface: EGLSurface? = null

    // GL program / handles
    private var oesTextureId: Int = 0
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var uTexHandle: Int = 0
    private var uBrightnessHandle: Int = 0
    private var uSaturationHandle: Int = 0
    private var uTexelSizeHandle: Int = 0

    // Fullscreen quad
    private val vertexData: FloatBuffer =
        ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(
                floatArrayOf(
                    -1f, -1f,
                    1f, -1f,
                    -1f,  1f,
                    1f,  1f
                )
            )
            position(0)
        }

    private val texCoordData: FloatBuffer =
        ByteBuffer.allocateDirect(4 * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            // standard orientation for TextureView
            put(
                floatArrayOf(
                    0f, 1f,
                    1f, 1f,
                    0f, 0f,
                    1f, 0f
                )
            )
            position(0)
        }

    fun start() {
        if (running.get()) return
        running.set(true)

        renderThread = Thread {
            try {
                initEglAndGl()
                // cameraSurface is now created
                cameraSurfaceReady.countDown()

                renderLoop()
            } finally {
                releaseGl()
            }
        }.apply { start() }
    }

    fun stop() {
        running.set(false)
        synchronized(frameSyncObject) {
            // wake up renderLoop if waiting
            frameSyncObject.notifyAll()
        }
        try {
            renderThread?.join(500)
        } catch (_: Exception) {
        }
        renderThread = null
    }

    /**
     * Wait until cameraSurface is ready (created on GL thread).
     */
    fun getCameraSurfaceBlocking(timeoutMs: Long = 1000): Surface? {
        cameraSurfaceReady.await(timeoutMs, TimeUnit.MILLISECONDS)
        return if (::cameraSurface.isInitialized) cameraSurface else null
    }

    // --------------------------------------------------
    // Render loop
    // --------------------------------------------------

    private fun renderLoop() {
        while (running.get()) {
            // 1) wait for a new frame
            synchronized(frameSyncObject) {
                while (running.get() && !frameAvailable) {
                    try {
                        frameSyncObject.wait()
                    } catch (_: InterruptedException) {
                    }
                }
                if (!running.get()) {}
                frameAvailable = false
            }

            // 2) update the OES texture with the latest camera frame
            try {
                surfaceTexture.updateTexImage()
            } catch (e: Exception) {
                // Do NOT break the loop – just continue.
                // Early exceptions here would otherwise kill rendering and make preview black.
                continue
            }

            // 3) draw to encoder
            drawToSurface(eglEncoderSurface)

            // 4) draw to preview
            if (eglPreviewSurface != null) {
                drawToSurface(eglPreviewSurface)
            }
        }
    }

    // --------------------------------------------------
    // EGL + GL initialization
    // --------------------------------------------------

    private fun initEglAndGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(
            eglDisplay, attribList, 0,
            configs, 0, configs.size, numConfigs, 0
        )
        val eglConfig = configs[0] ?: throw RuntimeException("Unable to find EGLConfig")

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
        )
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)

        eglEncoderSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, encoderSurface, surfaceAttribs, 0
        )
        if (eglEncoderSurface == null || eglEncoderSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create encoder EGLSurface")
        }

        if (previewSurface != null) {
            eglPreviewSurface = EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, previewSurface, surfaceAttribs, 0
            )
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglEncoderSurface, eglEncoderSurface, eglContext)) {
            throw RuntimeException("Failed to make EGL context current")
        }

        initGlObjects()
    }

    private fun initGlObjects() {
        // external OES texture for camera
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTextureId = tex[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // SurfaceTexture for camera input
        surfaceTexture = SurfaceTexture(oesTextureId)
        surfaceTexture.setDefaultBufferSize(width, height)
        cameraSurface = Surface(surfaceTexture)

        // Listen for new frames
        surfaceTexture.setOnFrameAvailableListener {
            synchronized(frameSyncObject) {
                frameAvailable = true
                frameSyncObject.notifyAll()
            }
        }

        // Shaders: pass-through + mild brightness/saturation; blur can be tuned later
        val vsSrc = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fsSrc = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            uniform float uBrightness;
            uniform float uSaturation;
            uniform vec2 uTexelSize; // currently optional

            void main() {
                // basic sample
                vec4 color = texture2D(uTexture, vTexCoord);

                // (Optional) VERY light 1D blur horizontally
                // Comment this out completely if you want pure pass-through.
                vec4 blur = (
                    texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0)) +
                    texture2D(uTexture, vTexCoord - vec2(uTexelSize.x, 0.0))
                ) * 0.5;

                vec4 smooth = mix(color, blur, 0.2); // small smoothing

                // brightness
                smooth.rgb += uBrightness;

                // saturation
                float gray = dot(smooth.rgb, vec3(0.299, 0.587, 0.114));
                smooth.rgb = mix(vec3(gray), smooth.rgb, uSaturation);

                gl_FragColor = smooth;
            }
        """.trimIndent()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Could not link program: $log")
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexHandle = GLES20.glGetUniformLocation(program, "uTexture")
        uBrightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
        uSaturationHandle = GLES20.glGetUniformLocation(program, "uSaturation")
        uTexelSizeHandle = GLES20.glGetUniformLocation(program, "uTexelSize")
    }

    private fun drawToSurface(surface: EGLSurface?) {
        if (surface == null) return
        val display = eglDisplay ?: return
        val context = eglContext ?: return

        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexData)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordData)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glUniform1i(uTexHandle, 0)

        // Very small effect by default – tune later
        GLES20.glUniform1f(uBrightnessHandle, 0.05f)   // slightly brighter
        GLES20.glUniform1f(uSaturationHandle, 1.1f)    // slightly more saturation
        GLES20.glUniform2f(
            uTexelSizeHandle,
            1.0f / width.toFloat(),
            1.0f / height.toFloat()
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(display, surface, System.nanoTime())
        EGL14.eglSwapBuffers(display, surface)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun releaseGl() {
        try { surfaceTexture.release() } catch (_: Exception) {}
        try { cameraSurface.release() } catch (_: Exception) {}

        GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
        if (program != 0) {
            GLES20.glDeleteProgram(program)
        }

        val display = eglDisplay
        if (display != null && display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            eglEncoderSurface?.let { EGL14.eglDestroySurface(display, it) }
            eglPreviewSurface?.let { EGL14.eglDestroySurface(display, it) }
            eglContext?.let { EGL14.eglDestroyContext(display, it) }
            EGL14.eglTerminate(display)
        }
        eglDisplay = null
        eglContext = null
        eglEncoderSurface = null
        eglPreviewSurface = null
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Could not compile shader $type: $log")
        }
        return shader
    }
}