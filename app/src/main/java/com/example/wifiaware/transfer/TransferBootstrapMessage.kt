package com.example.wifiaware.transfer

import org.json.JSONObject

sealed interface TransferBootstrapMessage {
    fun toJson(): String

    data class TransferRequest(
        val senderName: String,
        val transferId: String,
    ) : TransferBootstrapMessage {
        override fun toJson(): String = JSONObject()
            .put("type", "transfer_request")
            .put("senderName", senderName.take(24))
            .put("transferId", transferId)
            .toString()
    }

    data class TransferAccept(
        val transferId: String,
    ) : TransferBootstrapMessage {
        override fun toJson(): String = JSONObject()
            .put("type", "transfer_accept")
            .put("transferId", transferId)
            .toString()
    }

    companion object {
        fun fromJson(raw: String): TransferBootstrapMessage? = runCatching {
            val root = JSONObject(raw)
            when (root.getString("type")) {
                "transfer_request" -> {
                    TransferRequest(
                        senderName = root.optString("senderName", "Nearby device"),
                        transferId = root.optString("transferId", ""),
                    )
                }
                "transfer_accept" -> {
                    TransferAccept(
                        transferId = root.optString("transferId", ""),
                    )
                }
                else -> null
            }
        }.getOrNull()
    }
}
