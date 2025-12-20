package io.github.yaklede.elliott.wave.principle.coin.risk

import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiskSizingTest {
    @Test
    fun `computes position size from risk per trade and clamps`() {
        val properties = RiskProperties(
            riskPerTrade = BigDecimal("0.01"),
            dailyMaxDd = BigDecimal("0.03"),
            maxConsecutiveLosses = 3,
            cooldownMinutes = 60,
            minQty = BigDecimal("0.1"),
            maxQty = BigDecimal("5"),
        )
        val clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)
        val manager = RiskManager(properties, clock)

        val qty = manager.computeOrderQty(
            equity = BigDecimal("1000"),
            entryPrice = BigDecimal("100"),
            stopPrice = BigDecimal("90"),
        )
        assertEquals(BigDecimal("1.00000000"), qty)

        val clamped = manager.computeOrderQty(
            equity = BigDecimal("1000"),
            entryPrice = BigDecimal("100"),
            stopPrice = BigDecimal("99.9"),
        )
        assertEquals(BigDecimal("5"), clamped)
    }
}
