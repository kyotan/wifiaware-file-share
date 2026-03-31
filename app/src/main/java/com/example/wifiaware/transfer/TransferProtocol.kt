package com.example.wifiaware.transfer

import org.json.JSONObject

data class TransferOffer(
    val transferId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    fun toJson(): String = JSONObject()
        .put("type", "offer")
        .put("transferId", transferId)
        .put("fileName", fileName)
        .put("mimeType", mimeType)
        .put("size", sizeBytes)
        .put("sha256", sha256)
        .toString()
}

sealed interface TransferControlMessage {
    val transferId: String

    fun toJson(): String

    data class Accept(override val transferId: String) : TransferControlMessage {
        override fun toJson(): String = baseJson("accept").toString()
    }

    data class Reject(
        override val transferId: String,
        val reason: String,
    ) : TransferControlMessage {
        override fun toJson(): String = baseJson("reject").put("reason", reason).toString()
    }

    data class Resume(
        override val transferId: String,
        val offset: Long,
    ) : TransferControlMessage {
        override fun toJson(): String = baseJson("resume").put("offset", offset).toString()
    }

    data class Complete(override val transferId: String) : TransferControlMessage {
        override fun toJson(): String = baseJson("complete").toString()
    }

    data class Error(
        override val transferId: String,
        val reason: String,
    ) : TransferControlMessage {
        override fun toJson(): String = baseJson("error").put("reason", reason).toString()
    }

    fun baseJson(type: String): JSONObject = JSONObject()
        .put("type", type)
        .put("transferId", transferId)
}
