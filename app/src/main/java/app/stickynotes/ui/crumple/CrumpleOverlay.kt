package app.stickynotes.ui.crumple

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.stickynotes.ui.CrumpleJob
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Deleting a note: the card is photographed, scrunched into a paper ball (a deforming mesh with
 * crease shading), then tossed so it falls, bounces and rolls with gravity, casting a shadow on
 * the bottom of the screen, before fading away. [cardContent] draws the note exactly as in the grid.
 */
@Composable
fun CrumpleOverlay(
    job: CrumpleJob?,
    cardContent: @Composable (CrumpleJob) -> Unit,
    onFinished: (CrumpleJob) -> Unit,
) {
    if (job == null) return
    key(job) { CrumpleRun(job, cardContent, onFinished) }
}

@Composable
private fun CrumpleRun(
    job: CrumpleJob,
    cardContent: @Composable (CrumpleJob) -> Unit,
    onFinished: (CrumpleJob) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val finish by rememberUpdatedState(onFinished)
    val navBottom = WindowInsets.navigationBars.getBottom(density)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val bounds = remember {
            job.bounds?.takeIf { it.width > 1f && it.height > 1f } ?: run {
                val w = with(density) { 170.dp.toPx() }
                val h = with(density) { 190.dp.toPx() }
                Rect(Offset((screenW - w) / 2f, (screenH - h) / 2.6f), androidx.compose.ui.geometry.Size(w, h))
            }
        }
        val layer = rememberGraphicsLayer()
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        val snapshot = bitmap

        if (snapshot == null) {
            // Draw the card once where it was, recording it so it can be turned into a bitmap.
            Box(
                Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(
                        width = with(density) { bounds.width.toDp() },
                        height = with(density) { bounds.height.toDp() },
                    )
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                cardContent(job)
            }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                withFrameNanos { }
                val captured = try {
                    layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    null
                }
                if (captured == null || captured.width < 2 || captured.height < 2) {
                    finish(job)
                } else {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    bitmap = captured
                }
            }
        } else {
            val floorY = screenH - navBottom - with(density) { 28.dp.toPx() }
            val sim = remember(snapshot) {
                PaperBall(
                    bitmap = snapshot,
                    start = bounds,
                    screenW = screenW,
                    floorY = floorY,
                    dp = density.density,
                    seed = job.note.id.toInt() xor job.hashCode(),
                )
            }
            var frame by remember { mutableLongStateOf(0L) }
            LaunchedEffect(sim) {
                var last = withFrameNanos { it }
                while (!sim.done) {
                    val now = withFrameNanos { it }
                    sim.step(((now - last) / 1_000_000_000f).coerceIn(0f, 1f / 30f))
                    last = now
                    frame = now
                }
                finish(job)
            }
            Canvas(Modifier.fillMaxSize()) {
                if (frame == -1L) return@Canvas // reading frame redraws every frame
                sim.draw(this)
            }
        }
    }
}

/** The paper: a mesh over the card's bitmap that crumples, then a rigid ball with simple physics. */
private class PaperBall(
    private val bitmap: Bitmap,
    start: Rect,
    private val screenW: Float,
    private val floorY: Float,
    private val dp: Float,
    seed: Int,
) {
    private val w = bitmap.width.toFloat()
    private val h = bitmap.height.toFloat()
    private val cols = 14
    private val rows = 14
    private val count = (cols + 1) * (rows + 1)
    private val rest = FloatArray(count * 2)
    private val target = FloatArray(count * 2)
    private val delay = FloatArray(count)
    private val crease = FloatArray(count)
    private val wobble = FloatArray(count * 2)
    private val verts = FloatArray(count * 2)
    private val colors = IntArray(count)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val radius = (min(w, h) * 0.24f).coerceIn(26f * dp, 54f * dp)
    private val scaleToFit = 1f

    // Rigid-body state (screen space).
    private var cx = start.center.x
    private var cy = start.center.y
    private var vx = 0f
    private var vy = 0f
    private var angle = 0f
    private var spin = 0f

    private var t = 0f
    private var phase = Phase.CRUMPLE
    private var restTime = 0f
    private var fade = 1f
    private var shrink = 1f
    private var bounces = 0
    var done = false
        private set

    private val rnd = Random(seed)
    private val crumpleTime = 0.62f
    private val gravity = 2900f * dp
    private val endTwist = (rnd.nextFloat() - 0.5f) * 70f

    private enum class Phase { CRUMPLE, FLIGHT, FADE }

    init {
        val maxD = hypot(w / 2f, h / 2f)
        for (j in 0..rows) {
            for (i in 0..cols) {
                val k = j * (cols + 1) + i
                val x = w * i / cols
                val y = h * j / rows
                rest[2 * k] = x
                rest[2 * k + 1] = y
                val dx = x - w / 2f
                val dy = y - h / 2f
                val d = hypot(dx, dy)
                // Paper folds inward: outer points end up on the ball's surface, inner ones inside it.
                val ang = atan2(dy, dx) + (rnd.nextFloat() - 0.5f) * 1.1f
                val r = radius * (0.30f + 0.70f * (d / maxD)) * (0.78f + 0.38f * rnd.nextFloat())
                target[2 * k] = w / 2f + cos(ang) * r
                target[2 * k + 1] = h / 2f + sin(ang) * r
                delay[k] = 0.32f * (1f - d / maxD) // edges fold first
                crease[k] = rnd.nextFloat()
                wobble[2 * k] = (rnd.nextFloat() - 0.5f) * 14f * dp
                wobble[2 * k + 1] = (rnd.nextFloat() - 0.5f) * 14f * dp
            }
        }
        rest.copyInto(verts)
        colors.fill(0xFFFFFFFF.toInt())
    }

    fun step(dt: Float) {
        t += dt
        when (phase) {
            Phase.CRUMPLE -> {
                val p = (t / crumpleTime).coerceIn(0f, 1f)
                updateMesh(p)
                angle = endTwist * easeInOut(p)
                if (p >= 1f) {
                    phase = Phase.FLIGHT
                    val dir = if (rnd.nextBoolean()) 1f else -1f
                    // Toss toward the side with more room.
                    val room = if (cx < screenW / 2f) 1f else -1f
                    vx = (if (rnd.nextFloat() < 0.75f) room else dir) * (140f + 180f * rnd.nextFloat()) * dp
                    vy = -(380f + 160f * rnd.nextFloat()) * dp
                    spin = vx / radius * 57.3f
                }
            }

            Phase.FLIGHT -> {
                vy += gravity * dt
                cx += vx * dt
                cy += vy * dt
                angle += spin * dt

                // Walls.
                if (cx - radius < 0f) {
                    cx = radius
                    vx = abs(vx) * 0.6f
                } else if (cx + radius > screenW) {
                    cx = screenW - radius
                    vx = -abs(vx) * 0.6f
                }

                // Floor: bounce, lose energy, then roll.
                if (cy + radius >= floorY) {
                    cy = floorY - radius
                    if (vy > 0f) {
                        bounces++
                        vy = if (vy < 140f * dp) 0f else -vy * 0.42f
                        vx *= 0.82f
                    }
                    if (vy == 0f) {
                        vx *= (1f - 2.6f * dt).coerceAtLeast(0f)
                        restTime += dt
                    }
                    spin = vx / radius * 57.3f
                }
                if ((vy == 0f && (abs(vx) < 25f * dp || restTime > 0.45f)) || t > 3.5f) {
                    phase = Phase.FADE
                }
            }

            Phase.FADE -> {
                vx *= (1f - 3f * dt).coerceAtLeast(0f)
                cx += vx * dt
                angle += vx / radius * 57.3f * dt
                fade = (fade - dt / 0.28f).coerceAtLeast(0f)
                shrink = 0.7f + 0.3f * fade
                if (fade <= 0f) done = true
            }
        }
    }

    private fun updateMesh(p: Float) {
        val lightX = -0.55f
        val lightY = -0.83f
        for (k in 0 until count) {
            val local = ((p - delay[k]) / (1f - 0.32f)).coerceIn(0f, 1f)
            val e = easeInOut(local)
            val jiggle = sin(PI.toFloat() * local) // creases buckle mid-way
            val x = rest[2 * k] + (target[2 * k] - rest[2 * k]) * e + wobble[2 * k] * jiggle
            val y = rest[2 * k + 1] + (target[2 * k + 1] - rest[2 * k + 1]) * e + wobble[2 * k + 1] * jiggle
            verts[2 * k] = x
            verts[2 * k + 1] = y

            // Shading: random creases darken, and the ball is lit from the top-left.
            val nx = ((x - w / 2f) / radius).coerceIn(-1.4f, 1.4f)
            val ny = ((y - h / 2f) / radius).coerceIn(-1.4f, 1.4f)
            val facing = (nx * lightX + ny * lightY) * 0.5f
            val creaseShade = 0.10f + 0.42f * crease[k]
            val b = (1f - e * creaseShade + e * facing * 0.35f).coerceIn(0.38f, 1f)
            val g = (b * 255f).roundToInt()
            colors[k] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
    }

    fun draw(scope: DrawScope) = with(scope) {
        drawShadow()
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.translate(cx, cy)
            nc.rotate(angle)
            val lift = if (phase == Phase.CRUMPLE) 1f + 0.03f * sin(PI.toFloat() * (t / crumpleTime).coerceIn(0f, 1f)) else 1f
            val s = shrink * scaleToFit * lift
            nc.scale(s, s)
            nc.translate(-w / 2f, -h / 2f)
            paint.alpha = (fade * 255f).roundToInt()
            nc.drawBitmapMesh(bitmap, cols, rows, verts, 0, colors, 0, paint)
            nc.restore()
        }
    }

    private fun DrawScope.drawShadow() {
        if (phase == Phase.CRUMPLE) {
            // Soft shadow under the paper as it lifts off the board.
            val p = (t / crumpleTime).coerceIn(0f, 1f)
            val sw = (w + (radius * 2f - w) * p) * 0.55f
            val center = Offset(cx + 4f * dp, cy + 10f * dp + 6f * dp * p)
            drawSoftOval(center, sw, sw * 0.9f, 0.22f * fade)
            return
        }
        val height = (floorY - (cy + radius)).coerceAtLeast(0f)
        val closeness = (1f - height / (floorY * 0.65f)).coerceIn(0f, 1f)
        val sw = radius * (1.1f + 0.9f * closeness) * shrink
        drawSoftOval(Offset(cx, floorY - 2f * dp), sw, sw * 0.22f, (0.08f + 0.26f * closeness) * fade)
    }

    private fun DrawScope.drawSoftOval(center: Offset, halfW: Float, halfH: Float, alpha: Float) {
        if (halfW <= 0f || halfH <= 0f || alpha <= 0f) return
        withTransform({ scale(1f, halfH / halfW, pivot = center) }) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = alpha), Color.Black.copy(alpha = alpha * 0.4f), Color.Transparent),
                    center = center,
                    radius = halfW,
                ),
                radius = halfW,
                center = center,
            )
        }
    }

    private fun easeInOut(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
}
