package io.github.yaklede.elliott.wave.principle.coin.backtest

import java.math.BigDecimal

data class BacktestResult(
    val trades: Int,
    val winRate: BigDecimal,
    val profitFactor: BigDecimal,
    val maxDrawdown: BigDecimal,
    val finalEquity: BigDecimal,
)
