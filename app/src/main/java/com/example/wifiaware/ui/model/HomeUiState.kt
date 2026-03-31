package com.example.wifiaware.ui.model

import android.net.Uri
import com.example.wifiaware.aware.DebugLogEntry
import com.example.wifiaware.aware.IncomingTransferRequest

data class HomeUiState(
    val selectedRole: UserRole = UserRole.SEND,
    val awareSupported: Boolean = false,
    val awareAvailable: Boolean = false,
    val nearbyPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val sessionAttached: Boolean = false,
    val publishStarted: Boolean = false,
    val subscribeStarted: Boolean = false,
    val dataPathRequested: Boolean = false,
    val dataPathAvailable: Boolean = false,
    val isDiscovering: Boolean = false,
    val pairingReady: Boolean = false,
    val trustedOnly: Boolean = false,
    val statusHeadline: String = "Ready",
    val statusBody: String = "Scan to find nearby devices.",
    val peers: List<DiscoveredPeer> = emptyList(),
    val selectedPeerId: String? = "peer-1",
    val selectedFile: SelectedFileUiModel? = null,
    val receiveFolderLabel: String = "App private Downloads",
    val receiveFolderConfigured: Boolean = false,
    val pairingPassphrase: String = "aware-demo-pass",
    val incomingTransfer: IncomingTransferRequest? = null,
    val logs: List<DebugLogEntry> = emptyList(),
    val latestSavedFilePath: String? = null,
    val transferStatus: TransferStatus = TransferStatus(
        label = "Waiting",
        fileName = "No file selected",
        progress = 0f,
        speedText = "Idle",
        etaText = "Select a device",
    ),
)

enum class UserRole {
    SEND,
    RECEIVE,
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val distanceHint: String,
    val detail: String,
    val trustState: PeerTrustState,
    val isSelected: Boolean = false,
    val signalLevel: Float,
)

enum class PeerTrustState {
    TRUSTED,
    RETURNING,
    NEW,
}

data class TransferStatus(
    val label: String,
    val fileName: String,
    val progress: Float,
    val speedText: String,
    val etaText: String,
)

data class SelectedFileUiModel(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val transferId: String,
)
