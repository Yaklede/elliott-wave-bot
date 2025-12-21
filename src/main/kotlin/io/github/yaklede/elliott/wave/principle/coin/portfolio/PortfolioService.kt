package io.github.yaklede.elliott.wave.principle.coin.portfolio

import io.github.yaklede.elliott.wave.principle.coin.config.BacktestProperties
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class PortfolioService(
    backtestProperties: BacktestProperties,
) {
    private val trades = mutableListOf<TradeRecord>()
    private var lastMarkPrice: BigDecimal? = null

    private var mutableEquity: BigDecimal = backtestProperties.initialCapital
    private var mutablePosition: Position = Position.flat()

    val equity: BigDecimal
        get() = mutableEquity

    val position: Position
        get() = mutablePosition

    fun enterLong(
        qty: BigDecimal,
        price: BigDecimal,
        stopPrice: BigDecimal,
        takeProfitPrice: BigDecimal,
        trailActivationPrice: BigDecimal? = null,
        trailDistance: BigDecimal? = null,
        timeStopBars: Int? = null,
        breakEvenPrice: BigDecimal? = null,
        feeRate: BigDecimal,
        timeMs: Long,
        entryReason: io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason? = null,
        entryScore: BigDecimal? = null,
        confidenceScore: BigDecimal? = null,
        features: io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures? = null,
    ) {
        if (position.side != PositionSide.FLAT) return
        val fee = price.multiply(qty).multiply(feeRate)
        mutableEquity = mutableEquity.subtract(fee)
        mutablePosition = Position(
            side = PositionSide.LONG,
            qty = qty,
            avgPrice = price,
            entryFee = fee,
            stopPrice = stopPrice,
            takeProfitPrice = takeProfitPrice,
            trailActivationPrice = trailActivationPrice,
            trailDistance = trailDistance,
            timeStopBars = timeStopBars,
            breakEvenPrice = breakEvenPrice,
            entryTimeMs = timeMs,
            entryReason = entryReason,
            entryScore = entryScore,
            confidenceScore = confidenceScore,
            features = features,
            addsCount = 0,
            lastAddTimeMs = timeMs,
            lastAddPrice = price,
        )
        lastMarkPrice = price
    }

    fun enterShort(
        qty: BigDecimal,
        price: BigDecimal,
        stopPrice: BigDecimal,
        takeProfitPrice: BigDecimal,
        trailActivationPrice: BigDecimal? = null,
        trailDistance: BigDecimal? = null,
        timeStopBars: Int? = null,
        breakEvenPrice: BigDecimal? = null,
        feeRate: BigDecimal,
        timeMs: Long,
        entryReason: io.github.yaklede.elliott.wave.principle.coin.domain.EntryReason? = null,
        entryScore: BigDecimal? = null,
        confidenceScore: BigDecimal? = null,
        features: io.github.yaklede.elliott.wave.principle.coin.domain.RegimeFeatures? = null,
    ) {
        if (position.side != PositionSide.FLAT) return
        val fee = price.multiply(qty).multiply(feeRate)
        mutableEquity = mutableEquity.subtract(fee)
        mutablePosition = Position(
            side = PositionSide.SHORT,
            qty = qty,
            avgPrice = price,
            entryFee = fee,
            stopPrice = stopPrice,
            takeProfitPrice = takeProfitPrice,
            trailActivationPrice = trailActivationPrice,
            trailDistance = trailDistance,
            timeStopBars = timeStopBars,
            breakEvenPrice = breakEvenPrice,
            entryTimeMs = timeMs,
            entryReason = entryReason,
            entryScore = entryScore,
            confidenceScore = confidenceScore,
            features = features,
            addsCount = 0,
            lastAddTimeMs = timeMs,
            lastAddPrice = price,
        )
        lastMarkPrice = price
    }

    fun addToPosition(
        qty: BigDecimal,
        price: BigDecimal,
        feeRate: BigDecimal,
        timeMs: Long,
    ): Boolean {
        if (position.side == PositionSide.FLAT) return false
        if (qty <= BigDecimal.ZERO) return false
        val fee = price.multiply(qty).multiply(feeRate)
        mutableEquity = mutableEquity.subtract(fee)

        val newQty = mutablePosition.qty.add(qty)
        if (newQty <= BigDecimal.ZERO) return false
        val weighted = mutablePosition.avgPrice.multiply(mutablePosition.qty)
            .add(price.multiply(qty))
        val newAvg = weighted.divide(newQty, 8, java.math.RoundingMode.HALF_UP)
        val updatedEntryFee = (mutablePosition.entryFee ?: BigDecimal.ZERO).add(fee)

        mutablePosition = mutablePosition.copy(
            qty = newQty,
            avgPrice = newAvg,
            entryFee = updatedEntryFee,
            addsCount = mutablePosition.addsCount + 1,
            lastAddTimeMs = timeMs,
            lastAddPrice = price,
        )
        lastMarkPrice = price
        return true
    }

    fun exitLong(
        price: BigDecimal,
        feeRate: BigDecimal,
        timeMs: Long,
        exitReason: io.github.yaklede.elliott.wave.principle.coin.domain.ExitReason? = null,
    ): BigDecimal {
        if (position.side != PositionSide.LONG) return BigDecimal.ZERO
        val grossPnl = price.subtract(mutablePosition.avgPrice).multiply(mutablePosition.qty)
        val exitFee = price.multiply(mutablePosition.qty).multiply(feeRate)
        val entryFee = mutablePosition.entryFee ?: BigDecimal.ZERO
        val netPnl = grossPnl.subtract(entryFee).subtract(exitFee)
        mutableEquity = mutableEquity.add(grossPnl).subtract(exitFee)
        trades.add(
            TradeRecord(
                side = PositionSide.LONG,
                entryPrice = mutablePosition.avgPrice,
                exitPrice = price,
                qty = mutablePosition.qty,
                pnl = netPnl,
                grossPnl = grossPnl,
                entryFee = entryFee,
                exitFee = exitFee,
                entryTimeMs = mutablePosition.entryTimeMs ?: 0L,
                exitTimeMs = timeMs,
                entryReason = mutablePosition.entryReason,
                exitReason = exitReason,
                entryScore = mutablePosition.entryScore,
                confidenceScore = mutablePosition.confidenceScore,
                features = mutablePosition.features,
            )
        )
        mutablePosition = Position.flat()
        lastMarkPrice = price
        return netPnl
    }

    fun exitShort(
        price: BigDecimal,
        feeRate: BigDecimal,
        timeMs: Long,
        exitReason: io.github.yaklede.elliott.wave.principle.coin.domain.ExitReason? = null,
    ): BigDecimal {
        if (position.side != PositionSide.SHORT) return BigDecimal.ZERO
        val grossPnl = mutablePosition.avgPrice.subtract(price).multiply(mutablePosition.qty)
        val exitFee = price.multiply(mutablePosition.qty).multiply(feeRate)
        val entryFee = mutablePosition.entryFee ?: BigDecimal.ZERO
        val netPnl = grossPnl.subtract(entryFee).subtract(exitFee)
        mutableEquity = mutableEquity.add(grossPnl).subtract(exitFee)
        trades.add(
            TradeRecord(
                side = PositionSide.SHORT,
                entryPrice = mutablePosition.avgPrice,
                exitPrice = price,
                qty = mutablePosition.qty,
                pnl = netPnl,
                grossPnl = grossPnl,
                entryFee = entryFee,
                exitFee = exitFee,
                entryTimeMs = mutablePosition.entryTimeMs ?: 0L,
                exitTimeMs = timeMs,
                entryReason = mutablePosition.entryReason,
                exitReason = exitReason,
                entryScore = mutablePosition.entryScore,
                confidenceScore = mutablePosition.confidenceScore,
                features = mutablePosition.features,
            )
        )
        mutablePosition = Position.flat()
        lastMarkPrice = price
        return netPnl
    }

    fun markToMarket(price: BigDecimal) {
        lastMarkPrice = price
    }

    fun updateStopLoss(newStop: BigDecimal, trailingActive: Boolean = position.trailingActive) {
        if (position.side == PositionSide.FLAT) return
        mutablePosition = mutablePosition.copy(stopPrice = newStop, trailingActive = trailingActive)
    }

    fun unrealizedPnl(): BigDecimal {
        val mark = lastMarkPrice ?: return BigDecimal.ZERO
        return when (mutablePosition.side) {
            PositionSide.LONG -> mark.subtract(mutablePosition.avgPrice).multiply(mutablePosition.qty)
            PositionSide.SHORT -> mutablePosition.avgPrice.subtract(mark).multiply(mutablePosition.qty)
            PositionSide.FLAT -> BigDecimal.ZERO
        }
    }

    fun tradeHistory(): List<TradeRecord> = trades.toList()

    fun snapshot(): PortfolioSnapshot = PortfolioSnapshot(
        equity = mutableEquity,
        position = mutablePosition,
        lastMarkPrice = lastMarkPrice,
    )

    fun restore(snapshot: PortfolioSnapshot) {
        mutableEquity = snapshot.equity
        mutablePosition = snapshot.position
        lastMarkPrice = snapshot.lastMarkPrice
    }

    fun reset(initialCapital: BigDecimal) {
        mutableEquity = initialCapital
        mutablePosition = Position.flat()
        trades.clear()
        lastMarkPrice = null
    }
}
