package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import java.math.BigDecimal


data class InstrumentFilters(
    val minOrderQty: BigDecimal? = null,
    val maxOrderQty: BigDecimal? = null,
    val maxMktOrderQty: BigDecimal? = null,
    val qtyStep: BigDecimal? = null,
    val minNotionalValue: BigDecimal? = null,
    val tickSize: BigDecimal? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
)
