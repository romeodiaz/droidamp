package com.droidamp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.droidamp.player.AudioService
import com.droidamp.ui.screens.NowPlayingScreen
import com.droidamp.ui.screens.SearchScreen
import com.droidamp.ui.theme.DroidAmpTheme
import com.droidamp.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Permission result - we continue regardless
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Start the audio service
        startService(Intent(this, AudioService::class.java))

        val app = application as DroidAmpApplication
        val viewModel = PlayerViewModel(app.playerManager)

        setContent {
            DroidAmpTheme {
                var showSearch by remember { mutableStateOf(false) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showSearch) {
                        SearchScreen(
                            viewModel = viewModel,
                            onBack = { showSearch = false }
                        )
                    } else {
                        NowPlayingScreen(
                            viewModel = viewModel,
                            onSearchClick = { showSearch = true }
                        )
                    }
                }
            }
        }
    }
}


