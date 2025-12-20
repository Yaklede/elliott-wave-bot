package io.github.yaklede.elliott.wave.principle.coin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bybit")
data class BybitProperties(
    val baseUrl: String = "https://api-testnet.bybit.com",
    val wsPublicUrl: String = "wss://stream-testnet.bybit.com/v5/public/spot",
    val wsPrivateUrl: String = "wss://stream-testnet.bybit.com/v5/private",
    val apiKey: String = "",
    val apiSecret: String = "",
    val recvWindowMs: Long = 5000,
    val category: String = "spot",
    val symbol: String = "BTCUSDT",
    val interval: String = "15",
    val htfInterval: String = "60",
)
