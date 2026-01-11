package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
import io.github.yaklede.elliott.wave.principle.coin.domain.RejectReason
import java.math.BigDecimal

enum class SignalType {
    ENTER_LONG,
    ENTER_SHORT,
    EXIT_LONG,
    EXIT_SHORT,
    HOLD,
}

data class ExitPlan(
    val stopPrice: BigDecimal?,
    val takeProfitPrice: BigDecimal?,
    val trailActivationPrice: BigDecimal?,
    val trailDistance: BigDecimal?,
    val timeStopBars: Int?,
    val breakEvenPrice: BigDecimal?,
)

data class TradeSignal(
    val type: SignalType,
    val entryPrice: BigDecimal? = null,
    val exitPlan: ExitPlan? = null,
    val score: BigDecimal? = null,
    val confidence: BigDecimal? = null,
    val entryReason: EntryReason? = null,
    val rejectReason: RejectReason? = null,
    val features: RegimeFeatures? = null,
)
