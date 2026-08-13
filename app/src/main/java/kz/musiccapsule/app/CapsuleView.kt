package kz.musiccapsule.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.session.MediaController
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.max

class CapsuleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.BOLD) }
    private val shape = RectF()
    private val card = RectF()
    private var cameraX = 0f
    private var cameraY = 0f
    private var cameraRadius = dp(7f)
    private var baseCardTop = 0f
    private var baseCardBottom = 0f
    private var pulse = .35f
    private var pulseAnimator: ValueAnimator? = null

    var music = MusicState(); set(value) { field = value; invalidate() }
    var controls: MediaController.TransportControls? = null
    var timer: TimerState? = null; set(value) { field = value; invalidate() }
    var verticalOffset = 0f; set(value) { field = value; invalidate() }
    var expanded = false
    var morphProgress = 0f; set(value) {
        field = value.coerceIn(0f, 1f)
        invalidate()
    }
    var onToggle: (() -> Unit)? = null
    var onUserInteraction: (() -> Unit)? = null

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    fun configureGeometry(
        cameraX: Float, cameraY: Float, cameraRadius: Float,
        cardLeft: Float, cardTop: Float, cardRight: Float, cardBottom: Float
    ) {
        this.cameraX = cameraX
        this.cameraY = cameraY
        this.cameraRadius = cameraRadius
        baseCardTop = cardTop
        baseCardBottom = cardBottom
        card.set(cardLeft, cardTop, cardRight, cardBottom)
    }

    fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(.25f, 1f).apply {
            duration = 1050L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun stopPulse() { pulseAnimator?.cancel(); pulseAnimator = null; pulse = .3f; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        card.top = baseCardTop + verticalOffset
        card.bottom = baseCardBottom + verticalOffset
        computeShape()
        paint.color = Color.BLACK
        val compactRadius = if (verticalOffset < dp(2f)) cameraRadius else dp(11f)
        val radius = lerp(compactRadius, dp(28f), morphProgress)
        canvas.drawRoundRect(shape, radius, radius, paint)

        val contentAlpha = smoothStep(.42f, .78f, morphProgress)
        if (contentAlpha > 0f) {
            canvas.saveLayerAlpha(card.left, card.top, card.right, card.bottom, (255 * contentAlpha).toInt())
            if (timer != null) drawTimer(canvas) else {
                drawArtwork(canvas); drawLabels(canvas); drawPlayPause(canvas); drawProgress(canvas)
            }
            canvas.restore()
        }

        val dotAlpha = 1f - smoothStep(.02f, .20f, morphProgress)
        if (dotAlpha > 0f) drawPulseDot(canvas, dotAlpha)
    }

    private fun computeShape() {
        val compactY = cameraY + verticalOffset
        val compactRadius = if (verticalOffset < dp(2f)) cameraRadius else dp(11f)
        val start = RectF(cameraX - compactRadius, compactY - compactRadius, cameraX + compactRadius, compactY + compactRadius)
        shape.set(
            lerp(start.left, card.left, morphProgress),
            lerp(start.top, card.top, morphProgress),
            lerp(start.right, card.right, morphProgress),
            lerp(start.bottom, card.bottom, morphProgress)
        )
    }

    private fun drawPulseDot(canvas: Canvas, alpha: Float) {
        val compactY = cameraY + verticalOffset
        val compactRadius = if (verticalOffset < dp(2f)) cameraRadius else dp(11f)
        val cx = cameraX + compactRadius + dp(11f)
        paint.color = Color.argb((0x38 * alpha).toInt(), 49, 215, 255)
        canvas.drawCircle(cx, compactY, dp(2.7f) + dp(1.1f) * pulse, paint)
        paint.color = Color.argb((255 * alpha).toInt(), 49, 215, 255)
        canvas.drawCircle(cx, compactY, dp(1.4f) + dp(.35f) * pulse, paint)
    }

    private fun drawArtwork(canvas: Canvas) {
        val rect = RectF(card.left + dp(12f), card.top + dp(10f), card.left + dp(70f), card.top + dp(68f))
        val path = Path().apply { addRoundRect(rect, dp(13f), dp(13f), Path.Direction.CW) }
        canvas.save(); canvas.clipPath(path)
        music.artwork?.let { canvas.drawBitmap(it, null, rect, paint) } ?: run {
            paint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF725CFF.toInt(), 0xFFFF5D9E.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(rect, paint); paint.shader = null
        }
        canvas.restore()
    }

    private fun drawLabels(canvas: Canvas) {
        val x = card.left + dp(80f); val maxWidth = max(0f, card.width() - dp(216f))
        boldPaint.color = Color.WHITE; boldPaint.textSize = dp(14f)
        textPaint.color = 0xFFA3A3B1.toInt(); textPaint.textSize = dp(11.5f)
        canvas.drawText(ellipsize(music.title, boldPaint, maxWidth), x, card.top + dp(35f), boldPaint)
        canvas.drawText(ellipsize(music.artist.ifBlank { "Сейчас играет" }, textPaint, maxWidth), x, card.top + dp(55f), textPaint)
    }

    private fun drawPlayPause(canvas: Canvas) {
        val x = card.right - dp(43f); val y = card.top + dp(39f)
        paint.color = 0xFF24242B.toInt()
        canvas.drawCircle(x, y, dp(22f), paint)
        if (music.playing) drawPause(canvas, x, y) else drawPlay(canvas, x, y)
    }

    private fun drawTimer(canvas: Canvas) {
        val current = timer ?: return
        boldPaint.color = Color.WHITE; boldPaint.textSize = dp(18f)
        textPaint.color = 0xFFA3A3B1.toInt(); textPaint.textSize = dp(13f)
        canvas.drawText(current.title, card.left + dp(22f), card.top + dp(39f), boldPaint)
        val shownValue = current.chronometerTimeMillis?.let { base ->
            val delta = if (current.countDown) base - System.currentTimeMillis() else System.currentTimeMillis() - base
            formatDuration(delta.coerceAtLeast(0L))
        } ?: current.value
        canvas.drawText(shownValue, card.left + dp(22f), card.top + dp(64f), textPaint)
        if (current.chronometerTimeMillis != null) postInvalidateDelayed(500L)
    }

    private fun drawProgress(canvas: Canvas) {
        val start = card.left + dp(16f); val end = card.right - dp(16f); val y = card.bottom - dp(8f)
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = dp(2.5f); paint.color = 0xFF34343C.toInt()
        canvas.drawLine(start, y, end, y, paint)
        val ratio = if (music.duration > 0) (music.position.toFloat() / music.duration).coerceIn(0f, 1f) else 0f
        paint.color = 0xFF9B8AFF.toInt(); canvas.drawLine(start, y, start + (end - start) * ratio, y, paint)
    }

    fun handleTap(x: Float, y: Float) {
        onUserInteraction?.invoke()
        if (expanded && morphProgress > .82f) {
            when {
                y > card.bottom - dp(18f) && music.duration > 0 -> {
                    val ratio = ((x - card.left - dp(16f)) / (card.width() - dp(32f))).coerceIn(0f, 1f)
                    controls?.seekTo((music.duration * ratio).toLong())
                }
                timer == null && y < card.top + dp(76f) && x > card.right - dp(86f) ->
                    if (music.playing) controls?.pause() else controls?.play()
            }
        } else onToggle?.invoke()
    }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun drawPlay(c:Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawPath(Path().apply{moveTo(x-dp(5f),y-dp(7f));lineTo(x+dp(7f),y);lineTo(x-dp(5f),y+dp(7f));close()},paint)}
    private fun drawPause(c:Canvas,x:Float,y:Float){paint.color=Color.WHITE;c.drawRoundRect(x-dp(6f),y-dp(7f),x-dp(2f),y+dp(7f),dp(1f),dp(1f),paint);c.drawRoundRect(x+dp(2f),y-dp(7f),x+dp(6f),y+dp(7f),dp(1f),dp(1f),paint)}
    private fun ellipsize(s:String,p:Paint,maxW:Float):String{if(p.measureText(s)<=maxW)return s;var out=s;while(out.length>1&&p.measureText("$out…")>maxW)out=out.dropLast(1);return "$out…"}
    private fun smoothStep(a:Float,b:Float,x:Float):Float{val t=((x-a)/(b-a)).coerceIn(0f,1f);return t*t*(3f-2f*t)}
    private fun formatDuration(ms:Long):String{val total=ms/1000;val h=total/3600;val m=(total%3600)/60;val s=total%60;return if(h>0)"%d:%02d:%02d".format(h,m,s) else "%02d:%02d".format(m,s)}
    private fun lerp(a:Float,b:Float,t:Float)=a+(b-a)*t
    private fun dp(v:Float)=v*density
}
