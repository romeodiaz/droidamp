package com.droidamp

import android.app.Application
import com.droidamp.data.SearchHistoryManager
import com.droidamp.player.PlayerManager

class DroidAmpApplication : Application() {
    lateinit var playerManager: PlayerManager
        private set
    
    lateinit var searchHistoryManager: SearchHistoryManager
        private set

    override fun onCreate() {
        super.onCreate()
        playerManager = PlayerManager(this)
        searchHistoryManager = SearchHistoryManager(this)
    }
}


