package kz.musiccapsule.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.PathInterpolator

class OverlayController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val collapsedWidth = dp(46)
    private val expandedWidth = dp(342)
    private val height = dp(62)
    private val view = CapsuleView(context)
    private var attached = false
    private var expanded = false
    private var animator: ValueAnimator? = null

    private val params = WindowManager.LayoutParams(
        collapsedWidth,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = -dp(7)
        x = 0
    }

    init {
        view.onToggle = { animateTo(!expanded) }
    }

    fun update(state: MusicState, controls: MediaController.TransportControls) {
        view.music = state
        view.controls = controls
    }

    fun show(autoExpand: Boolean) {
        if (!Settings.canDrawOverlays(context)) return
        if (!attached) {
            runCatching { wm.addView(view, params) }.onSuccess { attached = true }
        }
        if (autoExpand && !expanded) animateTo(true)
    }

    fun hide() {
        if (!attached) return
        animator?.cancel()
        runCatching { wm.removeView(view) }
        attached = false
        expanded = false
        params.width = collapsedWidth
        params.x = 0
    }

    fun destroy() = hide()

    private fun animateTo(open: Boolean) {
        if (!attached) return
        animator?.cancel()
        val start = params.width
        val end = if (open) expandedWidth else collapsedWidth
        expanded = open
        view.expanded = open
        animator = ValueAnimator.ofInt(start, end).apply {
            duration = 520L
            interpolator = PathInterpolator(0.18f, 0.9f, 0.22f, 1f)
            addUpdateListener {
                val width = it.animatedValue as Int
                params.width = width
                // Keep the left edge anchored around the centered camera cutout.
                params.x = (width - collapsedWidth) / 2
                runCatching { wm.updateViewLayout(view, params) }
                view.reveal = (width - collapsedWidth).toFloat() / (expandedWidth - collapsedWidth)
            }
            start()
        }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
