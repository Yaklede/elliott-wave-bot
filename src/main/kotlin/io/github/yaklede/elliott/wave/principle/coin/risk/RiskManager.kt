package io.github.yaklede.elliott.wave.principle.coin.risk

import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import io.github.yaklede.elliott.wave.principle.coin.domain.RejectReason
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.springframework.stereotype.Component

@Component
class RiskManager(
    private val properties: RiskProperties,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Volatile
    private var state = RiskState()

    fun canEnter(now: Instant = clock.instant()): Boolean {
        updateDayBoundary(now)
        val cooldownUntil = state.cooldownUntil
        if (state.killSwitchActive) return false
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) return false
        return true
    }

    fun entryBlockReason(now: Instant = clock.instant()): RejectReason? {
        updateDayBoundary(now)
        val cooldownUntil = state.cooldownUntil
        if (state.killSwitchActive) return RejectReason.RISK_KILLSWITCH
        if (cooldownUntil != null && now.isBefore(cooldownUntil)) return RejectReason.COOLDOWN
        return null
    }

    fun computeOrderQty(
        equity: BigDecimal,
        entryPrice: BigDecimal,
        stopPrice: BigDecimal,
    ): BigDecimal {
        if (equity <= BigDecimal.ZERO) return BigDecimal.ZERO
        val stopDistance = entryPrice.subtract(stopPrice).abs()
        if (stopDistance <= BigDecimal.ZERO) return BigDecimal.ZERO

        val riskAmount = equity.multiply(properties.riskPerTrade)
        val rawQty = riskAmount.divide(stopDistance, 8, RoundingMode.DOWN)
        return rawQty
            .coerceAtLeast(properties.minQty)
            .coerceAtMost(properties.maxQty)
    }

    fun updateEquity(equity: BigDecimal, now: Instant = clock.instant()) {
        updateDayBoundary(now)
        val startEquity = state.dailyStartEquity ?: equity
        if (state.dailyStartEquity == null) {
            state = state.copy(dailyStartEquity = startEquity)
        }
        if (startEquity <= BigDecimal.ZERO) return
        val drawdown = startEquity.subtract(equity).divide(startEquity, 6, RoundingMode.HALF_UP)
        if (drawdown >= properties.dailyMaxDd) {
            state = state.copy(killSwitchActive = true)
        }
    }

    fun recordTradeResult(pnl: BigDecimal, now: Instant = clock.instant()) {
        updateDayBoundary(now)
        val losses = if (pnl < BigDecimal.ZERO) state.consecutiveLosses + 1 else 0
        val cooldownUntil = if (losses >= properties.maxConsecutiveLosses) {
            now.plusSeconds(properties.cooldownMinutes * 60)
        } else {
            state.cooldownUntil
        }
        state = state.copy(consecutiveLosses = losses, cooldownUntil = cooldownUntil)
    }

    fun resetForBacktest(startEquity: BigDecimal, now: Instant = clock.instant()) {
        val day = now.atZone(ZoneOffset.UTC).toLocalDate()
        state = RiskState(currentDay = day, dailyStartEquity = startEquity)
    }

    fun isKillSwitchActive(): Boolean = state.killSwitchActive

    fun snapshot(): RiskState = state

    fun restore(snapshot: RiskState) {
        state = snapshot
    }

    private fun updateDayBoundary(now: Instant) {
        val day = now.atZone(ZoneOffset.UTC).toLocalDate()
        val currentDay = state.currentDay
        if (currentDay == null || day.isAfter(currentDay)) {
            state = RiskState(currentDay = day)
        }
    }
}

private fun BigDecimal.coerceAtLeast(min: BigDecimal): BigDecimal = if (this < min) min else this
private fun BigDecimal.coerceAtMost(max: BigDecimal): BigDecimal = if (this > max) max else this
