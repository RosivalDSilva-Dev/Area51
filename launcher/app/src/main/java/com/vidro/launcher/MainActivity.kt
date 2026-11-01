package com.vidro.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import com.vidro.launcher.data.AppRepository
import com.vidro.launcher.data.LauncherState
import com.vidro.launcher.ui.LauncherRoot

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var state: LauncherState
    private val homeTicks = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        repo = AppRepository(applicationContext)
        state = LauncherState(applicationContext)
        repo.start()
        setContent {
            LauncherRoot(repo, state, homeTicks.intValue)
        }
    }

    // Botão "início" com o launcher já aberto: fecha gaveta e menus
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homeTicks.intValue++
        }
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }
}
