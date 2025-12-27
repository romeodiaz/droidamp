package com.droidamp.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.droidamp.DroidAmpApplication
import com.droidamp.MainActivity

class AudioService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var playerManager: PlayerManager

    override fun onCreate() {
        super.onCreate()

        playerManager = (application as DroidAmpApplication).playerManager

        // Create pending intent for notification tap
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        super.onDestroy()
    }
}


