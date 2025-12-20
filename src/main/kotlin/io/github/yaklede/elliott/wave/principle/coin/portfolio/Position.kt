package io.github.yaklede.elliott.wave.principle.coin.portfolio

import java.math.BigDecimal

enum class PositionSide {
    LONG,
    FLAT,
}

data class Position(
    val side: PositionSide,
    val qty: BigDecimal,
    val avgPrice: BigDecimal,
    val stopPrice: BigDecimal? = null,
    val takeProfitPrice: BigDecimal? = null,
    val entryTimeMs: Long? = null,
) {
    companion object {
        fun flat(): Position = Position(PositionSide.FLAT, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}


data class TradeRecord(
    val entryPrice: BigDecimal,
    val exitPrice: BigDecimal,
    val qty: BigDecimal,
    val pnl: BigDecimal,
    val entryTimeMs: Long,
    val exitTimeMs: Long,
)
