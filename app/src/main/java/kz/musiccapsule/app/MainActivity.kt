package kz.musiccapsule.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var overlayStatus: TextView
    private lateinit var mediaStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        overlayStatus.text = if (Settings.canDrawOverlays(this)) "✓ Доступ поверх окон включён" else "○ Доступ поверх окон выключен"
        mediaStatus.text = if (isListenerEnabled()) "✓ Доступ к музыке включён" else "○ Доступ к музыке выключен"
        accessibilityStatus.text = if (isAccessibilityEnabled()) "✓ Интерактивный остров включён" else "○ Интерактивный остров выключен"
        if (Settings.canDrawOverlays(this) && isListenerEnabled()) {
            NotificationListenerService.requestRebind(ComponentName(this, MusicListenerService::class.java))
        }
    }

    private fun buildContent(): View {
        val pad = dp(24)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, dp(44), pad, pad)
            setBackgroundColor(Color.rgb(9, 11, 16))
        }
        root.addView(TextView(this).apply {
            text = "Music Capsule"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }, matchWrap(dp(72)))
        root.addView(TextView(this).apply {
            text = "Музыкальный остров вокруг фронтальной камеры"
            textSize = 16f
            setTextColor(Color.rgb(170, 174, 190))
            gravity = Gravity.CENTER
        }, matchWrap(dp(70)))

        overlayStatus = statusView()
        root.addView(overlayStatus, matchWrap(dp(42)))
        root.addView(Button(this).apply {
            text = "1. Разрешить поверх окон"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }, matchWrap(dp(58)))

        mediaStatus = statusView()
        root.addView(mediaStatus, matchWrap(dp(42)).apply { topMargin = dp(18) })
        root.addView(Button(this).apply {
            text = "2. Разрешить доступ к музыке"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }, matchWrap(dp(58)))

        accessibilityStatus = statusView()
        root.addView(accessibilityStatus, matchWrap(dp(42)).apply { topMargin = dp(12) })
        root.addView(Button(this).apply {
            text = "3. Включить интерактивный остров"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, matchWrap(dp(58)))

        root.addView(TextView(this).apply {
            text = "После трёх разрешений включите музыку. Служба специальных возможностей нужна только для нажатия поверх строки состояния и не читает экран."
            textSize = 14f
            setTextColor(Color.rgb(145, 150, 168))
            gravity = Gravity.CENTER
        }, matchWrap(dp(110)).apply { topMargin = dp(28) })
        return root
    }

    private fun statusView() = TextView(this).apply {
        textSize = 15f
        setTextColor(Color.rgb(210, 213, 224))
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun isListenerEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun isAccessibilityEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains("$packageName/${CapsuleAccessibilityService::class.java.name}") == true

    private fun matchWrap(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
