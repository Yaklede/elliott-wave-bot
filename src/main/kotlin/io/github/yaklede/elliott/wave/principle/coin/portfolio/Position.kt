package io.github.yaklede.elliott.wave.principle.coin.portfolio

import io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason
import io.github.yaklede.elliott.wave.principle.coin.domain.ExitReason
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
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
    val trailActivationPrice: BigDecimal? = null,
    val trailDistance: BigDecimal? = null,
    val timeStopBars: Int? = null,
    val breakEvenPrice: BigDecimal? = null,
    val entryTimeMs: Long? = null,
    val entryReason: EntryReason? = null,
    val entryScore: BigDecimal? = null,
    val confidenceScore: BigDecimal? = null,
    val features: RegimeFeatures? = null,
    val trailingActive: Boolean = false,
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
    val entryReason: EntryReason? = null,
    val exitReason: ExitReason? = null,
    val entryScore: BigDecimal? = null,
    val confidenceScore: BigDecimal? = null,
    val features: RegimeFeatures? = null,
)
