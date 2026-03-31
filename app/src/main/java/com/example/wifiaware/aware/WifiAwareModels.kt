package com.example.wifiaware.aware

data class WifiAwareRuntimeState(
    val featureSupported: Boolean = false,
    val available: Boolean = false,
    val nearbyPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val sessionAttached: Boolean = false,
    val publishStarted: Boolean = false,
    val subscribeStarted: Boolean = false,
    val dataPathRequested: Boolean = false,
    val dataPathAvailable: Boolean = false,
    val isDiscovering: Boolean = false,
    val peers: List<AwarePeer> = emptyList(),
    val statusHeadline: String = "Checking Wi-Fi Aware support",
    val statusBody: String = "Verifying device capability, permission state, and service availability.",
    val incomingTransfer: IncomingTransferRequest? = null,
    val logs: List<DebugLogEntry> = emptyList(),
    val latestSavedFilePath: String? = null,
    val lastError: String? = null,
)

data class AwarePeer(
    val id: String,
    val name: String,
    val detail: String,
    val distanceHint: String,
    val signalLevel: Float,
    val trusted: Boolean = false,
)

data class IncomingTransferRequest(
    val peerId: String,
    val senderName: String,
    val fileName: String,
    val sizeBytes: Long,
    val transferId: String,
)

data class DebugLogEntry(
    val timestamp: String,
    val message: String,
)
