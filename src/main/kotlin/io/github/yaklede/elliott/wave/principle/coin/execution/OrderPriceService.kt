package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentFilters
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.InstrumentInfoService
import java.math.BigDecimal
import java.math.RoundingMode
import org.springframework.stereotype.Component

@Component
class OrderPriceService(
    private val instrumentInfoService: InstrumentInfoService,
    private val bybitProperties: BybitProperties,
) {
    suspend fun adjustPrices(
        entry: BigDecimal,
        stop: BigDecimal,
        takeProfit: BigDecimal,
        isLong: Boolean,
    ): PriceLevels? {
        val filters = instrumentInfoService.getFilters(bybitProperties.category, bybitProperties.symbol)
        return adjustPrices(entry, stop, takeProfit, filters, isLong)
    }

    fun adjustPrices(
        entry: BigDecimal,
        stop: BigDecimal,
        takeProfit: BigDecimal,
        filters: InstrumentFilters?,
        isLong: Boolean,
    ): PriceLevels? {
        var adjustedStop = stop
        var adjustedTp = takeProfit
        val tickSize = filters?.tickSize
        if (tickSize != null && tickSize > BigDecimal.ZERO) {
            adjustedStop = roundToTick(adjustedStop, tickSize, RoundingMode.DOWN)
            adjustedTp = roundToTick(adjustedTp, tickSize, RoundingMode.DOWN)
        }

        val minPrice = filters?.minPrice
        val maxPrice = filters?.maxPrice
        if (minPrice != null && (adjustedStop < minPrice || adjustedTp < minPrice || entry < minPrice)) {
            return null
        }
        if (maxPrice != null && (adjustedStop > maxPrice || adjustedTp > maxPrice || entry > maxPrice)) {
            return null
        }
        if (isLong) {
            if (adjustedStop >= entry) return null
            if (adjustedTp <= entry) return null
        } else {
            if (adjustedStop <= entry) return null
            if (adjustedTp >= entry) return null
        }

        return PriceLevels(entry, adjustedStop, adjustedTp)
    }

    private fun roundToTick(price: BigDecimal, tick: BigDecimal, mode: RoundingMode): BigDecimal {
        val steps = price.divide(tick, 0, mode)
        return steps.multiply(tick)
    }
}


data class PriceLevels(
    val entry: BigDecimal,
    val stop: BigDecimal,
    val takeProfit: BigDecimal,
)
