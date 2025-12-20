package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component

@Component
class InstrumentInfoService(
    private val client: BybitV5Client,
    private val properties: BybitProperties,
) {
    private val cache = ConcurrentHashMap<String, CachedInstrument>()
    private val ttl = Duration.ofMinutes(10).toMillis()

    suspend fun getFilters(category: String = properties.category, symbol: String = properties.symbol): InstrumentFilters? {
        val key = "${category.uppercase()}|${symbol.uppercase()}"
        val cached = cache[key]
        if (cached != null && !cached.isExpired(ttl)) {
            return cached.filters
        }
        val info = client.getInstrumentInfo(category, symbol) ?: return null
        val filters = info.toFilters()
        cache[key] = CachedInstrument(filters, System.currentTimeMillis())
        return filters
    }

    private fun BybitInstrumentInfo.toFilters(): InstrumentFilters {
        return InstrumentFilters(
            minOrderQty = lotSizeFilter?.minOrderQty?.toBigDecimalOrNull(),
            maxOrderQty = lotSizeFilter?.maxOrderQty?.toBigDecimalOrNull(),
            maxMktOrderQty = lotSizeFilter?.maxMktOrderQty?.toBigDecimalOrNull(),
            qtyStep = lotSizeFilter?.qtyStep?.toBigDecimalOrNull(),
            minNotionalValue = lotSizeFilter?.minNotionalValue?.toBigDecimalOrNull(),
            tickSize = priceFilter?.tickSize?.toBigDecimalOrNull(),
            minPrice = priceFilter?.minPrice?.toBigDecimalOrNull(),
            maxPrice = priceFilter?.maxPrice?.toBigDecimalOrNull(),
        )
    }
}

private data class CachedInstrument(
    val filters: InstrumentFilters,
    val fetchedAtMs: Long,
) {
    fun isExpired(ttlMs: Long): Boolean = System.currentTimeMillis() - fetchedAtMs > ttlMs
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (ex: NumberFormatException) {
        null
    }
