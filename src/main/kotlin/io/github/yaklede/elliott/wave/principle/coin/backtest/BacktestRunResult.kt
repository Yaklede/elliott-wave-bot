package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.portfolio.TradeRecord

data class BacktestRunResult(
    val result: BacktestResult,
    val trades: List<TradeRecord>,
    val decisions: List<DecisionRecord>,
)
