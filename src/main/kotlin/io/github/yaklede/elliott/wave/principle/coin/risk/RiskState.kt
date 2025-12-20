package io.github.yaklede.elliott.wave.principle.coin.risk

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RiskState(
    val currentDay: LocalDate? = null,
    val dailyStartEquity: BigDecimal? = null,
    val killSwitchActive: Boolean = false,
    val consecutiveLosses: Int = 0,
    val cooldownUntil: Instant? = null,
    val riskMultiplier: BigDecimal = BigDecimal.ONE,
)
