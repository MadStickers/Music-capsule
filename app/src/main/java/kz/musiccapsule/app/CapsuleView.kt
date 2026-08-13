package kz.musiccapsule.app

import android.content.Context
import android.graphics.*
import android.media.session.MediaController
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CapsuleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    var music = MusicState(); set(value) { field = value; invalidate() }
    var controls: MediaController.TransportControls? = null
    var expanded = false; set(value) { field = value; invalidate() }
    var reveal = 0f; set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var onToggle: (() -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val radius = h / 2f
        paint.color = Color.BLACK
        paint.setShadowLayer(dp(14f), 0f, dp(4f), 0x66000000)
        setLayerType(LAYER_TYPE_SOFTWARE, paint)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), h, radius, radius, paint)
        paint.clearShadowLayer()
        if (reveal < 0.08f || width < dp(110f)) return

        canvas.save()
        canvas.clipPath(Path().apply {
            addRoundRect(0f, 0f, width.toFloat(), h, radius, radius, Path.Direction.CW)
        })
        canvas.translate((1f - reveal) * -dp(24f), 0f)
        drawArtwork(canvas)
        drawLabels(canvas)
        drawButtons(canvas)
        drawProgress(canvas)
        canvas.restore()
    }

    private fun drawArtwork(canvas: Canvas) {
        val left = dp(9f)
        val top = dp(9f)
        val size = dp(44f)
        val rect = RectF(left, top, left + size, top + size)
        val path = Path().apply { addRoundRect(rect, dp(12f), dp(12f), Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        val bitmap = music.artwork
        if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, paint)
        else {
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF725CFF.toInt(), 0xFFFF5D9E.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null
            paint.color = Color.WHITE; paint.strokeWidth = dp(2f); paint.style = Paint.Style.STROKE
            canvas.drawCircle(rect.centerX(), rect.centerY(), dp(8f), paint); paint.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    private fun drawLabels(canvas: Canvas) {
        val x = dp(62f)
        val maxWidth = max(0f, width - dp(190f))
        boldPaint.color = Color.WHITE
        boldPaint.textSize = dp(13.5f)
        textPaint.color = 0xFF9C9CAA.toInt()
        textPaint.textSize = dp(11.5f)
        canvas.drawText(ellipsize(music.title, boldPaint, maxWidth), x, dp(27f), boldPaint)
        canvas.drawText(ellipsize(music.artist.ifBlank { "Сейчас играет" }, textPaint, maxWidth), x, dp(44f), textPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        val cy = dp(29f)
        val nextX = width - dp(28f)
        val playX = width - dp(70f)
        val prevX = width - dp(112f)
        paint.color = Color.WHITE
        drawPrevious(canvas, prevX, cy)
        if (music.playing) drawPause(canvas, playX, cy) else drawPlay(canvas, playX, cy)
        drawNext(canvas, nextX, cy)
    }

    private fun drawProgress(canvas: Canvas) {
        val start = dp(62f)
        val end = width - dp(18f)
        val y = height - dp(5f)
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = dp(2f); paint.color = 0xFF34343C.toInt()
        canvas.drawLine(start, y, end, y, paint)
        val ratio = if (music.duration > 0) (music.position.toFloat() / music.duration).coerceIn(0f, 1f) else 0f
        paint.color = 0xFF9B8AFF.toInt(); canvas.drawLine(start, y, start + (end - start) * ratio, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        if (expanded && reveal > .8f) {
            when {
                event.x > width - dp(48f) -> controls?.skipToNext()
                event.x > width - dp(91f) -> if (music.playing) controls?.pause() else controls?.play()
                event.x > width - dp(133f) -> controls?.skipToPrevious()
                event.y > height - dp(14f) && music.duration > 0 -> {
                    val ratio = ((event.x - dp(62f)) / (width - dp(80f))).coerceIn(0f, 1f)
                    controls?.seekTo((music.duration * ratio).toLong())
                }
                else -> onToggle?.invoke()
            }
        } else onToggle?.invoke()
        performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    private fun drawPlay(c: Canvas, x: Float, y: Float) { paint.color = Color.WHITE; c.drawPath(Path().apply { moveTo(x-dp(5f),y-dp(7f)); lineTo(x+dp(7f),y); lineTo(x-dp(5f),y+dp(7f)); close() }, paint) }
    private fun drawPause(c: Canvas, x: Float, y: Float) { paint.color=Color.WHITE; c.drawRoundRect(x-dp(6f),y-dp(7f),x-dp(2f),y+dp(7f),dp(1f),dp(1f),paint); c.drawRoundRect(x+dp(2f),y-dp(7f),x+dp(6f),y+dp(7f),dp(1f),dp(1f),paint) }
    private fun drawPrevious(c: Canvas, x: Float, y: Float) { paint.color=Color.WHITE; c.drawRect(x-dp(7f),y-dp(7f),x-dp(5f),y+dp(7f),paint); c.drawPath(Path().apply { moveTo(x+dp(6f),y-dp(7f)); lineTo(x-dp(5f),y); lineTo(x+dp(6f),y+dp(7f)); close() },paint) }
    private fun drawNext(c: Canvas, x: Float, y: Float) { paint.color=Color.WHITE; c.drawRect(x+dp(5f),y-dp(7f),x+dp(7f),y+dp(7f),paint); c.drawPath(Path().apply { moveTo(x-dp(6f),y-dp(7f)); lineTo(x+dp(5f),y); lineTo(x-dp(6f),y+dp(7f)); close() },paint) }
    private fun ellipsize(s: String, p: Paint, maxW: Float): String { if (p.measureText(s)<=maxW) return s; var out=s; while(out.length>1 && p.measureText("$out…")>maxW) out=out.dropLast(1); return "$out…" }
    private fun dp(v: Float)=v*density
}
