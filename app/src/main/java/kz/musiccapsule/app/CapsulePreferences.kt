package kz.musiccapsule.app

import android.content.Context

object CapsulePreferences {
    private const val FILE = "music_capsule_settings"
    private const val KEY_OFFSET = "vertical_offset_dp"
    private const val KEY_COLLAPSE = "collapse_delay_ms"
    private const val KEY_CONTROL_MODE = "control_mode"

    enum class ControlMode { GESTURES, BUTTONS, SYSTEM }

    fun verticalOffsetDp(context: Context): Int =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_OFFSET, 0)

    fun setVerticalOffsetDp(context: Context, value: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_OFFSET, value).apply()
    }

    fun collapseDelayMs(context: Context): Long =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong(KEY_COLLAPSE, 2_000L)

    fun setCollapseDelayMs(context: Context, value: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong(KEY_COLLAPSE, value).apply()
    }

    fun controlMode(context: Context): ControlMode = runCatching {
        ControlMode.valueOf(context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY_CONTROL_MODE, ControlMode.GESTURES.name)!!)
    }.getOrDefault(ControlMode.GESTURES)

    fun setControlMode(context: Context, value: ControlMode) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_CONTROL_MODE, value.name).apply()
    }
}
