package kz.musiccapsule.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.content.pm.PackageManager
import java.security.MessageDigest
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.slider.Slider
import com.google.android.material.button.MaterialButtonToggleGroup

class MainActivity : AppCompatActivity() {
    private lateinit var overlayStatus: TextView
    private lateinit var mediaStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        overlayStatus.text = status(Settings.canDrawOverlays(this))
        mediaStatus.text = status(isListenerEnabled())
        accessibilityStatus.text = status(isAccessibilityEnabled())
        if (Settings.canDrawOverlays(this) && isListenerEnabled()) {
            NotificationListenerService.requestRebind(ComponentName(this, MusicListenerService::class.java))
        }
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(36))
        }
        root.addView(TextView(this).apply {
            text = "Music Capsule"
            textSize = 34f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapMatch().apply { bottomMargin = dp(8) })
        root.addView(TextView(this).apply {
            text = "Музыкальный остров вокруг камеры"
            textSize = 16f
            alpha = .72f
        }, wrapMatch().apply { bottomMargin = dp(8) })

        root.addView(sectionTitle("Разрешения"))
        overlayStatus = TextView(this)
        root.addView(permissionCard("Поверх других приложений", "Разрешает показывать капсулу поверх интерфейса.", overlayStatus, "Открыть") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        mediaStatus = TextView(this)
        root.addView(permissionCard("Доступ к музыке", "Используется только для чтения активной MediaSession и управления воспроизведением.", mediaStatus, "Открыть") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        accessibilityStatus = TextView(this)
        root.addView(permissionCard("Интерактивный остров", "Нужен для жестов над строкой состояния. Содержимое экрана не читается.", accessibilityStatus, "Открыть") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(sectionTitle("Положение"))
        root.addView(settingCard("Вертикальное смещение", "Можно также удержать компактный остров и потянуть его пальцем.") {
            val slider = Slider(this@MainActivity).apply {
                val maxOffsetDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density / 4f).coerceAtLeast(50f)
                valueFrom = 0f; valueTo = maxOffsetDp.toInt().toFloat(); stepSize = 1f
                value = CapsulePreferences.verticalOffsetDp(this@MainActivity).toFloat().coerceIn(0f, valueTo)
                addOnChangeListener { _, newValue, fromUser ->
                    if (fromUser) {
                        val valueDp = newValue.toInt()
                        CapsulePreferences.setVerticalOffsetDp(this@MainActivity, valueDp)
                        MusicOverlayBridge.applyVerticalOffsetDp(valueDp)
                    }
                }
            }
            addView(slider, matchWrap(dp(52)))
        })

        root.addView(sectionTitle("Поведение"))
        root.addView(settingCard("Управление музыкой", "Выберите жесты, обычные кнопки или только системное управление.") {
            val group = MaterialButtonToggleGroup(this@MainActivity).apply {
                isSingleSelection = true
                isSelectionRequired = true
            }
            val gestures = modeButton("Жесты")
            val buttons = modeButton("Кнопки")
            val system = modeButton("Системное")
            group.addView(gestures); group.addView(buttons); group.addView(system)
            val selected = when (CapsulePreferences.controlMode(this@MainActivity)) {
                CapsulePreferences.ControlMode.GESTURES -> gestures.id
                CapsulePreferences.ControlMode.BUTTONS -> buttons.id
                CapsulePreferences.ControlMode.SYSTEM -> system.id
            }
            group.check(selected)
            group.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked) return@addOnButtonCheckedListener
                val mode = when (checkedId) {
                    buttons.id -> CapsulePreferences.ControlMode.BUTTONS
                    system.id -> CapsulePreferences.ControlMode.SYSTEM
                    else -> CapsulePreferences.ControlMode.GESTURES
                }
                CapsulePreferences.setControlMode(this@MainActivity, mode)
                MusicOverlayBridge.applyControlMode(mode)
            }
            addView(group, wrapMatch().apply { topMargin = dp(8) })
        })
        root.addView(settingCard("Автосворачивание", "Время после последнего касания.") {
            val valueLabel = TextView(this@MainActivity).apply { gravity = Gravity.CENTER; textSize = 15f }
            val slider = Slider(this@MainActivity).apply {
                valueFrom = 1.5f; valueTo = 5f; stepSize = .5f
                value = (CapsulePreferences.collapseDelayMs(this@MainActivity) / 1000f).coerceIn(1.5f, 5f)
                addOnChangeListener { _, newValue, fromUser ->
                    valueLabel.text = "${newValue} с"
                    if (fromUser) CapsulePreferences.setCollapseDelayMs(this@MainActivity, (newValue * 1000).toLong())
                }
            }
            valueLabel.text = "${slider.value} с"
            addView(valueLabel, matchWrap(dp(32)))
            addView(slider, matchWrap(dp(52)))
        })

        root.addView(sectionTitle("Подсказка"))
        root.addView(infoCard("В режиме жестов: свайп влево или вправо переключает трек, свайп вверх сворачивает остров. Долгое удержание компактного острова включает перемещение."))

        root.addView(sectionTitle("Безопасность"))
        root.addView(settingCard("Сертификат установленного APK", "Сверяйте этот SHA-256 с отпечатком в шаге Verify signature and certificate на GitHub Actions.") {
            addView(TextView(this@MainActivity).apply {
                text = signingCertificateSha256()
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTextIsSelectable(true)
            }, wrapMatch().apply { topMargin = dp(8) })
        })

        return ScrollView(this).apply { isFillViewport = true; addView(root) }
    }

    private fun permissionCard(title: String, body: String, statusView: TextView, buttonText: String, action: () -> Unit): View =
        settingCard(title, body) {
            statusView.textSize = 14f; statusView.alpha = .8f
            addView(statusView, wrapMatch().apply { topMargin = dp(10); bottomMargin = dp(8) })
            addView(MaterialButton(this@MainActivity).apply { text = buttonText; setOnClickListener { action() } }, matchWrap(dp(50)))
        }

    private fun settingCard(title: String, body: String, content: LinearLayout.() -> Unit): MaterialCardView =
        MaterialCardView(this).apply {
            radius = dp(24).toFloat(); cardElevation = 0f
            strokeWidth = dp(1)
            val column = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
                addView(TextView(this@MainActivity).apply { text = title; textSize = 18f; setTypeface(typeface, android.graphics.Typeface.BOLD) }, wrapMatch())
                addView(TextView(this@MainActivity).apply { text = body; textSize = 14f; alpha = .7f }, wrapMatch().apply { topMargin = dp(6); bottomMargin = dp(4) })
                content()
            }
            addView(column)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }

    private fun infoCard(textValue: String): View = settingCard("Управление", textValue) {}
    private fun modeButton(label: String) = MaterialButton(this).apply {
        id = View.generateViewId(); text = label; isCheckable = true
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
        setPadding(dp(4), 0, dp(4), 0)
        textSize = 12f
    }
    private fun sectionTitle(value: String) = TextView(this).apply { text = value; textSize = 14f; alpha = .7f; setPadding(dp(4), dp(18), 0, dp(10)) }
    private fun status(enabled: Boolean) = if (enabled) "● Включено" else "○ Требуется доступ"
    private fun isListenerEnabled() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
    private fun isAccessibilityEnabled() = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.contains("$packageName/${CapsuleAccessibilityService::class.java.name}") == true
    @Suppress("DEPRECATION")
    private fun signingCertificateSha256(): String = runCatching {
        val info = if (android.os.Build.VERSION.SDK_INT >= 28) {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                .signatures?.firstOrNull()
        } ?: return@runCatching "Сертификат недоступен"
        MessageDigest.getInstance("SHA-256").digest(info.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrElse { "Не удалось прочитать сертификат" }
    private fun matchWrap(height: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    private fun wrapMatch() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
