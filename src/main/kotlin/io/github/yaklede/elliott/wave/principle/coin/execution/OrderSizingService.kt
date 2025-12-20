package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentFilters
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentInfoService
import io.github.yaklede.elliott.wave.principle.coin.risk.RiskManager
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class OrderSizingService(
    private val riskManager: RiskManager,
    private val instrumentInfoService: InstrumentInfoService,
    private val bybitProperties: BybitProperties,
) {
    suspend fun computeQty(
        equity: BigDecimal,
        entryPrice: BigDecimal,
        stopPrice: BigDecimal,
    ): BigDecimal {
        val rawQty = riskManager.computeOrderQty(equity, entryPrice, stopPrice)
        if (rawQty <= BigDecimal.ZERO) return BigDecimal.ZERO
        val filters = instrumentInfoService.getFilters(bybitProperties.category, bybitProperties.symbol)
        return applyFilters(rawQty, entryPrice, filters)
    }

    private fun applyFilters(
        qty: BigDecimal,
        price: BigDecimal,
        filters: InstrumentFilters?,
    ): BigDecimal {
        if (filters == null) return qty
        var adjusted = qty
        val step = filters.qtyStep
        if (step != null && step > BigDecimal.ZERO) {
            val steps = adjusted.divide(step, 0, RoundingMode.DOWN)
            adjusted = steps.multiply(step)
        }
        val maxQty = filters.maxMktOrderQty ?: filters.maxOrderQty
        if (maxQty != null && adjusted > maxQty) adjusted = maxQty
        val minQty = filters.minOrderQty
        if (minQty != null && adjusted < minQty) return BigDecimal.ZERO
        val minNotional = filters.minNotionalValue
        if (minNotional != null && price.multiply(adjusted) < minNotional) return BigDecimal.ZERO
        return adjusted
    }
}
