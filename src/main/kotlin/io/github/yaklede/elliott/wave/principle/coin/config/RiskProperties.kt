package io.github.yaklede.elliott.wave.principle.coin.config

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("risk")
data class RiskProperties(
    val riskPerTrade: BigDecimal = BigDecimal("0.01"),
    val dailyMaxDd: BigDecimal = BigDecimal("0.03"),
    val maxConsecutiveLosses: Int = 3,
    val cooldownMinutes: Long = 60,
    val minQty: BigDecimal = BigDecimal("0.0"),
    val maxQty: BigDecimal = BigDecimal("1000000.0"),
    val statePath: String? = "data/risk-state.json",
)
