package com.confighub.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.confighub.android.ui.screens.HomeScreen
import com.confighub.android.ui.theme.ConfigHubTheme
import com.confighub.android.vpn.TunnelManager

class MainActivity : ComponentActivity() {
    private val tunnelManager = TunnelManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auto-update on open (collector rule): in the full app this
        // triggers one collector run here. In this skeleton there is no
        // real fetch yet, so nothing is fetched at this point.
        setContent {
            ConfigHubTheme {
                HomeScreen(tunnelManager)
            }
        }
    }
}
