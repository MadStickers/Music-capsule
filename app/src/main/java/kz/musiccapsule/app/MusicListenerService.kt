package kz.musiccapsule.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

class MusicListenerService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var sessions: MediaSessionManager
    private var controller: MediaController? = null
    private var lastPlaying = false
    private var pauseHideScheduled = false

    private val hideAfterPause = Runnable {
        pauseHideScheduled = false
        MusicOverlayBridge.hide()
    }
    private val progressTick = object : Runnable {
        override fun run() {
            chooseSession()
            main.postDelayed(this, 500L)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishState()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishState()
        override fun onSessionDestroyed() = chooseSession()
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { chooseSession(it) }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessions.addOnActiveSessionsChangedListener(sessionListener, ComponentName(this, javaClass))
        chooseSession()
        main.post(progressTick)
    }

    override fun onListenerDisconnected() {
        controller?.unregisterCallback(controllerCallback)
        if (::sessions.isInitialized) sessions.removeOnActiveSessionsChangedListener(sessionListener)
        main.removeCallbacksAndMessages(null)
        MusicOverlayBridge.hide()
        super.onListenerDisconnected()
    }

    private fun chooseSession(list: List<MediaController>? = null) {
        val available = list ?: runCatching {
            sessions.getActiveSessions(ComponentName(this, javaClass))
        }.getOrDefault(emptyList())
        val next = available
            .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: available.firstOrNull { it.metadata != null }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = next
            controller?.registerCallback(controllerCallback, main)
        }
        publishState()
    }

    private fun publishState() {
        val current = controller ?: run {
            main.removeCallbacks(hideAfterPause)
            pauseHideScheduled = false
            lastPlaying = false
            MusicOverlayBridge.hide()
            return
        }
        val metadata = current.metadata
        val playback = current.playbackState
        val audioActive = (getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING && audioActive
        val position = if (isPlaying && playback != null) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
            (playback.position + elapsed * playback.playbackSpeed).toLong().coerceAtLeast(0L)
        } else playback?.position ?: 0L
        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val state = MusicState(
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Неизвестный трек",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            artwork = art,
            position = position,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            playing = isPlaying
        )
        MusicOverlayBridge.update(state, current.transportControls)
        if (isPlaying) {
            main.removeCallbacks(hideAfterPause)
            pauseHideScheduled = false
            MusicOverlayBridge.show()
        } else if (lastPlaying && !pauseHideScheduled) {
            pauseHideScheduled = true
            main.postDelayed(hideAfterPause, 5_000L)
        }
        lastPlaying = isPlaying
    }
}
