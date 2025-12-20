package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import java.nio.charset.StandardCharsets
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Component

@Component
class BybitSigner {
    fun buildGetSignString(
        timestamp: String,
        apiKey: String,
        recvWindow: String,
        queryString: String,
    ): String = "${timestamp}${apiKey}${recvWindow}${queryString}"

    fun buildPostSignString(
        timestamp: String,
        apiKey: String,
        recvWindow: String,
        jsonBody: String,
    ): String = "${timestamp}${apiKey}${recvWindow}${jsonBody}"

    fun signGet(
        secret: String,
        timestamp: String,
        apiKey: String,
        recvWindow: String,
        queryString: String,
    ): String = sign(secret, buildGetSignString(timestamp, apiKey, recvWindow, queryString))

    fun signPost(
        secret: String,
        timestamp: String,
        apiKey: String,
        recvWindow: String,
        jsonBody: String,
    ): String = sign(secret, buildPostSignString(timestamp, apiKey, recvWindow, jsonBody))

    private fun sign(secret: String, payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val hash = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(hash).lowercase()
    }
}
