package com.v2ray.ang.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.startVpn()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            FanoosTheme {
                MainScreen(
                    state = state,
                    onConnectClick = { onConnectClicked(state) },
                    onUpdateClick = { viewModel.updateSubscription() },
                    onPingClick = { viewModel.togglePingTest() },
                    onServerClick = { guid -> viewModel.selectServer(guid) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.syncServiceState()
    }

    private fun onConnectClicked(state: MainUiState) {
        if (state.isConnecting) return
        if (state.isConnected) {
            viewModel.stopVpn()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            viewModel.startVpn()
        } else {
            vpnPermissionLauncher.launch(intent)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
