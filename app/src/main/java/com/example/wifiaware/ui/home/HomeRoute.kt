package com.example.wifiaware.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeviceHub
import androidx.compose.material.icons.rounded.FilePresent
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wifiaware.ui.model.DiscoveredPeer
import com.example.wifiaware.ui.model.HomeUiState
import com.example.wifiaware.ui.model.PeerTrustState
import com.example.wifiaware.ui.model.UserRole

@Composable
fun HomeRoute(
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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = paddingValues.calculateTopPadding() + 20.dp,
                end = 20.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroCard(
                    state = state,
                    onRoleSelected = onRoleSelected,
                    onStartDiscovery = onStartDiscovery,
                    onRetryPermissions = onRetryPermissions,
                    hasRequestedPermissions = hasRequestedPermissions,
                )
            }
            item {
                StatusStrip(state = state, onToggleTrustedOnly = onToggleTrustedOnly)
            }
            item {
                DiscoveryPanel(
                    state = state,
                    onStartDiscovery = onStartDiscovery,
                    onSelectPeer = onSelectPeer,
                    onPickFile = onPickFile,
                    onPickReceiveFolder = onPickReceiveFolder,
                    onPassphraseChanged = onPassphraseChanged,
                    onAcceptIncomingTransfer = onAcceptIncomingTransfer,
                    onRejectIncomingTransfer = onRejectIncomingTransfer,
                    onConfirmPairing = onConfirmPairing,
                )
            }
            item {
                TransferPanel(state = state, onCancelTransfer = onCancelTransfer)
            }
            item {
                DebugPanel(state = state)
            }
            item {
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }
}

@Composable
private fun HeroCard(
    state: HomeUiState,
    onRoleSelected: (UserRole) -> Unit,
    onStartDiscovery: () -> Unit,
    onRetryPermissions: () -> Unit,
    hasRequestedPermissions: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(32.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                        start = Offset.Zero,
                        end = Offset(1200f, 600f),
                    ),
                )
                .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "Nearby file transfer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                RoleSelector(
                    selectedRole = state.selectedRole,
                    onRoleSelected = onRoleSelected,
                )
                PermissionCallout(
                    state = state,
                    hasRequestedPermissions = hasRequestedPermissions,
                    onRetryPermissions = onRetryPermissions,
                )
            }
        }
    }
}

@Composable
private fun RoleSelector(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val options = UserRole.entries
        options.forEachIndexed { index, role ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onRoleSelected(role) },
                selected = selectedRole == role,
                label = {
                    Text(
                        if (role == UserRole.SEND) "Send flow" else "Receive flow",
                        maxLines = 1,
                    )
                },
                icon = {},
            )
        }
    }
}

@Composable
private fun PermissionCallout(
    state: HomeUiState,
    hasRequestedPermissions: Boolean,
    onRetryPermissions: () -> Unit,
) {
    val allGranted = state.nearbyPermissionGranted && state.locationPermissionGranted
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (allGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    contentDescription = null,
                )
                Column {
                    Text(
                        text = if (allGranted) "Permissions are ready" else "Nearby Wi-Fi access is required",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (allGranted) {
                            "Ready to scan."
                        } else if (hasRequestedPermissions) {
                            "Allow permission to continue."
                        } else {
                            "Grant permission first."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!allGranted) {
                FilledTonalButton(onClick = onRetryPermissions) {
                    Text("Grant access")
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(
    state: HomeUiState,
    onToggleTrustedOnly: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Radio",
            value = when {
                !state.awareSupported -> "Unsupported"
                state.awareAvailable -> "Available"
                else -> "Busy"
            },
            supporting = when {
                !state.awareSupported -> "Unsupported device"
                state.publishStarted && state.subscribeStarted -> "Scanning"
                state.sessionAttached -> "Ready"
                else -> state.statusHeadline
            },
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Link",
            value = when {
                state.dataPathAvailable -> "Connected"
                state.dataPathRequested -> "Negotiating"
                state.pairingReady -> "Ready"
                else -> "Idle"
            },
            supporting = if (state.trustedOnly) "Trusted peers only" else "All peers visible",
            trailing = {
                FilterChip(
                    selected = state.trustedOnly,
                    onClick = { onToggleTrustedOnly(!state.trustedOnly) },
                    label = { Text("Trusted only") },
                )
            },
        )
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    supporting: String,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun DiscoveryPanel(
    state: HomeUiState,
    onStartDiscovery: () -> Unit,
    onSelectPeer: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickReceiveFolder: () -> Unit,
    onPassphraseChanged: (String) -> Unit,
    onAcceptIncomingTransfer: () -> Unit,
    onRejectIncomingTransfer: () -> Unit,
    onConfirmPairing: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Nearby devices", style = MaterialTheme.typography.titleLarge)
                FilledTonalButton(
                    onClick = onStartDiscovery,
                ) {
                    Icon(Icons.Rounded.DeviceHub, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
            FilePreparationPanel(
                state = state,
                onPickFile = onPickFile,
                onPickReceiveFolder = onPickReceiveFolder,
                onPassphraseChanged = onPassphraseChanged,
            )
            state.incomingTransfer?.let { request ->
                IncomingTransferCard(
                    request = request,
                    onAccept = onAcceptIncomingTransfer,
                    onReject = onRejectIncomingTransfer,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.peers.isEmpty()) {
                    EmptyPeersCard(
                        message = if (!state.awareSupported) {
                            "This device does not support Wi-Fi Aware."
                        } else if (!state.awareAvailable) {
                            "Wi-Fi Aware is unavailable."
                        } else if (!state.nearbyPermissionGranted || !state.locationPermissionGranted) {
                            "Grant permission, then scan."
                        } else {
                            "No devices found."
                        },
                    )
                } else {
                    state.peers.forEach { peer ->
                        PeerRow(
                            peer = peer.copy(isSelected = peer.id == state.selectedPeerId),
                            onSelectPeer = onSelectPeer,
                        )
                    }
                }
            }
            AnimatedVisibility(visible = state.selectedPeerId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = onConfirmPairing) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.selectedRole == UserRole.SEND) "Send file" else "Request secure link")
                    }
                }
            }
        }
    }
}

@Composable
private fun IncomingTransferCard(
    request: com.example.wifiaware.aware.IncomingTransferRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Incoming transfer", style = MaterialTheme.typography.titleMedium)
            Text(
                "${request.senderName} wants to send ${request.fileName} (${request.sizeBytes} bytes).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = onAccept) {
                    Text("Accept")
                }
                OutlinedButton(onClick = onReject) {
                    Text("Reject")
                }
            }
        }
    }
}

@Composable
private fun FilePreparationPanel(
    state: HomeUiState,
    onPickFile: () -> Unit,
    onPickReceiveFolder: () -> Unit,
    onPassphraseChanged: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.FilePresent, contentDescription = null)
                Column {
                    Text(if (state.selectedRole == UserRole.SEND) "File" else "Save location", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (state.selectedRole == UserRole.SEND) {
                FilledTonalButton(onClick = onPickFile) {
                    Text(if (state.selectedFile == null) "Choose file" else "Choose another file")
                }
                state.selectedFile?.let { selectedFile ->
                    Text(
                        text = "${selectedFile.displayName} • ${selectedFile.sizeBytes} bytes",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "SHA-256 ${selectedFile.sha256.take(20)}...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FilledTonalButton(onClick = onPickReceiveFolder) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.receiveFolderConfigured) {
                            "Change receive folder"
                        } else {
                            "Choose receive folder"
                        },
                    )
                }
                Text(
                    text = if (state.receiveFolderConfigured) {
                        "Save to ${state.receiveFolderLabel}"
                    } else {
                        "Save to ${state.receiveFolderLabel}"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.pairingPassphrase,
                onValueChange = onPassphraseChanged,
                label = { Text("Passphrase") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text("Use the same value on both devices.")
                },
                singleLine = true,
            )
        }
    }
}

@Composable
private fun EmptyPeersCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DeviceHub, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeerRow(
    peer: DiscoveredPeer,
    onSelectPeer: (String) -> Unit,
) {
    val containerColor = if (peer.isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        onClick = { onSelectPeer(peer.id) },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalBadge(level = peer.signalLevel)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(peer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TrustPill(peer.trustState)
                }
                Text(
                    text = peer.distanceHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = peer.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ArrowOutward, contentDescription = null)
        }
    }
}

@Composable
private fun TrustPill(trustState: PeerTrustState) {
    val (label, color) = when (trustState) {
        PeerTrustState.TRUSTED -> "Trusted" to MaterialTheme.colorScheme.tertiaryContainer
        PeerTrustState.RETURNING -> "Seen before" to MaterialTheme.colorScheme.primaryContainer
        PeerTrustState.NEW -> "New" to MaterialTheme.colorScheme.secondaryContainer
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SignalBadge(level: Float) {
    val signalColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            drawLine(
                color = signalColor,
                start = Offset(size.width * 0.15f, size.height * 0.85f),
                end = Offset(size.width * 0.15f, size.height * (1f - level)),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = signalColor,
                start = Offset(size.width * 0.5f, size.height * 0.85f),
                end = Offset(size.width * 0.5f, size.height * (1f - level * 0.88f)),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = signalColor,
                start = Offset(size.width * 0.85f, size.height * 0.85f),
                end = Offset(size.width * 0.85f, size.height * (1f - level * 0.74f)),
                strokeWidth = 10f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TransferPanel(
    state: HomeUiState,
    onCancelTransfer: () -> Unit,
) {
    val animatedProgress = animateFloatAsState(
        targetValue = state.transferStatus.progress,
        label = "transferProgress",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Transfer status", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.transferStatus.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onCancelTransfer) {
                    Icon(Icons.Rounded.PauseCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel")
                }
            }
            Text(
                state.transferStatus.fileName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            LinearProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(999.dp)),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AssistChip(onClick = { }, label = { Text(state.transferStatus.speedText) })
                AssistChip(onClick = { }, label = { Text(state.transferStatus.etaText) })
            }
            state.latestSavedFilePath?.let { path ->
                Text(
                    text = "Saved: $path",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DebugPanel(state: HomeUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(32.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null)
                Text("Logs", style = MaterialTheme.typography.titleLarge)
            }
            if (state.logs.isEmpty()) {
                Text(
                    "No logs yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.logs.take(12).forEach { entry ->
                        Text(
                            text = "${entry.timestamp}  ${entry.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
