package kz.musiccapsule.app

import android.accessibilityservice.AccessibilityService
import android.media.session.MediaController
import android.view.WindowManager

object MusicOverlayBridge {
    private var overlay: OverlayController? = null
    private var state: MusicState? = null
    private var controls: MediaController.TransportControls? = null
    private var timer: TimerState? = null

    fun attach(service: AccessibilityService) {
        overlay?.destroy()
        overlay = OverlayController(service, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        publishLatest()
    }

    fun detach() {
        overlay?.destroy()
        overlay = null
    }

    fun update(newState: MusicState, newControls: MediaController.TransportControls) {
        state = newState
        controls = newControls
        publishLatest()
    }

    fun updateTimer(newTimer: TimerState?) {
        timer = newTimer
        overlay?.updateTimer(newTimer)
        if (newTimer != null) overlay?.show()
        else {
            publishLatest()
            if (state?.playing != true) overlay?.hide()
        }
    }

    fun hasTimer(): Boolean = timer != null

    fun show() = overlay?.show()
    fun hide() = overlay?.hide()

    fun applyVerticalOffsetDp(value: Int) = overlay?.applyVerticalOffsetDp(value)

    private fun publishLatest() {
        overlay?.updateTimer(timer)
        val currentState = state
        val currentControls = controls
        if (currentState != null && currentControls != null) {
            overlay?.update(currentState, currentControls)
        }
        if (currentState?.playing == true || timer != null) overlay?.show()
    }
}
