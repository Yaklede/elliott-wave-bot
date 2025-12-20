package io.github.yaklede.elliott.wave.principle.coin.config

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("backtest")
data class BacktestProperties(
    val initialCapital: BigDecimal = BigDecimal("1000"),
    val feeRate: BigDecimal = BigDecimal("0.0006"),
    val slippageBps: Int = 2,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val csvPath: String? = null,
)
