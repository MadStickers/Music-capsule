package kz.musiccapsule.app

import android.accessibilityservice.AccessibilityService
import android.media.session.MediaController
import android.view.WindowManager

object MusicOverlayBridge {
    private var overlay: OverlayController? = null
    private var state: MusicState? = null
    private var controls: MediaController.TransportControls? = null

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

    fun show() = overlay?.show()
    fun hide() = overlay?.hide()

    fun applyVerticalOffsetDp(value: Int) = overlay?.applyVerticalOffsetDp(value)
    fun applyControlMode(value: CapsulePreferences.ControlMode) = overlay?.applyControlMode(value)

    private fun publishLatest() {
        val currentState = state
        val currentControls = controls
        if (currentState != null && currentControls != null) {
            overlay?.update(currentState, currentControls)
        }
        if (currentState?.playing == true) overlay?.show()
    }
}
