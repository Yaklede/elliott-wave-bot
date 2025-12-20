package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitResponse<T>(
    val retCode: Int,
    val retMsg: String,
    val result: T? = null,
    val time: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitTimeResult(
    val timeSecond: String? = null,
    val timeNano: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitKlineResult(
    val list: List<List<String>> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitInstrumentInfoResult(
    val category: String? = null,
    val list: List<BybitInstrumentInfo> = emptyList(),
    val nextPageCursor: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitInstrumentInfo(
    val symbol: String? = null,
    val lotSizeFilter: BybitLotSizeFilter? = null,
    val priceFilter: BybitPriceFilter? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitLotSizeFilter(
    val minOrderQty: String? = null,
    val maxOrderQty: String? = null,
    val maxMktOrderQty: String? = null,
    val qtyStep: String? = null,
    val minNotionalValue: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitPriceFilter(
    val tickSize: String? = null,
    val minPrice: String? = null,
    val maxPrice: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BybitOrderResult(
    val orderId: String? = null,
    val orderLinkId: String? = null,
)
