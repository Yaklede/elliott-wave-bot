package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BybitSignerTest {
    private val signer = BybitSigner()

    @Test
    fun `builds sign string and hmac signature`() {
        val timestamp = "1670000000000"
        val apiKey = "testKey"
        val recvWindow = "5000"
        val queryString = "category=spot&symbol=BTCUSDT"
        val secret = "mysecret"

        val signString = signer.buildGetSignString(timestamp, apiKey, recvWindow, queryString)
        assertEquals("1670000000000testKey5000category=spot&symbol=BTCUSDT", signString)

        val signature = signer.signGet(secret, timestamp, apiKey, recvWindow, queryString)
        assertEquals("7208dfe67fb07a50e08a50b0353ac6cfb404bca5375ab7ea594b80243f465070", signature)
    }
}
