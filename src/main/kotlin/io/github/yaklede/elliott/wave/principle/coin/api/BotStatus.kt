package io.github.yaklede.elliott.wave.principle.coin.api

import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import java.math.BigDecimal


data class BotStatus(
    val mode: BotMode,
    val symbol: String,
    val lastCandleTime: Long?,
    val currentPosition: PositionSummary,
    val lastSignal: String?,
    val killSwitchActive: Boolean,
)

data class PositionSummary(
    val side: PositionSide,
    val qty: BigDecimal,
    val avgPrice: BigDecimal,
)
