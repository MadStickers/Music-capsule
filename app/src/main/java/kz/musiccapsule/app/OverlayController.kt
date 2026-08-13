package kz.musiccapsule.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.session.MediaController
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.PathInterpolator

class OverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val screenWidth: Int
    private val cameraCenterX: Int
    private val cameraTop: Int
    private val collapsedWidth = dp(78)
    private val collapsedHeight: Int
    private val expandedWidth: Int
    private val expandedHeight = dp(116)
    private val view = CapsuleView(context)
    private var attached = false
    private var expanded = false
    private var animator: ValueAnimator? = null
    private val collapseAfterTouch = Runnable { animateTo(false) }

    init {
        val geometry = readCutoutGeometry()
        screenWidth = geometry.first
        cameraCenterX = geometry.second.centerX()
        cameraTop = geometry.second.top.coerceAtLeast(0)
        collapsedHeight = maxOf(dp(48), geometry.second.height() + dp(12))
        expandedWidth = minOf(dp(352), screenWidth - dp(12))
        view.onToggle = { if (expanded) animateTo(false) else animateTo(true) }
        view.onUserInteraction = { if (expanded) scheduleCollapse() }
    }

    private val params by lazy {
        WindowManager.LayoutParams(
            collapsedWidth,
            collapsedHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cameraCenterX - collapsedWidth / 2
            y = cameraTop
        }
    }

    fun update(state: MusicState, controls: MediaController.TransportControls) {
        view.music = state
        view.controls = controls
    }

    fun show() {
        if (!Settings.canDrawOverlays(context)) return
        if (!attached) {
            resetCollapsedGeometry()
            runCatching { wm.addView(view, params) }.onSuccess {
                attached = true
                view.startPulse()
            }
        }
    }

    fun hide() {
        main.removeCallbacks(collapseAfterTouch)
        animator?.cancel()
        if (attached) runCatching { wm.removeView(view) }
        attached = false
        expanded = false
        view.expanded = false
        view.reveal = 0f
        view.stopPulse()
        resetCollapsedGeometry()
    }

    fun destroy() = hide()

    private fun animateTo(open: Boolean) {
        if (!attached || (open == expanded && animator?.isRunning == true)) return
        main.removeCallbacks(collapseAfterTouch)
        animator?.cancel()
        val startWidth = params.width
        val startHeight = params.height
        val endWidth = if (open) expandedWidth else collapsedWidth
        val endHeight = if (open) expandedHeight else collapsedHeight
        expanded = open
        view.expanded = open
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (open) 520L else 420L
            interpolator = PathInterpolator(0.18f, 0.88f, 0.22f, 1f)
            addUpdateListener { animation ->
                val t = animation.animatedValue as Float
                val width = lerp(startWidth, endWidth, t)
                val height = lerp(startHeight, endHeight, t)
                params.width = width
                params.height = height
                params.x = cameraCenterX - width / 2
                params.y = cameraTop
                view.reveal = (width - collapsedWidth).toFloat() /
                    (expandedWidth - collapsedWidth).coerceAtLeast(1)
                runCatching { wm.updateViewLayout(view, params) }
            }
            doOnEnd {
                view.reveal = if (open) 1f else 0f
                if (open) scheduleCollapse()
            }
            start()
        }
    }

    private fun scheduleCollapse() {
        main.removeCallbacks(collapseAfterTouch)
        main.postDelayed(collapseAfterTouch, 2_000L)
    }

    private fun resetCollapsedGeometry() {
        params.width = collapsedWidth
        params.height = collapsedHeight
        params.x = cameraCenterX - collapsedWidth / 2
        params.y = cameraTop
    }

    private fun readCutoutGeometry(): Pair<Int, Rect> {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val fallback = Rect(metrics.widthPixels / 2 - dp(14), 0, metrics.widthPixels / 2 + dp(14), dp(38))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return metrics.widthPixels to fallback
        val windowMetrics = wm.currentWindowMetrics
        val cutouts = windowMetrics.windowInsets.displayCutout?.boundingRects.orEmpty()
        val centered = cutouts.filter { it.top <= dp(32) }
            .minByOrNull { kotlin.math.abs(it.centerX() - windowMetrics.bounds.centerX()) }
            ?: fallback
        return windowMetrics.bounds.width() to centered
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
            override fun onAnimationEnd(animation: android.animation.Animator) { if (!cancelled) block() }
        })
    }

    private fun lerp(start: Int, end: Int, t: Float) = (start + (end - start) * t).toInt()
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
