package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import java.math.BigDecimal

enum class SignalType {
    ENTER_LONG,
    EXIT_LONG,
    HOLD,
}

data class TradeSignal(
    val type: SignalType,
    val entryPrice: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    val takeProfitPrice: BigDecimal? = null,
    val score: BigDecimal? = null,
)
