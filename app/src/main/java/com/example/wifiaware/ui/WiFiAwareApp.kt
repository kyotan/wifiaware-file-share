package com.example.wifiaware.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.wifiaware.ui.home.HomeRoute
import com.example.wifiaware.ui.model.HomeUiState
import com.example.wifiaware.ui.model.UserRole

@Composable
fun WiFiAwareApp(
    state: HomeUiState,
    onRoleSelected: (UserRole) -> Unit,
    onStartDiscovery: () -> Unit,
    onRetryPermissions: () -> Unit,
    onToggleTrustedOnly: (Boolean) -> Unit,
    onSelectPeer: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickReceiveFolder: () -> Unit,
    onPassphraseChanged: (String) -> Unit,
    onAcceptIncomingTransfer: () -> Unit,
    onRejectIncomingTransfer: () -> Unit,
    onConfirmPairing: () -> Unit,
    onCancelTransfer: () -> Unit,
    hasRequestedPermissions: Boolean,
) {
    val colors = androidx.compose.material3.MaterialTheme.colorScheme

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            colors.surface,
                            colors.surfaceContainerLowest,
                            colors.secondaryContainer.copy(alpha = 0.55f),
                        ),
                    ),
                )
                .padding(top = 8.dp),
        ) {
            HomeRoute(
                state = state,
                onRoleSelected = onRoleSelected,
                onStartDiscovery = onStartDiscovery,
                onRetryPermissions = onRetryPermissions,
                onToggleTrustedOnly = onToggleTrustedOnly,
                onSelectPeer = onSelectPeer,
                onPickFile = onPickFile,
                onPickReceiveFolder = onPickReceiveFolder,
                onPassphraseChanged = onPassphraseChanged,
                onAcceptIncomingTransfer = onAcceptIncomingTransfer,
                onRejectIncomingTransfer = onRejectIncomingTransfer,
                onConfirmPairing = onConfirmPairing,
                onCancelTransfer = onCancelTransfer,
                hasRequestedPermissions = hasRequestedPermissions,
            )
        }
    }
}
