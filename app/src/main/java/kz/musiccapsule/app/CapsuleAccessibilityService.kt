package kz.musiccapsule.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class CapsuleAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        MusicOverlayBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        MusicOverlayBridge.detach()
        super.onDestroy()
    }
}
