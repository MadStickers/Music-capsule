package kz.musiccapsule.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.session.MediaController
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max

class CapsuleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private var pulse = .45f
    private var pulseAnimator: ValueAnimator? = null

    var music = MusicState(); set(value) { field = value; invalidate() }
    var controls: MediaController.TransportControls? = null
    var expanded = false; set(value) { field = value; invalidate() }
    var reveal = 0f; set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var cameraCenterY = dp(18f); set(value) { field = value; invalidate() }
    var onToggle: (() -> Unit)? = null
    var onUserInteraction: (() -> Unit)? = null

    fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(.28f, 1f).apply {
            duration = 920L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stopPulse() { pulseAnimator?.cancel(); pulseAnimator = null }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        setLayerType(LAYER_TYPE_SOFTWARE, paint)
        if (reveal < .02f) {
            // Only a subtle camera-sized mask; the whole View remains a large touch target.
            paint.color = Color.BLACK
            canvas.drawCircle(width / 2f, cameraCenterY, dp(7f), paint)
        } else {
            paint.color = Color.BLACK
            paint.setShadowLayer(dp(12f), 0f, dp(4f), 0x70000000)
            val radius = minOf(dp(28f), height / 2f)
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, paint)
            paint.clearShadowLayer()
        }
        if (reveal < .82f) drawPulseDot(canvas)
        if (reveal > .08f) {
            val alpha = ((reveal - .08f) / .92f).coerceIn(0f, 1f)
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt())
            drawExpandedContent(canvas)
            canvas.restore()
        }
    }

    private fun drawPulseDot(canvas: Canvas) {
        val cx = width / 2f + dp(12f)
        val cy = cameraCenterY
        paint.color = 0x408F7CFF
        canvas.drawCircle(cx, cy, dp(2.8f) + dp(1.8f) * pulse, paint)
        paint.color = 0xFF9B8AFF.toInt()
        canvas.drawCircle(cx, cy, dp(1.5f) + dp(.5f) * pulse, paint)
    }

    private fun drawExpandedContent(canvas: Canvas) {
        drawArtwork(canvas); drawLabels(canvas); drawButtons(canvas); drawProgress(canvas)
    }

    private fun drawArtwork(canvas: Canvas) {
        val left = dp(12f); val top = dp(10f); val size = dp(58f)
        val rect = RectF(left, top, left + size, top + size)
        val path = Path().apply { addRoundRect(rect, dp(13f), dp(13f), Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        music.artwork?.let { canvas.drawBitmap(it, null, rect, paint) } ?: run {
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF725CFF.toInt(), 0xFFFF5D9E.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null
        }
        canvas.restore()
    }

    private fun drawLabels(canvas: Canvas) {
        val x = dp(80f); val maxWidth = max(0f, width - dp(216f))
        boldPaint.color = Color.WHITE; boldPaint.textSize = dp(14f)
        textPaint.color = 0xFFA3A3B1.toInt(); textPaint.textSize = dp(11.5f)
        canvas.drawText(ellipsize(music.title, boldPaint, maxWidth), x, dp(35f), boldPaint)
        canvas.drawText(ellipsize(music.artist.ifBlank { "Сейчас играет" }, textPaint, maxWidth), x, dp(55f), textPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        val cy = dp(39f); val nextX = width - dp(24f); val playX = width - dp(67f); val prevX = width - dp(110f)
        drawPrevious(canvas, prevX, cy)
        if (music.playing) drawPause(canvas, playX, cy) else drawPlay(canvas, playX, cy)
        drawNext(canvas, nextX, cy)
    }

    private fun drawProgress(canvas: Canvas) {
        val start = dp(16f); val end = width - dp(16f); val y = height - dp(8f)
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = dp(2.5f); paint.color = 0xFF34343C.toInt()
        canvas.drawLine(start, y, end, y, paint)
        val ratio = if (music.duration > 0) (music.position.toFloat() / music.duration).coerceIn(0f, 1f) else 0f
        paint.color = 0xFF9B8AFF.toInt(); canvas.drawLine(start, y, start + (end - start) * ratio, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) onUserInteraction?.invoke()
        if (event.action != MotionEvent.ACTION_UP) return true
        onUserInteraction?.invoke()
        if (expanded && reveal > .82f) {
            when {
                event.y > height - dp(18f) && music.duration > 0 -> {
                    val ratio = ((event.x - dp(16f)) / (width - dp(32f))).coerceIn(0f, 1f)
                    controls?.seekTo((music.duration * ratio).toLong())
                }
                event.y < dp(76f) && event.x > width - dp(45f) -> controls?.skipToNext()
                event.y < dp(76f) && event.x > width - dp(89f) -> if (music.playing) controls?.pause() else controls?.play()
                event.y < dp(76f) && event.x > width - dp(132f) -> controls?.skipToPrevious()
                else -> onToggle?.invoke()
            }
        } else onToggle?.invoke()
        performClick(); return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun drawPlay(c: Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawPath(Path().apply{moveTo(x-dp(5f),y-dp(7f));lineTo(x+dp(7f),y);lineTo(x-dp(5f),y+dp(7f));close()},paint)}
    private fun drawPause(c: Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawRoundRect(x-dp(6f),y-dp(7f),x-dp(2f),y+dp(7f),dp(1f),dp(1f),paint);c.drawRoundRect(x+dp(2f),y-dp(7f),x+dp(6f),y+dp(7f),dp(1f),dp(1f),paint)}
    private fun drawPrevious(c: Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawRect(x-dp(7f),y-dp(7f),x-dp(5f),y+dp(7f),paint);c.drawPath(Path().apply{moveTo(x+dp(6f),y-dp(7f));lineTo(x-dp(5f),y);lineTo(x+dp(6f),y+dp(7f));close()},paint)}
    private fun drawNext(c: Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawRect(x+dp(5f),y-dp(7f),x+dp(7f),y+dp(7f),paint);c.drawPath(Path().apply{moveTo(x-dp(6f),y-dp(7f));lineTo(x+dp(5f),y);lineTo(x-dp(6f),y+dp(7f));close()},paint)}
    private fun ellipsize(s:String,p:Paint,maxW:Float):String{if(p.measureText(s)<=maxW)return s;var out=s;while(out.length>1&&p.measureText("$out…")>maxW)out=out.dropLast(1);return "$out…"}
    private fun dp(v:Float)=v*density
}
