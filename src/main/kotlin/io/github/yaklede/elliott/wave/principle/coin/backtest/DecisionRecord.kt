package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason
import io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures
import io.github.yaklede.elliott.wave.principle.coin.domain.RejectReason
import io.github.yaklede.elliott.wave.principle.coin.strategy.elliott.SignalType
import java.math.BigDecimal

data class DecisionRecord(
    val timeMs: Long,
    val signalType: SignalType,
    val entryReason: EntryReason? = null,
    val rejectReason: RejectReason? = null,
    val score: BigDecimal? = null,
    val confidence: BigDecimal? = null,
    val features: RegimeFeatures? = null,
)
