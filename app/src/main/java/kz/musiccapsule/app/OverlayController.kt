package kz.musiccapsule.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.PathInterpolator

class OverlayController(
    private val context: Context,
    private val windowType: Int = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val geometry = readGeometry()
    private val overlayWidth = minOf(dp(352), geometry.screenWidth - dp(12))
    private val overlayHeight = geometry.statusBarBottom + dp(100)
    private val overlayLeft = geometry.camera.centerX() - overlayWidth / 2
    private val view = CapsuleView(context)
    private var attached = false
    private var expanded = false
    private var animator: ValueAnimator? = null
    private val collapseAfterTouch = Runnable { animateTo(false) }

    init {
        view.configureGeometry(
            cameraX = (geometry.camera.centerX() - overlayLeft).toFloat(),
            cameraY = geometry.camera.centerY().toFloat(),
            cameraRadius = dp(7).toFloat(),
            cardLeft = 0f,
            cardTop = (geometry.statusBarBottom + dp(4)).toFloat(),
            cardRight = overlayWidth.toFloat(),
            cardBottom = overlayHeight.toFloat()
        )
        view.onToggle = { animateTo(!expanded) }
        view.onUserInteraction = { if (expanded) scheduleCollapse() }
    }

    private val params = WindowManager.LayoutParams(
        overlayWidth,
        overlayHeight,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = overlayLeft
        y = 0
    }

    fun update(state: MusicState, controls: MediaController.TransportControls) {
        view.music = state
        view.controls = controls
        if (state.playing) view.startPulse() else view.stopPulse()
    }

    fun show() {
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
            !Settings.canDrawOverlays(context)
        ) return
        if (!attached) {
            runCatching { wm.addView(view, params) }.onSuccess {
                attached = true
                if (view.music.playing) view.startPulse()
            }
        }
    }

    fun hide() {
        main.removeCallbacks(collapseAfterTouch)
        animator?.cancel()
        animator = null
        if (attached) runCatching { wm.removeView(view) }
        attached = false
        expanded = false
        view.expanded = false
        view.morphProgress = 0f
        view.stopPulse()
    }

    fun destroy() = hide()

    private fun animateTo(open: Boolean) {
        if (!attached || (open == expanded && animator?.isRunning == true)) return
        main.removeCallbacks(collapseAfterTouch)
        animator?.cancel()
        expanded = open
        view.expanded = open
        // Keep the larger previous touch region during the morph itself.
        view.refreshTouchableRegion()
        val end = if (open) 1f else 0f
        animator = ValueAnimator.ofFloat(view.morphProgress, end).apply {
            duration = if (open) 420L else 360L
            interpolator = if (open) PathInterpolator(.16f, 1f, .3f, 1f)
            else PathInterpolator(.32f, 0f, .2f, 1f)
            addUpdateListener { view.morphProgress = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) {
                    view.refreshTouchableRegion()
                    if (!cancelled && open) scheduleCollapse()
                    animator = null
                }
            })
            start()
        }
    }

    private fun scheduleCollapse() {
        main.removeCallbacks(collapseAfterTouch)
        main.postDelayed(collapseAfterTouch, 2_000L)
    }

    private data class Geometry(val screenWidth: Int, val camera: Rect, val statusBarBottom: Int)

    private fun readGeometry(): Geometry {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val fallback = Rect(metrics.widthPixels / 2 - dp(7), dp(8), metrics.widthPixels / 2 + dp(7), dp(22))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Geometry(metrics.widthPixels, fallback, dp(32))
        val windowMetrics = wm.currentWindowMetrics
        val camera = windowMetrics.windowInsets.displayCutout?.boundingRects.orEmpty()
            .filter { it.top <= dp(32) }
            .minByOrNull { kotlin.math.abs(it.centerX() - windowMetrics.bounds.centerX()) }
            ?: fallback
        val statusBottom = windowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            .coerceAtLeast(camera.bottom)
        return Geometry(windowMetrics.bounds.width(), camera, statusBottom)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
