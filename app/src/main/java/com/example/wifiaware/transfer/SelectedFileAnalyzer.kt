package com.example.wifiaware.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.security.MessageDigest
import java.util.UUID

class SelectedFileAnalyzer(
    private val contentResolver: ContentResolver,
) {
    fun analyze(uri: Uri): SelectedFileDescriptor {
        val metadata = queryMetadata(uri)
        val digest = MessageDigest.getInstance("SHA-256")

        contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead <= 0) break
                digest.update(buffer, 0, bytesRead)
            }
        } ?: error("Unable to open selected file")

        return SelectedFileDescriptor(
            uri = uri,
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte) },
            transferId = UUID.randomUUID().toString(),
        )
    }

    private fun queryMetadata(uri: Uri): FileMetadata {
        var displayName = "selected-file"
        var sizeBytes = 0L
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }

        return FileMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
        )
    }
}

data class SelectedFileDescriptor(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val transferId: String,
)

private data class FileMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
)
