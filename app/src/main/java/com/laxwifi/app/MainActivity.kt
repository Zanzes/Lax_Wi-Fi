package com.laxwifi.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.laxwifi.app.ui.EditScreen
import com.laxwifi.app.ui.HomeScreen
import com.laxwifi.app.ui.LaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        MonitorService.start(this)

        setContent {
            LaxTheme {
                var editingId by rememberSaveable { mutableStateOf<String?>(null) }
                val id = editingId
                if (id == null) {
                    HomeScreen(onEdit = { editingId = it })
                } else {
                    BackHandler { editingId = null }
                    EditScreen(networkId = id, onBack = { editingId = null })
                }
            }
        }
    }
}
