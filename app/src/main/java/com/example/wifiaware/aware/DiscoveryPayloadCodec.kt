package com.example.wifiaware.aware

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object DiscoveryPayloadCodec {
    private const val VERSION = 1

    fun encode(deviceName: String, role: String): ByteArray {
        val json = JSONObject()
            .put("v", VERSION)
            .put("role", role)
            .put("deviceName", deviceName)
            .put("caps", JSONArray().put("pairing_qr").put("resume_v1"))

        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun decode(bytes: ByteArray?): DecodedDiscoveryPayload? {
        if (bytes == null || bytes.isEmpty()) return null

        return runCatching {
            val json = JSONObject(String(bytes, StandardCharsets.UTF_8))
            DecodedDiscoveryPayload(
                version = json.optInt("v", VERSION),
                role = json.optString("role", "dual"),
                deviceName = json.optString("deviceName", "Nearby device"),
            )
        }.getOrNull()
    }
}

data class DecodedDiscoveryPayload(
    val version: Int,
    val role: String,
    val deviceName: String,
)
