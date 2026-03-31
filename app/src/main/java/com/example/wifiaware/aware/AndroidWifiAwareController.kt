package com.example.wifiaware.aware

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.ServiceDiscoveryInfo
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wifiaware.transfer.SelectedFileDescriptor
import com.example.wifiaware.transfer.SocketTransferEngine
import com.example.wifiaware.transfer.TransferBootstrapMessage
import com.example.wifiaware.transfer.TransferOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class AndroidWifiAwareController(
    private val appContext: Context,
) {
    private val wifiAwareManager: WifiAwareManager? =
        appContext.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private val connectivityManager: ConnectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val discoveredPeers = ConcurrentHashMap<String, AwarePeer>()
    private val peerHandles = ConcurrentHashMap<String, PeerHandle>()
    private val peerSessionOrigins = ConcurrentHashMap<String, SessionOrigin>()
    private val transferEngine = SocketTransferEngine(appContext, connectivityManager)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(WifiAwareRuntimeState())
    val state: StateFlow<WifiAwareRuntimeState> = _state

    private var receiverRegistered = false
    private var awareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var activeNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentPassphrase: String = "aware-demo-pass"
    private var receiveDestinationTreeUri: Uri? = null
    private var nextMessageId: Int = 1
    private var pendingOutgoingTransfer: PendingOutgoingTransfer? = null
    private var pendingIncomingTransfer: PendingIncomingTransfer? = null
    private var activeServerTransferId: String? = null
    private var activeClientTransferId: String? = null
    private var intentionalSessionReset: Boolean = false

    private val availabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) {
                val availableNow = runCatching { wifiAwareManager?.isAvailable == true }.getOrDefault(false)
                if (!availableNow) {
                    closeSessions()
                }
                _state.update {
                    it.copy(
                        available = availableNow,
                        sessionAttached = availableNow && awareSession != null,
                        publishStarted = availableNow && publishSession != null,
                        subscribeStarted = availableNow && subscribeSession != null,
                        statusHeadline = if (availableNow) {
                            "Wi-Fi Aware is available"
                        } else {
                            "Wi-Fi Aware is currently unavailable"
                        },
                        statusBody = if (availableNow) {
                            "You can attach a session and start publish/subscribe discovery."
                        } else {
                            "Wi-Fi, firmware state, or system resources are preventing Wi-Fi Aware right now."
                        },
                    )
                }
            }
        }
    }

    fun start() {
        registerReceiverIfNeeded()
        refreshCapability()
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        val nearbyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        val locationGranted =
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        _state.update {
            it.copy(
                nearbyPermissionGranted = nearbyGranted,
                locationPermissionGranted = locationGranted,
                statusHeadline = when {
                    !it.featureSupported -> "Wi-Fi Aware not supported on this device"
                    !nearbyGranted || !locationGranted -> "Permission is required before discovery"
                    it.available -> "Ready to start discovery"
                    else -> it.statusHeadline
                },
                statusBody = when {
                    !it.featureSupported -> "This APK can still open, but discovery and direct transfer need hardware support."
                    !nearbyGranted || !locationGranted -> "Grant nearby Wi-Fi access so attach, publish, and subscribe can start safely."
                    it.available -> "Press scan to attach a session and begin dual-role discovery."
                    else -> it.statusBody
                },
            )
        }
    }

    fun startDiscovery(roleLabel: String) {
        refreshCapability()
        refreshPermissionState()

        val snapshot = _state.value
        if (!snapshot.featureSupported) {
            emitError(
                headline = "Wi-Fi Aware is not supported",
                body = "This device does not expose PackageManager.FEATURE_WIFI_AWARE.",
            )
            return
        }
        if (!snapshot.nearbyPermissionGranted || !snapshot.locationPermissionGranted) {
            emitError(
                headline = "Permission is missing",
                body = "Nearby Wi-Fi permission must be granted before attach and discovery.",
            )
            return
        }
        if (!snapshot.available) {
            emitError(
                headline = "Wi-Fi Aware is unavailable",
                body = "Turn on Wi-Fi and ensure no conflicting mode is blocking Aware.",
            )
            return
        }

        if (awareSession != null) {
            launchPublishAndSubscribe(roleLabel)
            return
        }

        try {
            _state.update {
                it.copy(
                    isDiscovering = true,
                    statusHeadline = "Attaching Wi-Fi Aware session",
                    statusBody = "Waiting for the platform to confirm the device joined or created an Aware cluster.",
                    lastError = null,
                )
            }
            wifiAwareManager?.attach(
                object : AttachCallback() {
                    override fun onAttached(session: WifiAwareSession) {
                        awareSession = session
                        _state.update {
                            it.copy(
                                sessionAttached = true,
                                isDiscovering = true,
                                statusHeadline = "Wi-Fi Aware session attached",
                                statusBody = "Starting publish and subscribe discovery with the shared service identifier.",
                                lastError = null,
                            )
                        }
                        launchPublishAndSubscribe(roleLabel)
                    }

                    override fun onAttachFailed() {
                        emitError(
                            headline = "Attach failed",
                            body = "Android refused the Aware attach request. This can happen if the radio is busy or disabled.",
                        )
                    }
                },
                mainHandler,
            )
        } catch (securityException: SecurityException) {
            emitError(
                headline = "Attach blocked by permissions",
                body = securityException.message ?: "Android threw SecurityException while attaching to Wi-Fi Aware.",
            )
        }
    }

    fun stop() {
        closeSessions()
        unregisterReceiverIfNeeded()
        scope.cancel()
    }

    fun updatePassphrase(passphrase: String) {
        currentPassphrase = passphrase
        addLog("Passphrase updated")
    }

    fun updateReceiveDestination(treeUri: Uri?) {
        receiveDestinationTreeUri = treeUri
        addLog(
            if (treeUri == null) {
                "Receive destination reset to app private Downloads"
            } else {
                "Receive destination updated"
            },
        )
    }

    fun startOutgoingTransfer(
        peerId: String,
        file: SelectedFileDescriptor,
        offer: TransferOffer,
    ) {
        runCatching {
            val peerHandle = peerHandles[peerId]
            if (peerHandle == null) {
                emitError(
                    headline = "Peer no longer available",
                    body = "Select a currently discovered peer before starting transfer.",
                )
                return
            }

            pendingOutgoingTransfer = PendingOutgoingTransfer(
                peerId = peerId,
                file = file,
                offer = offer,
            )
            addLog("Preparing outgoing transfer for ${offer.fileName} (${offer.sizeBytes} bytes)")
            sendTransferBootstrap(
                peerHandle,
                TransferBootstrapMessage.TransferRequest(
                    senderName = Build.MODEL ?: "Android",
                    transferId = offer.transferId,
                ),
            )
            _state.update {
                it.copy(
                    dataPathRequested = false,
                    dataPathAvailable = false,
                    statusHeadline = "Transfer request sent",
                    statusBody = "Waiting for the receiver to accept before negotiating the secure Wi-Fi Aware link.",
                    lastError = null,
                )
            }
        }.onFailure { error ->
            emitUnexpectedError("Outgoing transfer setup crashed", error)
        }
    }

    fun acceptIncomingTransfer() {
        runCatching {
            val pending = pendingIncomingTransfer ?: return
            addLog("Incoming transfer accepted")
            _state.update { it.copy(incomingTransfer = null) }
            val peerHandle = peerHandles[pending.peerId]
            if (peerHandle == null) {
                emitError(
                    headline = "Peer no longer available",
                    body = "The sender disappeared before the transfer could be accepted.",
                )
                return
            }
            sendTransferBootstrap(
                peerHandle,
                TransferBootstrapMessage.TransferAccept(
                    transferId = pending.transferId,
                ),
            )
            requestDataPath(pending.peerId, currentPassphrase, serverMode = false)
        }.onFailure { error ->
            emitUnexpectedError("Incoming transfer accept crashed", error)
        }
    }

    fun rejectIncomingTransfer() {
        val pending = pendingIncomingTransfer ?: return
        addLog("Incoming transfer rejected")
        pendingIncomingTransfer = null
        _state.update {
            it.copy(
                incomingTransfer = null,
                statusHeadline = "Incoming transfer rejected",
                statusBody = "The receive side rejected the incoming transfer.",
            )
        }
    }

    private fun requestDataPath(peerId: String, passphrase: String, serverMode: Boolean) {
        if (passphrase.length !in 8..63) {
            emitError(
                headline = "Invalid passphrase",
                body = "Wi-Fi Aware PSK must be between 8 and 63 characters.",
            )
            return
        }

        val peerHandle = peerHandles[peerId]
        val session = sessionForPeer(peerId)
        if (peerHandle == null || session == null) {
            emitError(
                headline = "Cannot request data path",
                body = "Select a discovered peer after discovery has started and keep that peer visible.",
            )
            return
        }

        try {
            intentionalSessionReset = false
            unregisterNetworkCallback()

            val specifier = WifiAwareNetworkSpecifier.Builder(session, peerHandle)
                .setPskPassphrase(passphrase)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                .setNetworkSpecifier(specifier)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    activeNetwork = network
                    addLog("Wi-Fi Aware data path became available")
                    _state.update {
                        it.copy(
                            dataPathRequested = true,
                            dataPathAvailable = true,
                            statusHeadline = "Secure Wi-Fi Aware link available",
                            statusBody = "The next implementation step is binding sockets to the provided Network and exchanging transfer messages.",
                            lastError = null,
                        )
                    }
                    if (serverMode) {
                        launchServerTransfer(network)
                    }
                }

                override fun onUnavailable() {
                    activeNetwork = null
                    addLog("Wi-Fi Aware data path request failed")
                    _state.update {
                        it.copy(
                            dataPathRequested = false,
                            dataPathAvailable = false,
                            statusHeadline = "Data path unavailable",
                            statusBody = "Android could not establish the secure Wi-Fi Aware link with the selected peer.",
                            lastError = "Data path unavailable",
                        )
                    }
                }

                override fun onLost(network: Network) {
                    if (activeNetwork == network) {
                        activeNetwork = null
                    }
                    if (intentionalSessionReset) {
                        intentionalSessionReset = false
                        addLog("Wi-Fi Aware session closed")
                        return
                    }
                    addLog("Wi-Fi Aware data path lost")
                    _state.update {
                        it.copy(
                            dataPathAvailable = false,
                            statusHeadline = "Aware link lost",
                            statusBody = "The secure Wi-Fi Aware network was lost. Discovery can continue and the request can be retried.",
                        )
                    }
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val transportInfo = networkCapabilities.transportInfo as? WifiAwareNetworkInfo ?: return
                    if (!serverMode && activeClientTransferId == null) {
                        addLog("Received WifiAwareNetworkInfo with peer endpoint details")
                        launchClientTransfer(network, transportInfo)
                    }
                }
            }
            networkCallback = callback

            addLog("Requesting secure data path in ${if (serverMode) "server" else "client"} mode")
            _state.update {
                it.copy(
                    dataPathRequested = true,
                    dataPathAvailable = false,
                    statusHeadline = "Requesting secure data path",
                    statusBody = "Android is negotiating a secure Wi-Fi Aware link to the selected peer.",
                    lastError = null,
                )
            }
            connectivityManager.requestNetwork(request, callback, DATA_PATH_TIMEOUT_MS)
        } catch (securityException: SecurityException) {
            emitError(
                headline = "Data path request blocked",
                body = securityException.message ?: "Android rejected the secure Wi-Fi Aware network request.",
            )
        } catch (illegalArgumentException: IllegalArgumentException) {
            emitError(
                headline = "Invalid data path request",
                body = illegalArgumentException.message ?: "The secure link request parameters were rejected by Android.",
            )
        } catch (runtimeException: RuntimeException) {
            emitUnexpectedError("Data path request crashed", runtimeException)
        }
    }

    private fun launchPublishAndSubscribe(roleLabel: String) {
        val session = awareSession ?: return
        closeDiscoverySessionsOnly()
        discoveredPeers.clear()
        peerHandles.clear()
        pushPeers()

        val payload = DiscoveryPayloadCodec.encode(
            deviceName = Build.MODEL ?: "Android device",
            role = roleLabel.lowercase(Locale.US),
        )

        val publishConfig = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .setServiceSpecificInfo(payload)
            .build()

        val subscribeConfig = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        try {
            session.publish(
                publishConfig,
                object : DiscoverySessionCallback() {
                    override fun onPublishStarted(session: PublishDiscoverySession) {
                        publishSession = session
                        addLog("Publish session started")
                        _state.update {
                            it.copy(
                                publishStarted = true,
                                statusHeadline = "Publish started",
                                statusBody = "This device is now advertising the shared Wi-Fi Aware service.",
                            )
                        }
                    }

                    override fun onSessionConfigFailed() {
                        emitError(
                            headline = "Publish failed",
                            body = "Android could not start the publish session for the shared service.",
                        )
                    }

                    override fun onSessionTerminated() {
                        publishSession = null
                        _state.update {
                            it.copy(
                                publishStarted = false,
                                statusHeadline = "Publish stopped",
                                statusBody = "The advertising session ended and can be restarted.",
                            )
                        }
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        handleMessageReceived(peerHandle, message)
                        markPeerOrigin(peerHandle, SessionOrigin.PUBLISH)
                    }
                },
                mainHandler,
            )

            session.subscribe(
                subscribeConfig,
                object : DiscoverySessionCallback() {
                    override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                        subscribeSession = session
                        addLog("Subscribe session started")
                        _state.update {
                            it.copy(
                                subscribeStarted = true,
                                isDiscovering = true,
                                statusHeadline = "Scanning nearby peers",
                                statusBody = "Listening for the same service from nearby Android or iOS devices.",
                            )
                        }
                    }

                    override fun onServiceDiscovered(info: ServiceDiscoveryInfo) {
                        registerPeer(
                            peerHandle = info.peerHandle,
                            serviceSpecificInfo = info.serviceSpecificInfo,
                            origin = SessionOrigin.SUBSCRIBE,
                        )
                    }

                    override fun onServiceDiscovered(
                        peerHandle: PeerHandle,
                        serviceSpecificInfo: ByteArray?,
                        matchFilter: MutableList<ByteArray>?,
                    ) {
                        registerPeer(peerHandle, serviceSpecificInfo, SessionOrigin.SUBSCRIBE)
                    }

                    override fun onServiceLost(peerHandle: PeerHandle, reason: Int) {
                        val key = peerHandle.hashCode().toString()
                        discoveredPeers.remove(key)
                        peerHandles.remove(key)
                        addLog("Peer lost: $key")
                        pushPeers()
                        _state.update {
                            it.copy(
                                statusHeadline = "Peer moved out of range",
                                statusBody = "A discovered service is no longer visible. Discovery continues automatically.",
                            )
                        }
                    }

                    override fun onSessionConfigFailed() {
                        emitError(
                            headline = "Subscribe failed",
                            body = "Android could not start the subscribe session for nearby peer discovery.",
                        )
                    }

                    override fun onSessionTerminated() {
                        subscribeSession = null
                        _state.update {
                            it.copy(
                                subscribeStarted = false,
                                isDiscovering = false,
                                statusHeadline = "Subscribe stopped",
                                statusBody = "Peer scanning ended. You can start discovery again.",
                            )
                        }
                    }

                    override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                        handleMessageReceived(peerHandle, message)
                        markPeerOrigin(peerHandle, SessionOrigin.SUBSCRIBE)
                    }
                },
                mainHandler,
            )
        } catch (securityException: SecurityException) {
            emitError(
                headline = "Discovery blocked by permissions",
                body = securityException.message ?: "Android rejected publish/subscribe due to permission state.",
            )
        }
    }

    private fun registerPeer(
        peerHandle: PeerHandle,
        serviceSpecificInfo: ByteArray?,
        origin: SessionOrigin,
    ) {
        val decoded = DiscoveryPayloadCodec.decode(serviceSpecificInfo)
        val id = peerHandle.hashCode().toString()
        peerHandles[id] = peerHandle
        peerSessionOrigins[id] = origin
        addLog("Peer discovered: ${decoded?.deviceName ?: id}")
        discoveredPeers[id] = AwarePeer(
            id = id,
            name = decoded?.deviceName ?: "Nearby device",
            detail = buildString {
                append("Discovered via ")
                append(SERVICE_NAME)
                decoded?.let {
                    append(" • role=")
                    append(it.role)
                    append(" • protocol v")
                    append(it.version)
                }
            },
            distanceHint = "Discovery callback received",
            signalLevel = 0.72f,
            trusted = false,
        )
        pushPeers()
        _state.update {
            it.copy(
                statusHeadline = "Found ${decoded?.deviceName ?: "a peer"}",
                statusBody = "The app can now progress to pairing and Wi-Fi Aware data-path setup.",
                lastError = null,
            )
        }
    }

    private fun handleMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
        val decodedMessage = TransferBootstrapMessage.fromJson(String(message)) ?: return
        val peerId = peerHandle.hashCode().toString()
        when (decodedMessage) {
            is TransferBootstrapMessage.TransferRequest -> {
                val origin = peerSessionOrigins[peerId] ?: SessionOrigin.PUBLISH
                peerSessionOrigins[peerId] = origin
                if (pendingIncomingTransfer?.transferId == decodedMessage.transferId) {
                    addLog("Duplicate incoming transfer request ignored")
                    return
                }
                pendingIncomingTransfer = PendingIncomingTransfer(
                    peerId = peerId,
                    transferId = decodedMessage.transferId,
                    senderName = decodedMessage.senderName,
                )
                addLog("Incoming transfer request from ${decodedMessage.senderName}")
                _state.update {
                    it.copy(
                        statusHeadline = "Incoming transfer request",
                        statusBody = "${decodedMessage.senderName} wants to send a file. Accept on this device to continue.",
                        incomingTransfer = IncomingTransferRequest(
                            peerId = peerId,
                            senderName = decodedMessage.senderName,
                            fileName = "Pending secure metadata",
                            sizeBytes = -1,
                            transferId = decodedMessage.transferId,
                        ),
                        lastError = null,
                    )
                }
            }
            is TransferBootstrapMessage.TransferAccept -> {
                val origin = peerSessionOrigins[peerId] ?: SessionOrigin.SUBSCRIBE
                peerSessionOrigins[peerId] = origin
                val pending = pendingOutgoingTransfer
                if (pending == null || pending.offer.transferId != decodedMessage.transferId) {
                    addLog("Transfer accept ignored for unknown transfer ${decodedMessage.transferId}")
                    return
                }
                addLog("Receiver accepted transfer request")
                requestDataPath(peerId, currentPassphrase, serverMode = true)
            }
        }
    }

    private fun sendTransferBootstrap(peerHandle: PeerHandle, message: TransferBootstrapMessage) {
        val peerId = peerHandle.hashCode().toString()
        val session = sessionForPeer(peerId) ?: return
        try {
            session.sendMessage(peerHandle, nextMessageId++, message.toJson().toByteArray())
            addLog("Sent transfer bootstrap message")
            _state.update {
                it.copy(
                    statusHeadline = "Transfer request sent",
                    statusBody = "The peer has been asked to prepare a secure Wi-Fi Aware link for file transfer.",
                    lastError = null,
                )
            }
        } catch (error: IllegalArgumentException) {
            emitError(
                headline = "Transfer bootstrap too large",
                body = error.message ?: "The Wi-Fi Aware bootstrap message exceeded device limits.",
            )
        } catch (runtimeException: RuntimeException) {
            emitUnexpectedError("Transfer bootstrap crashed", runtimeException)
        }
    }

    private fun launchServerTransfer(network: Network) {
        val pending = pendingOutgoingTransfer ?: return
        if (activeServerTransferId == pending.offer.transferId) return
        activeServerTransferId = pending.offer.transferId
        scope.launch {
            runCatching {
                transferEngine.sendFileOverServerSocket(
                    network = network,
                    port = TRANSFER_PORT,
                    file = pending.file,
                    offer = pending.offer,
                    onStatus = ::emitTransferStatus,
                )
                pendingOutgoingTransfer = null
                closeTransferSessionForRestart(
                    headline = "Transfer complete",
                    body = "Ready to scan again.",
                )
            }.onFailure { error ->
                addLog("Sender socket transfer failed: ${error.message}")
                emitError(
                    headline = "File send failed",
                    body = error.message ?: "The sender side socket transfer failed.",
                )
            }.also {
                activeServerTransferId = null
            }
        }
    }

    private fun launchClientTransfer(network: Network, info: WifiAwareNetworkInfo) {
        val pending = pendingIncomingTransfer ?: return
        if (activeClientTransferId == pending.transferId) return
        val host = info.peerIpv6Addr ?: return
        val port = info.port.takeIf { it > 0 } ?: TRANSFER_PORT

        activeClientTransferId = pending.transferId
        pendingIncomingTransfer = null
        scope.launch {
            runCatching {
                transferEngine.receiveFileFromPeer(
                    network = network,
                    host = host,
                    port = port,
                    destinationTreeUri = receiveDestinationTreeUri,
                    onStatus = ::emitTransferStatus,
                )
            }.onSuccess { result ->
                addLog("File received and saved to ${result.savedLocation}")
                _state.update {
                    it.copy(
                        statusHeadline = "File received",
                        statusBody = "Saved ${result.offer.fileName} from ${pending.senderName} to ${result.savedLocation}",
                        latestSavedFilePath = result.savedLocation,
                        lastError = null,
                    )
                }
                closeTransferSessionForRestart(
                    headline = "File received",
                    body = "Saved ${result.offer.fileName}. Scan to receive again.",
                )
            }.onFailure { error ->
                addLog("Receiver socket transfer failed: ${error.message}")
                emitError(
                    headline = "File receive failed",
                    body = error.message ?: "The receiver side socket transfer failed.",
                )
            }.also {
                activeClientTransferId = null
            }
        }
    }

    private fun emitTransferStatus(headline: String, body: String, progress: Float) {
        addLog("$headline: $body")
        _state.update {
            it.copy(
                statusHeadline = headline,
                statusBody = body,
                lastError = null,
            )
        }
    }

    private fun closeTransferSessionForRestart(headline: String, body: String) {
        intentionalSessionReset = true
        addLog("Closing data path and discovery after transfer")
        closeSessions()
        _state.update {
            it.copy(
                statusHeadline = headline,
                statusBody = body,
                lastError = null,
            )
        }
    }

    private fun pushPeers() {
        _state.update { it.copy(peers = discoveredPeers.values.sortedBy { peer -> peer.name }) }
    }

    private fun refreshCapability() {
        val supported = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        val availableNow = if (supported) {
            runCatching { wifiAwareManager?.isAvailable == true }.getOrDefault(false)
        } else {
            false
        }
        _state.update {
            it.copy(
                featureSupported = supported,
                available = availableNow,
                statusHeadline = when {
                    !supported -> "Wi-Fi Aware not supported on this device"
                    availableNow -> "Ready to start discovery"
                    else -> "Wi-Fi Aware is currently unavailable"
                },
                statusBody = when {
                    !supported -> "Install this APK on hardware that exposes PackageManager.FEATURE_WIFI_AWARE."
                    availableNow -> "Permissions and attach are the next gates before real peer discovery."
                    else -> "Wi-Fi Aware exists on this device, but the system reports it is unavailable right now."
                },
            )
        }
    }

    private fun emitError(headline: String, body: String) {
        addLog("ERROR $headline: $body")
        _state.update {
            it.copy(
                isDiscovering = false,
                statusHeadline = headline,
                statusBody = body,
                lastError = body,
            )
        }
    }

    private fun emitUnexpectedError(headline: String, error: Throwable) {
        val body = error.message ?: error::class.java.simpleName
        Log.e(LOG_TAG, headline, error)
        emitError(headline, body)
    }

    private fun closeDiscoverySessionsOnly() {
        publishSession?.close()
        publishSession = null
        subscribeSession?.close()
        subscribeSession = null
        _state.update {
            it.copy(
                publishStarted = false,
                subscribeStarted = false,
                dataPathRequested = false,
                dataPathAvailable = false,
                incomingTransfer = null,
                peers = emptyList(),
            )
        }
    }

    private fun closeSessions() {
        closeDiscoverySessionsOnly()
        awareSession?.close()
        awareSession = null
        discoveredPeers.clear()
        peerHandles.clear()
        peerSessionOrigins.clear()
        unregisterNetworkCallback()
        activeNetwork = null
        pendingOutgoingTransfer = null
        pendingIncomingTransfer = null
        activeServerTransferId = null
        activeClientTransferId = null
        _state.update {
            it.copy(
                sessionAttached = false,
                isDiscovering = false,
                dataPathRequested = false,
                dataPathAvailable = false,
                incomingTransfer = null,
                peers = emptyList(),
            )
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            availabilityReceiver,
            IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        appContext.unregisterReceiver(availabilityReceiver)
        receiverRegistered = false
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }

    private fun addLog(message: String) {
        Log.d(LOG_TAG, message)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _state.update { current ->
            current.copy(
                logs = (listOf(DebugLogEntry(timestamp, message)) + current.logs).take(80),
            )
        }
    }

    private fun markPeerOrigin(peerHandle: PeerHandle, origin: SessionOrigin) {
        val peerId = peerHandle.hashCode().toString()
        peerHandles[peerId] = peerHandle
        peerSessionOrigins[peerId] = origin
    }

    private fun sessionForPeer(peerId: String): DiscoverySession? = when (peerSessionOrigins[peerId]) {
        SessionOrigin.PUBLISH -> publishSession
        SessionOrigin.SUBSCRIBE -> subscribeSession
        null -> subscribeSession ?: publishSession
    }

    private companion object {
        const val LOG_TAG = "WiFiAwareApp"
        const val SERVICE_NAME = "wfareshare"
        const val TRANSFER_PORT = 8988
        const val DATA_PATH_TIMEOUT_MS = 15_000
    }
}

private data class PendingOutgoingTransfer(
    val peerId: String,
    val file: SelectedFileDescriptor,
    val offer: TransferOffer,
)

private data class PendingIncomingTransfer(
    val peerId: String,
    val transferId: String,
    val senderName: String,
)

private enum class SessionOrigin {
    PUBLISH,
    SUBSCRIBE,
}
