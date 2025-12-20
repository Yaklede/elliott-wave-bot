package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.api.BotStatus
import io.github.yaklede.elliott.wave.principle.coin.api.PositionSummary
import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.portfolio.Position
import io.github.yaklede.elliott.wave.principle.coin.portfolio.PositionSide
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component

@Component
class BotStateStore {
    private val state = AtomicReference(
        BotStatus(
            mode = BotMode.BACKTEST,
            symbol = "",
            lastCandleTime = null,
            currentPosition = PositionSummary(
                side = PositionSide.FLAT,
                qty = BigDecimal.ZERO,
                avgPrice = BigDecimal.ZERO,
            ),
            lastSignal = null,
            killSwitchActive = false,
        )
    )

    fun update(
        mode: BotMode,
        symbol: String,
        lastCandleTime: Long?,
        position: Position,
        lastSignal: String?,
        killSwitchActive: Boolean,
    ) {
        state.set(
            BotStatus(
                mode = mode,
                symbol = symbol,
                lastCandleTime = lastCandleTime,
                currentPosition = PositionSummary(
                    side = position.side,
                    qty = position.qty,
                    avgPrice = position.avgPrice,
                ),
                lastSignal = lastSignal,
                killSwitchActive = killSwitchActive,
            )
        )
    }

    fun snapshot(): BotStatus = state.get()
}
