package kz.musiccapsule.app

data class TimerState(
    val sourceKey: String,
    val title: String,
    val value: String,
    val chronometerTimeMillis: Long? = null,
    val countDown: Boolean = false
)
