package io.github.yaklede.elliott.wave.principle.coin.marketdata

import java.math.BigDecimal


data class Candle(
    val timeOpenMs: Long,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: BigDecimal,
)
