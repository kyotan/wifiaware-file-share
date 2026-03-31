package com.example.wifiaware

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wifiaware.ui.WiFiAwareApp
import com.example.wifiaware.ui.WiFiAwareTheme
import com.example.wifiaware.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WiFiAwareTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var requestedOnce by remember { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    viewModel.onPermissionsResult(
                        nearbyGranted = result[Manifest.permission.NEARBY_WIFI_DEVICES] == true ||
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU,
                        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true,
                    )
                }
                val documentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    uri?.let(viewModel::onFileSelected)
                }
                val folderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    if (uri != null) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        viewModel.onReceiveFolderSelected(uri)
                    }
                }

                WiFiAwareApp(
                    state = state,
                    onRoleSelected = viewModel::onRoleSelected,
                    onStartDiscovery = viewModel::onStartDiscovery,
                    onRetryPermissions = {
                        viewModel.onRetryPermissions()
                        requestedOnce = true
                        permissionLauncher.launch(requiredPermissions())
                    },
                    onToggleTrustedOnly = viewModel::onToggleTrustedOnly,
                    onSelectPeer = viewModel::onPeerSelected,
                    onPickFile = { documentLauncher.launch(arrayOf("*/*")) },
                    onPickReceiveFolder = { folderLauncher.launch(null) },
                    onPassphraseChanged = viewModel::onPassphraseChanged,
                    onAcceptIncomingTransfer = viewModel::onAcceptIncomingTransfer,
                    onRejectIncomingTransfer = viewModel::onRejectIncomingTransfer,
                    onConfirmPairing = viewModel::onPairingConfirmed,
                    onCancelTransfer = viewModel::onCancelTransfer,
                    hasRequestedPermissions = requestedOnce,
                )
            }
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
    add(Manifest.permission.ACCESS_FINE_LOCATION)
}.toTypedArray()
