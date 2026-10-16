package com.rcempire.safebuild

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Lightweight native OpenGL ES scene: an extruded, faceted shield with a raised check,
 * cyan rim lighting and continuous 3D rotation. No WebView or external model download.
 */
@Composable
fun Shield3DView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(ShieldRenderer())
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                setZOrderOnTop(false)
                preserveEGLContextOnPause = true
            }
        },
        update = { }
    )
}

private class ShieldRenderer : GLSurfaceView.Renderer {
    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private lateinit var shield: FloatBuffer
    private lateinit var shieldColors: FloatBuffer
    private lateinit var check: FloatBuffer
    private lateinit var checkColors: FloatBuffer
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var started = System.nanoTime()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.075f, 0.09f, 0.13f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        val vertexShader = """
            uniform mat4 uMVP;
            uniform mat4 uModel;
            attribute vec3 aPosition;
            attribute vec4 aColor;
            varying vec4 vColor;
            varying float vLight;
            void main() {
              vec4 world = uModel * vec4(aPosition, 1.0);
              gl_Position = uMVP * vec4(aPosition, 1.0);
              vec3 n = normalize(mat3(uModel) * vec3(0.0, 0.0, 1.0));
              vLight = 0.72 + 0.28 * max(0.0, dot(n, normalize(vec3(-0.4, 0.6, 1.0))));
              vColor = aColor;
            }
        """.trimIndent()
        val fragmentShader = """
            precision mediump float;
            varying vec4 vColor;
            varying float vLight;
            void main() {
              gl_FragColor = vec4(vColor.rgb * vLight, vColor.a);
            }
        """.trimIndent()
        program = GLES20.glCreateProgram().also { p ->
            GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vertexShader))
            GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader))
            GLES20.glLinkProgram(p)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetAttribLocation(program, "aColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        val geometry = buildShield()
        shield = floats(geometry.first)
        shieldColors = floats(geometry.second)
        val checkGeometry = buildCheck()
        check = floats(checkGeometry.first)
        checkColors = floats(checkGeometry.second)
        started = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.frustumM(projection, 0, -aspect, aspect, -1f, 1f, 2f, 18f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        Matrix.setLookAtM(view, 0, 0f, 0f, 7.2f, 0f, 0f, 0f, 0f, 1f, 0f)
        val seconds = (System.nanoTime() - started) / 1_000_000_000f
        val turn = seconds * 24f
        val bob = sin(seconds * 1.5f) * 0.10f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, 0f, bob, 0f)
        Matrix.rotateM(model, 0, turn, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, -8f, 1f, 0f, 0f)
        drawMesh(shield, shieldColors, 0, 0, GLES20.GL_TRIANGLES)
        // The check is a raised 3D ribbon on the shield's front face.
        drawMesh(check, checkColors, 0, 0, GLES20.GL_TRIANGLES)
    }

    private fun drawMesh(vertices: FloatBuffer, colors: FloatBuffer, ignored: Int, ignored2: Int, mode: Int) {
        vertices.position(0); colors.position(0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, model, 0)
        Matrix.multiplyMM(mvp, 0, view, 0, model, 0)
        val finalMvp = FloatArray(16)
        Matrix.multiplyMM(finalMvp, 0, projection, 0, mvp, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, finalMvp, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colors)
        GLES20.glDrawArrays(mode, 0, vertices.limit() / 3)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun buildShield(): Pair<FloatArray, FloatArray> {
        // A beveled shield is formed from a front/back pentagon plus side walls.
        val front = arrayOf(
            floatArrayOf(0f, 1.65f, 0.42f),
            floatArrayOf(-1.18f, 1.02f, 0.42f),
            floatArrayOf(-0.96f, -0.55f, 0.42f),
            floatArrayOf(0f, -1.55f, 0.42f),
            floatArrayOf(0.96f, -0.55f, 0.42f),
            floatArrayOf(1.18f, 1.02f, 0.42f)
        )
        val back = front.map { floatArrayOf(it[0] * 0.91f, it[1] * 0.94f, -0.32f) }.toTypedArray()
        val v = ArrayList<Float>()
        val c = ArrayList<Float>()
        fun tri(a: FloatArray, b: FloatArray, d: FloatArray, color: FloatArray) {
            listOf(a, b, d).forEach { p -> v.addAll(p.toList()); c.addAll(color.toList()) }
        }
        val dark = floatArrayOf(0.035f, 0.19f, 0.29f, 1f)
        val mid = floatArrayOf(0.025f, 0.42f, 0.58f, 1f)
        val bright = floatArrayOf(0.05f, 0.78f, 0.91f, 1f)
        val rear = floatArrayOf(0.025f, 0.10f, 0.18f, 1f)
        for (i in 1 until front.size - 1) tri(front[0], front[i], front[i + 1], if (i % 2 == 0) mid else dark)
        for (i in front.indices) {
            val j = (i + 1) % front.size
            tri(front[i], back[i], back[j], dark)
            tri(front[i], back[j], front[j], if (i == 0 || i == 5) bright else mid)
            // Thin luminous bevel on each front edge.
            tri(front[i], front[j], floatArrayOf(front[j][0] * 0.91f, front[j][1] * 0.91f, 0.455f), bright)
        }
        for (i in 1 until back.size - 1) tri(back[0], back[i + 1], back[i], rear)
        return v.toFloatArray() to c.toFloatArray()
    }

    private fun buildCheck(): Pair<FloatArray, FloatArray> {
        val v = ArrayList<Float>()
        val c = ArrayList<Float>()
        val glow = floatArrayOf(0.38f, 0.96f, 1f, 1f)
        // Raised, thick checkmark ribbon, split into two beveled bars.
        fun bar(ax: Float, ay: Float, bx: Float, by: Float, width: Float) {
            val dx = bx - ax; val dy = by - ay
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            val ox = -dy / length * width; val oy = dx / length * width
            val a = floatArrayOf(ax + ox, ay + oy, 0.53f)
            val b = floatArrayOf(ax - ox, ay - oy, 0.53f)
            val d = floatArrayOf(bx + ox, by + oy, 0.53f)
            val e = floatArrayOf(bx - ox, by - oy, 0.53f)
            listOf(a, b, d, d, b, e).forEach { p -> v.addAll(p.toList()); c.addAll(glow.toList()) }
        }
        bar(-0.57f, 0.05f, -0.15f, -0.42f, 0.105f)
        bar(-0.15f, -0.42f, 0.64f, 0.48f, 0.105f)
        return v.toFloatArray() to c.toFloatArray()
    }

    private fun floats(data: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(data); position(0)
        }

    private fun compile(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }
}
