package com.droidamp

import android.app.Application
import com.droidamp.player.PlayerManager

class DroidAmpApplication : Application() {
    lateinit var playerManager: PlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManager(this)
    }
}


