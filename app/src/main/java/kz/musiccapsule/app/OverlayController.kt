package kz.musiccapsule.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
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
    private val screenWidth: Int
    private val cameraCenterX: Int
    private val cameraCenterY: Int
    private val cameraTop: Int
    private val expandedY: Int
    private val collapsedWidth = dp(78)
    private val collapsedHeight: Int
    private val expandedWidth: Int
    private val expandedHeight = dp(96)
    private val view = CapsuleView(context)
    private var attached = false
    private var expanded = false
    private var animating = false
    private val collapseAfterTouch = Runnable { animateTo(false) }

    init {
        val geometry = readCutoutGeometry()
        screenWidth = geometry.screenWidth
        cameraCenterX = geometry.camera.centerX()
        cameraCenterY = geometry.camera.centerY()
        cameraTop = geometry.camera.top.coerceAtLeast(0)
        expandedY = geometry.statusBarBottom + dp(4)
        collapsedHeight = maxOf(dp(36), geometry.camera.height() + dp(8))
        expandedWidth = minOf(dp(352), screenWidth - dp(12))
        view.cameraCenterY = (cameraCenterY - cameraTop).toFloat()
        view.onToggle = { if (expanded) animateTo(false) else animateTo(true) }
        view.onUserInteraction = { if (expanded) scheduleCollapse() }
    }

    private val params by lazy {
        WindowManager.LayoutParams(
            collapsedWidth,
            collapsedHeight,
            windowType,
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
        if (state.playing) view.startPulse() else view.stopPulse()
    }

    fun show() {
        if (windowType == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY &&
            !Settings.canDrawOverlays(context)
        ) return
        if (!attached) {
            resetCollapsedGeometry()
            runCatching { wm.addView(view, params) }.onSuccess {
                attached = true
                if (view.music.playing) view.startPulse()
            }
        }
    }

    fun hide() {
        main.removeCallbacks(collapseAfterTouch)
        view.animate().setListener(null)
        view.animate().cancel()
        animating = false
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
        if (!attached || animating || open == expanded) return
        main.removeCallbacks(collapseAfterTouch)
        view.animate().setListener(null)
        view.animate().cancel()
        animating = true
        expanded = open
        view.expanded = open

        if (open) {
            params.width = expandedWidth
            params.height = expandedHeight
            params.x = cameraCenterX - expandedWidth / 2
            params.y = expandedY
            runCatching { wm.updateViewLayout(view, params) }

            view.reveal = 1f
            view.pivotX = expandedWidth / 2f
            view.pivotY = 0f
            view.scaleX = collapsedWidth.toFloat() / expandedWidth
            view.scaleY = collapsedHeight.toFloat() / expandedHeight
            view.translationY = -dp(10).toFloat()
            view.alpha = .25f
            view.animate()
                .scaleX(1f).scaleY(1f).translationY(0f).alpha(1f)
                .setDuration(360L)
                .setInterpolator(PathInterpolator(.16f, 1f, .3f, 1f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animating = false
                        view.animate().setListener(null)
                        scheduleCollapse()
                    }
                }).start()
        } else {
            view.pivotX = expandedWidth / 2f
            view.pivotY = 0f
            view.animate()
                .scaleX(collapsedWidth.toFloat() / expandedWidth)
                .scaleY(collapsedHeight.toFloat() / expandedHeight)
                .translationY(-dp(10).toFloat())
                .alpha(.18f)
                .setDuration(260L)
                .setInterpolator(PathInterpolator(.4f, 0f, .7f, .2f))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetCollapsedGeometry()
                        runCatching { wm.updateViewLayout(view, params) }
                        view.reveal = 0f
                        view.scaleX = 1f
                        view.scaleY = 1f
                        view.translationY = 0f
                        view.alpha = 1f
                        animating = false
                        view.animate().setListener(null)
                    }
                }).start()
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
        view.cameraCenterY = (cameraCenterY - cameraTop).toFloat()
    }

    private data class ScreenGeometry(val screenWidth: Int, val camera: Rect, val statusBarBottom: Int)

    private fun readCutoutGeometry(): ScreenGeometry {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val fallback = Rect(metrics.widthPixels / 2 - dp(14), 0, metrics.widthPixels / 2 + dp(14), dp(38))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ScreenGeometry(metrics.widthPixels, fallback, dp(32))
        val windowMetrics = wm.currentWindowMetrics
        val cutouts = windowMetrics.windowInsets.displayCutout?.boundingRects.orEmpty()
        val centered = cutouts.filter { it.top <= dp(32) }
            .minByOrNull { kotlin.math.abs(it.centerX() - windowMetrics.bounds.centerX()) }
            ?: fallback
        val statusBarBottom = windowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            .coerceAtLeast(centered.bottom)
        return ScreenGeometry(windowMetrics.bounds.width(), centered, statusBarBottom)
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
