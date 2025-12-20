package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord


data class BacktestReport(
    val result: BacktestResult,
    val trades: List<TradeRecord>,
)
