package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitWsKlineMessage(
    val topic: String? = null,
    val type: String? = null,
    val data: List<BybitWsKlineData> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitWsKlineData(
    val start: String? = null,
    val end: String? = null,
    val interval: String? = null,
    val open: String? = null,
    val close: String? = null,
    val high: String? = null,
    val low: String? = null,
    val volume: String? = null,
    val confirm: Boolean? = null,
)
