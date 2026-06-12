package com.generationcamera.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.generationcamera.engine.EffectParams
import com.generationcamera.engine.EraEngine
import com.generationcamera.engine.Eras
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 4-pass GLES 3.0 pipeline (see ARCHITECTURE.md):
 *   P1 camera OES -> scene FBO (gate weave + down-res)
 *   P2 scene -> half-res separable Gaussian blur
 *   P3 era über-shader -> screen (or full-res FBO for stills)
 *   P4 frame overlay quad (alpha blend)
 *
 * Orientation convention: every FBO stores the image with row 0 = image
 * BOTTOM (shader uv.y = 0 is the bottom). The preview gets this for free from
 * the SurfaceTexture transform; the still path forces it with a flip matrix
 * on input and un-flips after glReadPixels.
 */
class EraRenderer(
    private val context: Context,
    private val view: GLSurfaceView,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    @Volatile var params: EffectParams = EraEngine.compute(8, 5, forStill = false)

    private val mainHandler = Handler(Looper.getMainLooper())

    private var progOes = 0
    private var progCopy = 0
    private var progBlur = 0
    private var progEra = 0
    private lateinit var quad: Quad

    private var oesTex = 0
    private var surfaceTexture: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)
    private val identityMatrix = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
    private val flipYMatrix = floatArrayOf(   // uv' = (u, 1 - v)
        1f, 0f, 0f, 0f,
        0f, -1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 1f, 0f, 1f,
    )

    private var viewW = 0
    private var viewH = 0
    private var sceneFbo: Fbo? = null
    private var blurA: Fbo? = null
    private var blurB: Fbo? = null

    private lateinit var lutTex: IntArray
    private lateinit var dustTex: IntArray
    private lateinit var leakTex: IntArray
    private var maxTexSize = 4096

    private var startMs = 0L
    private var frameCount = 0L
    private var loggedFirstFrame = false

    // ------------------------------------------------------------ lifecycle

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vert = GlUtilsGc.loadAssetText(context, "shaders/fullscreen.vert")
        progOes = GlUtilsGc.buildProgram(vert, GlUtilsGc.loadAssetText(context, "shaders/oes_copy.frag"))
        progCopy = GlUtilsGc.buildProgram(vert, GlUtilsGc.loadAssetText(context, "shaders/copy.frag"))
        progBlur = GlUtilsGc.buildProgram(vert, GlUtilsGc.loadAssetText(context, "shaders/blur.frag"))
        progEra = GlUtilsGc.buildProgram(vert, GlUtilsGc.loadAssetText(context, "shaders/era.frag"))
        quad = Quad()

        val size = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, size, 0)
        maxTexSize = max(2048, size[0])

        lutTex = IntArray(Eras.all.size) { i ->
            val lut = context.assets.open(Eras.all[i].lutAsset).bufferedReader()
                .use { CubeLutParser.parse(it) }
            GlUtilsGc.createLutTexture(lut)
        }
        dustTex = IntArray(3) { i ->
            GlUtilsGc.createTexture(GlUtilsGc.loadAssetBitmap(context, "overlays/dust_$i.png"), recycle = true)
        }
        leakTex = IntArray(2) { i ->
            GlUtilsGc.createTexture(GlUtilsGc.loadAssetBitmap(context, "overlays/leak_$i.png"), recycle = true)
        }
        loggedFirstFrame = false

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 1f)

        // (Re)create the camera input; on context loss the old one is dead.
        surfaceTexture?.release()
        oesTex = GlUtilsGc.createOesTexture()
        val st = SurfaceTexture(oesTex)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        startMs = SystemClock.elapsedRealtime()
        Log.i(TAG, "Surface created; maxTexSize=$maxTexSize")
        mainHandler.post { onSurfaceTextureReady(st) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width
        viewH = height
        releaseFbos()
        Log.i(TAG, "Surface changed: ${width}x$height")
    }

    override fun onFrameAvailable(st: SurfaceTexture?) {
        view.requestRender()
    }

    // ------------------------------------------------------------ preview

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        if (viewW == 0 || viewH == 0) return
        // Rendered continuously: latch the newest camera frame each vsync.
        // updateTexImage with no new frame is a harmless no-op, so we don't
        // depend on onFrameAvailable plumbing at all.
        try {
            st.updateTexImage()
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateTexImage failed", e)
            return
        }
        st.getTransformMatrix(texMatrix)
        frameCount++
        if (!loggedFirstFrame) {
            loggedFirstFrame = true
            Log.i(TAG, "First preview frame drawn (${viewW}x$viewH)")
        }

        val p = params
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000f
        ensureFbos(viewW, viewH, p.resScale)
        val scene = sceneFbo!!; val ba = blurA!!; val bb = blurB!!

        // P1: OES -> scene (gate weave applied as UV offset)
        scene.bind()
        GLES30.glUseProgram(progOes)
        GLES30.glUniformMatrix4fv(loc(progOes, "uTexMatrix"), 1, false, texMatrix, 0)
        val wx = (sin(t * 2f * PI.toFloat() * 1.2f) + 0.4f * sin(t * 7.3f)) * p.weave
        val wy = cos(t * 2f * PI.toFloat() * 0.9f) * p.weave * 0.7f
        GLES30.glUniform2f(loc(progOes, "uWeave"), wx, wy)
        bindTex(0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTex, progOes, "uTexture")
        quad.draw()

        blurPasses(scene, ba, bb)
        eraPass(p, scene.texture, bb.texture, target = null, outW = viewW, outH = viewH,
            time = t, seed = (frameCount % 512L).toFloat())
        GlUtilsGc.checkError("onDrawFrame")
    }

    // ------------------------------------------------------------ still capture

    /**
     * Renders a captured still through the exact same programs/uniforms on
     * the GL thread. Call via glSurfaceView.queueEvent. Returns the processed
     * bitmap (caller saves it off-thread).
     */
    fun processStill(src: Bitmap, params: EffectParams): Bitmap {
        // Cap the working resolution: large mobile GPUs partially drop tiles
        // under the memory pressure of a 12 MP multi-pass chain. ~5 MP output
        // keeps the whole chain well inside budget.
        var w = src.width; var h = src.height
        val maxDim = min(maxTexSize, STILL_MAX_DIM)
        if (max(w, h) > maxDim) {
            val s = maxDim.toFloat() / max(w, h)
            w = (w * s).toInt(); h = (h * s).toInt()
        }
        Log.i(TAG, "processStill: src=${src.width}x${src.height} -> ${w}x$h")
        val input = if (w != src.width) Bitmap.createScaledBitmap(src, w, h, true) else src
        // grain cells are sized in output pixels; scale so the still matches
        // the preview's apparent grain (preview is ~1080 px wide)
        val p = params.copy(grainSize = params.grainSize * (w / 1080f).coerceAtLeast(1f))

        val srcTex = GlUtilsGc.createTexture(input, recycle = input !== src)
        val sw = max(8, (w * p.resScale).toInt())
        val sh = max(8, (h * p.resScale).toInt())
        val scene = Fbo(sw, sh)
        val ba = Fbo(max(4, sw / 2), max(4, sh / 2))
        val bb = Fbo(max(4, sw / 2), max(4, sh / 2))
        val out = Fbo(w, h)

        // P1: bitmap -> scene, flipped so row0 = image bottom (preview parity)
        scene.bind()
        GLES30.glUseProgram(progCopy)
        GLES30.glUniformMatrix4fv(loc(progCopy, "uTexMatrix"), 1, false, flipYMatrix, 0)
        GLES30.glUniform2f(loc(progCopy, "uWeave"), 0f, 0f)
        bindTex(0, GLES30.GL_TEXTURE_2D, srcTex, progCopy, "uTexture")
        quad.draw()
        GlUtilsGc.checkError("still P1")

        blurPasses(scene, ba, bb)
        GlUtilsGc.checkError("still P2")
        eraPass(p, scene.texture, bb.texture, target = out, outW = w, outH = h,
            time = 4.7f, seed = 7.31f)   // fixed time/seed: deterministic still
        GlUtilsGc.checkError("still P3")

        // Free everything but the result target before the big readback, and
        // force the GPU to finish all tiles before reading.
        GLES30.glDeleteTextures(1, intArrayOf(srcTex), 0)
        scene.release(); ba.release(); bb.release()
        GLES30.glFinish()

        // Read back (rows arrive bottom-up == image bottom first) and un-flip.
        val buf = ByteBuffer.allocateDirect(w * h * 4)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, out.framebuffer)
        GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GlUtilsGc.checkError("still readPixels")
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        flipRowsInPlace(buf, w, h)
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        buf.position(0)
        result.copyPixelsFromBuffer(buf)

        out.release()
        return result
    }

    // ------------------------------------------------------------ passes

    private fun blurPasses(scene: Fbo, ba: Fbo, bb: Fbo) {
        GLES30.glUseProgram(progBlur)
        GLES30.glUniformMatrix4fv(loc(progBlur, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform2f(loc(progBlur, "uWeave"), 0f, 0f)
        ba.bind()
        GLES30.glUniform2f(loc(progBlur, "uDirection"), 1f / ba.width, 0f)
        bindTex(0, GLES30.GL_TEXTURE_2D, scene.texture, progBlur, "uTexture")
        quad.draw()
        bb.bind()
        GLES30.glUniform2f(loc(progBlur, "uDirection"), 0f, 1f / bb.height)
        bindTex(0, GLES30.GL_TEXTURE_2D, ba.texture, progBlur, "uTexture")
        quad.draw()
    }

    private fun eraPass(
        p: EffectParams, sceneTex: Int, blurTex: Int, target: Fbo?,
        outW: Int, outH: Int, time: Float, seed: Float,
    ) {
        if (target != null) {
            target.bind()
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, outW, outH)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glUseProgram(progEra)
        GLES30.glUniformMatrix4fv(loc(progEra, "uTexMatrix"), 1, false, identityMatrix, 0)
        GLES30.glUniform2f(loc(progEra, "uWeave"), 0f, 0f)

        bindTex(0, GLES30.GL_TEXTURE_2D, sceneTex, progEra, "uScene")
        bindTex(1, GLES30.GL_TEXTURE_2D, blurTex, progEra, "uBlur")
        bindTex(2, GLES30.GL_TEXTURE_3D, lutTex[p.eraIndex], progEra, "uLut")
        val dustIdx = if (p.dust > 0f) ((time * 1.4f).toInt() % dustTex.size) else 0
        bindTex(3, GLES30.GL_TEXTURE_2D, dustTex[dustIdx], progEra, "uDust")
        bindTex(4, GLES30.GL_TEXTURE_2D, leakTex[p.leakIndex], progEra, "uLeak")

        setF("uLutStrength", p.lutStrength)
        setF("uLutSize", 17f)
        setF("uGrain", p.grain)
        setF("uGrainSize", p.grainSize)
        setF("uChromaNoise", p.chromaNoise)
        setF("uVignette", p.vignette)
        setF("uVignetteHard", p.vignetteHard)
        setF("uSoftFocus", p.softFocus)
        setF("uHalation", p.halation)
        setF("uSharpness", p.sharpness)
        setF("uScanline", p.scanline)
        setF("uChromaShift", p.chromaShift)
        setF("uFlutter", p.flutter)
        setF("uFlicker", p.flicker)
        setF("uDustOpacity", p.dust)
        setF("uLeakOpacity", p.leak)
        setF("uTime", time)
        setF("uSeed", seed)
        GLES30.glUniform2f(loc(progEra, "uResolution"), outW.toFloat(), outH.toFloat())
        quad.draw()
    }

    // ------------------------------------------------------------ helpers

    private fun ensureFbos(w: Int, h: Int, resScale: Float) {
        val sw = max(8, (w * resScale).toInt())
        val sh = max(8, (h * resScale).toInt())
        if (sceneFbo?.width != sw || sceneFbo?.height != sh) {
            releaseFbos()
            sceneFbo = Fbo(sw, sh)
            blurA = Fbo(max(4, sw / 2), max(4, sh / 2))
            blurB = Fbo(max(4, sw / 2), max(4, sh / 2))
        }
    }

    private fun releaseFbos() {
        sceneFbo?.release(); blurA?.release(); blurB?.release()
        sceneFbo = null; blurA = null; blurB = null
    }

    private fun flipRowsInPlace(buf: ByteBuffer, w: Int, h: Int) {
        val rowBytes = w * 4
        val top = ByteArray(rowBytes)
        val bottom = ByteArray(rowBytes)
        for (y in 0 until h / 2) {
            buf.position(y * rowBytes); buf.get(top)
            buf.position((h - 1 - y) * rowBytes); buf.get(bottom)
            buf.position(y * rowBytes); buf.put(bottom)
            buf.position((h - 1 - y) * rowBytes); buf.put(top)
        }
        buf.position(0)
    }

    private fun loc(program: Int, name: String) = GLES30.glGetUniformLocation(program, name)

    private fun setF(name: String, value: Float) {
        GLES30.glUniform1f(loc(progEra, name), value)
    }

    private fun bindTex(unit: Int, target: Int, tex: Int, program: Int, uniform: String) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, tex)
        GLES30.glUniform1i(loc(program, uniform), unit)
    }

    private companion object {
        const val TAG = "EraRenderer"
        const val STILL_MAX_DIM = 2560
    }
}
