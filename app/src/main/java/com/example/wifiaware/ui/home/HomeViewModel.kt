package com.example.wifiaware.ui.home

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wifiaware.aware.AndroidWifiAwareController
import com.example.wifiaware.aware.WifiAwareRuntimeState
import com.example.wifiaware.transfer.SelectedFileAnalyzer
import com.example.wifiaware.transfer.TransferOffer
import com.example.wifiaware.ui.model.DiscoveredPeer
import com.example.wifiaware.ui.model.HomeUiState
import com.example.wifiaware.ui.model.PeerTrustState
import com.example.wifiaware.ui.model.SelectedFileUiModel
import com.example.wifiaware.ui.model.TransferStatus
import com.example.wifiaware.ui.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val controller = AndroidWifiAwareController(application.applicationContext)
    private val fileAnalyzer = SelectedFileAnalyzer(application.contentResolver)
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var latestRuntimeState = WifiAwareRuntimeState()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        restoreReceiveDestination()
        controller.start()
        viewModelScope.launch {
            controller.state.collectLatest(::applyRuntimeState)
        }
    }

    fun onRoleSelected(role: UserRole) {
        _uiState.update { it.copy(selectedRole = role) }
    }

    fun onStartDiscovery() {
        controller.startDiscovery(_uiState.value.selectedRole.name)
    }

    fun onRetryPermissions() {
        controller.refreshPermissionState()
        _uiState.update {
            it.copy(
                statusHeadline = "Permission request triggered",
                statusBody = "Grant permission to continue.",
            )
        }
    }

    fun onPermissionsResult(nearbyGranted: Boolean, locationGranted: Boolean) {
        controller.refreshPermissionState()
        _uiState.update {
            it.copy(
                nearbyPermissionGranted = nearbyGranted,
                locationPermissionGranted = locationGranted,
            )
        }
    }

    fun onToggleTrustedOnly(enabled: Boolean) {
        _uiState.update { it.copy(trustedOnly = enabled) }
        applyRuntimeState(latestRuntimeState)
    }

    fun onPeerSelected(peerId: String) {
        _uiState.update { state ->
            val selected = state.peers.firstOrNull { it.id == peerId }
            state.copy(
                selectedPeerId = peerId,
                statusHeadline = selected?.let { "Selected ${it.name}" } ?: state.statusHeadline,
                statusBody = selected?.detail ?: state.statusBody,
                transferStatus = if (selected == null) {
                    state.transferStatus
                } else {
                    TransferStatus(
                        label = "Ready to pair",
                        fileName = "pending-transfer.bin",
                        progress = 0.05f,
                        speedText = "Awaiting secure setup",
                        etaText = "Waiting for peer confirmation",
                    )
                },
            )
        }
    }

    fun onPairingConfirmed() {
        val snapshot = _uiState.value
        val peerId = snapshot.selectedPeerId ?: return
        val file = snapshot.selectedFile ?: return
        controller.updatePassphrase(snapshot.pairingPassphrase)
        controller.startOutgoingTransfer(
            peerId = peerId,
            file = com.example.wifiaware.transfer.SelectedFileDescriptor(
                uri = file.uri,
                displayName = file.displayName,
                mimeType = file.mimeType,
                sizeBytes = file.sizeBytes,
                sha256 = file.sha256,
                transferId = file.transferId,
            ),
            offer = TransferOffer(
                transferId = file.transferId,
                fileName = file.displayName,
                mimeType = file.mimeType,
                sizeBytes = file.sizeBytes,
                sha256 = file.sha256,
            ),
        )
        _uiState.update {
            it.copy(
                pairingReady = true,
                transferStatus = TransferStatus(
                    label = "Transfer bootstrap sent",
                    fileName = it.selectedFile?.displayName ?: "pending-transfer.bin",
                    progress = 0.24f,
                    speedText = "Negotiating secure link",
                    etaText = "Waiting for peer",
                ),
                statusHeadline = "Transfer bootstrap sent",
                statusBody = "Negotiating secure link.",
            )
        }
    }

    fun onCancelTransfer() {
        _uiState.update {
            it.copy(
                pairingReady = false,
                transferStatus = it.transferStatus.copy(
                    label = "Transfer cancelled",
                    progress = 0f,
                    speedText = "Stopped",
                    etaText = "Ready",
                ),
            )
        }
    }

    fun onPassphraseChanged(value: String) {
        controller.updatePassphrase(value)
        _uiState.update { it.copy(pairingPassphrase = value) }
    }

    fun onAcceptIncomingTransfer() {
        controller.acceptIncomingTransfer()
    }

    fun onReceiveFolderSelected(uri: Uri) {
        controller.updateReceiveDestination(uri)
        val label = buildFolderLabel(uri)
        preferences.edit()
            .putString(KEY_RECEIVE_FOLDER_URI, uri.toString())
            .putString(KEY_RECEIVE_FOLDER_LABEL, label)
            .apply()
        _uiState.update {
            it.copy(
                receiveFolderLabel = label,
                receiveFolderConfigured = true,
                statusHeadline = "Receive folder ready",
                statusBody = "Save to $label",
            )
        }
    }

    fun onRejectIncomingTransfer() {
        controller.rejectIncomingTransfer()
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        statusHeadline = "Analyzing selected file",
                        statusBody = "Preparing file.",
                    )
                }
            runCatching {
                withContext(Dispatchers.IO) {
                    fileAnalyzer.analyze(uri)
                }
            }.onSuccess { descriptor ->
                _uiState.update {
                    it.copy(
                selectedFile = SelectedFileUiModel(
                            uri = descriptor.uri,
                            displayName = descriptor.displayName,
                            mimeType = descriptor.mimeType,
                            sizeBytes = descriptor.sizeBytes,
                            sha256 = descriptor.sha256,
                            transferId = descriptor.transferId,
                        ),
                        transferStatus = it.transferStatus.copy(
                            fileName = descriptor.displayName,
                            speedText = "File ready",
                            etaText = "${descriptor.sizeBytes} bytes",
                        ),
                        statusHeadline = "File ready for offer",
                        statusBody = "Choose a device to continue.",
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        statusHeadline = "Failed to analyze file",
                        statusBody = error.message ?: "The selected file could not be read.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        controller.stop()
        super.onCleared()
    }

    private fun restoreReceiveDestination() {
        val savedUri = preferences.getString(KEY_RECEIVE_FOLDER_URI, null)?.let(Uri::parse)
        val savedLabel = preferences.getString(KEY_RECEIVE_FOLDER_LABEL, null)
        controller.updateReceiveDestination(savedUri)
        if (savedUri != null && savedLabel != null) {
            _uiState.update {
                it.copy(
                    receiveFolderLabel = savedLabel,
                    receiveFolderConfigured = true,
                )
            }
        }
    }

    private fun buildFolderLabel(uri: Uri): String {
        return runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(uri)
            treeId.substringAfterLast(':').ifBlank { "Selected folder" }
        }.getOrDefault("Selected folder")
    }

    private fun applyRuntimeState(runtime: WifiAwareRuntimeState) {
        latestRuntimeState = runtime
        _uiState.update { current ->
            val mappedPeers = runtime.peers.map { peer ->
                DiscoveredPeer(
                    id = peer.id,
                    name = peer.name,
                    distanceHint = peer.distanceHint,
                    detail = peer.detail,
                    trustState = if (peer.trusted) PeerTrustState.TRUSTED else PeerTrustState.NEW,
                    signalLevel = peer.signalLevel,
                    isSelected = current.selectedPeerId == peer.id,
                )
            }.let { peers ->
                if (current.trustedOnly) peers.filter { it.trustState == PeerTrustState.TRUSTED } else peers
            }

            current.copy(
                awareSupported = runtime.featureSupported,
                awareAvailable = runtime.available,
                nearbyPermissionGranted = runtime.nearbyPermissionGranted,
                locationPermissionGranted = runtime.locationPermissionGranted,
                sessionAttached = runtime.sessionAttached,
                publishStarted = runtime.publishStarted,
                subscribeStarted = runtime.subscribeStarted,
                dataPathRequested = runtime.dataPathRequested,
                dataPathAvailable = runtime.dataPathAvailable,
                isDiscovering = runtime.isDiscovering,
                statusHeadline = runtime.statusHeadline,
                statusBody = runtime.statusBody,
                incomingTransfer = runtime.incomingTransfer,
                logs = runtime.logs,
                latestSavedFilePath = runtime.latestSavedFilePath,
                peers = mappedPeers,
                selectedPeerId = current.selectedPeerId?.takeIf { selectedId ->
                    mappedPeers.any { it.id == selectedId }
                },
                transferStatus = current.transferStatus.copy(
                    speedText = when {
                        runtime.dataPathAvailable -> "Secure link available"
                        runtime.dataPathRequested -> "Negotiating link"
                        runtime.publishStarted && runtime.subscribeStarted -> "Discovery active"
                        runtime.sessionAttached -> "Session attached"
                        else -> current.transferStatus.speedText
                    },
                ),
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "wifiaware_prefs"
        const val KEY_RECEIVE_FOLDER_URI = "receive_folder_uri"
        const val KEY_RECEIVE_FOLDER_LABEL = "receive_folder_label"
    }
}
