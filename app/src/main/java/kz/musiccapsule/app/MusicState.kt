package kz.musiccapsule.app

import android.graphics.Bitmap

data class MusicState(
    val title: String = "Неизвестный трек",
    val artist: String = "",
    val artwork: Bitmap? = null,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playing: Boolean = false
)
