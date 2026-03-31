package com.example.wifiaware.transfer

import android.content.ContentResolver
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class SocketTransferEngine(
    private val appContext: Context,
    private val connectivityManager: ConnectivityManager,
) {
    fun sendFileOverServerSocket(
        network: Network,
        port: Int,
        file: SelectedFileDescriptor,
        offer: TransferOffer,
        onStatus: (String, String, Float) -> Unit,
    ) {
        try {
            connectivityManager.bindProcessToNetwork(network)
            ServerSocket().use { serverSocket ->
                serverSocket.reuseAddress = true
                serverSocket.bind(InetSocketAddress(port))
                onStatus("Waiting for receiver", "Server socket is listening on port $port.", 0.3f)
                serverSocket.accept().use { accepted ->
                    handleSenderSocket(appContext.contentResolver, accepted, file, offer, onStatus)
                }
            }
        } finally {
            connectivityManager.bindProcessToNetwork(null)
        }
    }

    fun receiveFileFromPeer(
        network: Network,
        host: java.net.Inet6Address,
        port: Int,
        destinationTreeUri: Uri?,
        onStatus: (String, String, Float) -> Unit,
    ): ReceivedFileResult {
        val socket = network.socketFactory.createSocket(host, port)
        socket.use { connected ->
            return handleReceiverSocket(appContext, connected, destinationTreeUri, onStatus)
        }
    }

    private fun handleSenderSocket(
        contentResolver: ContentResolver,
        socket: Socket,
        file: SelectedFileDescriptor,
        offer: TransferOffer,
        onStatus: (String, String, Float) -> Unit,
    ) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        writeFrame(output, offer.toJson().toByteArray())
        output.flush()
        onStatus("Offer sent", "Waiting for receiver acceptance.", 0.4f)

        val response = String(readFrame(input))
        if (!response.contains("\"type\":\"accept\"")) {
            error("Receiver did not accept transfer: $response")
        }

        contentResolver.openInputStream(file.uri)?.use { source ->
            val buffer = ByteArray(CHUNK_SIZE)
            var sent = 0L
            while (true) {
                val read = source.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                sent += read
                onStatus(
                    "Sending file",
                    "${file.displayName} ${sent}/${file.sizeBytes} bytes",
                    (sent.toFloat() / file.sizeBytes.coerceAtLeast(1L)).coerceIn(0f, 1f),
                )
            }
            output.flush()
        } ?: error("Unable to open selected file for sending")

        val completion = String(readFrame(input))
        if (!completion.contains("\"type\":\"complete\"")) {
            error("Receiver reported transfer failure: $completion")
        }
        onStatus("Transfer complete", "Receiver confirmed file integrity.", 1f)
    }

    private fun handleReceiverSocket(
        context: Context,
        socket: Socket,
        destinationTreeUri: Uri?,
        onStatus: (String, String, Float) -> Unit,
    ): ReceivedFileResult {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        val offerJson = String(readFrame(input))
        val offer = parseOffer(offerJson)
        writeFrame(output, TransferControlMessage.Accept(offer.transferId).toJson().toByteArray())
        output.flush()

        val digest = MessageDigest.getInstance("SHA-256")
        val destination = openReceiveDestination(context, offer, destinationTreeUri)

        destination.outputStream.use { fileOut ->
            val buffer = ByteArray(CHUNK_SIZE)
            var received = 0L
            while (received < offer.sizeBytes) {
                val toRead = minOf(buffer.size.toLong(), offer.sizeBytes - received).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) error("Connection closed before file finished")
                fileOut.write(buffer, 0, read)
                digest.update(buffer, 0, read)
                received += read
                onStatus(
                    "Receiving file",
                    "${offer.fileName} ${received}/${offer.sizeBytes} bytes",
                    (received.toFloat() / offer.sizeBytes.coerceAtLeast(1L)).coerceIn(0f, 1f),
                )
            }
        }

        val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        if (actualHash != offer.sha256) {
            writeFrame(
                output,
                TransferControlMessage.Error(offer.transferId, "hash_mismatch").toJson().toByteArray(),
            )
            output.flush()
            error("Received file hash mismatch")
        }

        writeFrame(output, TransferControlMessage.Complete(offer.transferId).toJson().toByteArray())
        output.flush()
        onStatus("Transfer complete", "Saved to ${destination.savedLocation}", 1f)
        return ReceivedFileResult(destination.savedLocation, offer)
    }

    private fun parseOffer(raw: String): TransferOffer {
        val json = org.json.JSONObject(raw)
        return TransferOffer(
            transferId = json.getString("transferId"),
            fileName = json.getString("fileName"),
            mimeType = json.getString("mimeType"),
            sizeBytes = json.getLong("size"),
            sha256 = json.getString("sha256"),
        )
    }

    private fun writeFrame(output: DataOutputStream, payload: ByteArray) {
        output.writeInt(payload.size)
        output.write(payload)
    }

    private fun readFrame(input: DataInputStream): ByteArray {
        val length = input.readInt()
        require(length in 1..(1024 * 1024)) { "Invalid frame length: $length" }
        return ByteArray(length).also(input::readFully)
    }

    private fun openReceiveDestination(
        context: Context,
        offer: TransferOffer,
        destinationTreeUri: Uri?,
    ): ReceiveDestination {
        if (destinationTreeUri == null) {
            val outputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            val outputFile = uniqueFile(outputDir, offer.fileName)
            return ReceiveDestination(
                outputStream = outputFile.outputStream(),
                savedLocation = outputFile.absolutePath,
            )
        }

        val treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            destinationTreeUri,
            DocumentsContract.getTreeDocumentId(destinationTreeUri),
        )
        val documentUri = createDocumentInTree(
            contentResolver = context.contentResolver,
            parentDocumentUri = treeDocumentUri,
            mimeType = offer.mimeType,
            displayName = offer.fileName,
        )
        val outputStream = context.contentResolver.openOutputStream(documentUri, "w")
            ?: error("Unable to open selected destination folder for writing")
        return ReceiveDestination(
            outputStream = outputStream,
            savedLocation = documentUri.toString(),
        )
    }

    private fun createDocumentInTree(
        contentResolver: ContentResolver,
        parentDocumentUri: Uri,
        mimeType: String,
        displayName: String,
    ): Uri {
        val initial = DocumentsContract.createDocument(
            contentResolver,
            parentDocumentUri,
            mimeType.ifBlank { "application/octet-stream" },
            displayName,
        )
        if (initial != null) {
            return initial
        }

        val fallbackName = uniqueFileName(displayName)
        return DocumentsContract.createDocument(
            contentResolver,
            parentDocumentUri,
            mimeType.ifBlank { "application/octet-stream" },
            fallbackName,
        ) ?: error("Unable to create a file in the selected destination folder")
    }

    private fun uniqueFile(directory: File, originalName: String): File {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        var candidate = File(directory, originalName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$baseName-$index$extension")
            index += 1
        }
        return candidate
    }

    private fun uniqueFileName(originalName: String): String {
        val dotIndex = originalName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) originalName.substring(0, dotIndex) else originalName
        val extension = if (dotIndex > 0) originalName.substring(dotIndex) else ""
        return "$baseName-${System.currentTimeMillis()}$extension"
    }

    private companion object {
        const val CHUNK_SIZE = 64 * 1024
    }
}

data class ReceivedFileResult(
    val savedLocation: String,
    val offer: TransferOffer,
)

private data class ReceiveDestination(
    val outputStream: OutputStream,
    val savedLocation: String,
)
