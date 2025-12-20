package io.github.yaklede.elliott.wave.principle.coin.backtest


data class BacktestRequest(
    val csvPath: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
)
