package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

class BybitApiException(
    message: String,
    val retCode: Int? = null,
    val responseTimeMs: Long? = null,
) : RuntimeException(message)
