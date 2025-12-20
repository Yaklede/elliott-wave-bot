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
        feeRate: BigDecimal,
        timeMs: Long,
    ) {
        if (position.side != PositionSide.FLAT) return
        val fee = price.multiply(qty).multiply(feeRate)
        mutableEquity = mutableEquity.subtract(fee)
        mutablePosition = Position(PositionSide.LONG, qty, price, stopPrice, takeProfitPrice, timeMs)
        lastMarkPrice = price
    }

    fun exitLong(
        price: BigDecimal,
        feeRate: BigDecimal,
        timeMs: Long,
    ): BigDecimal {
        if (position.side != PositionSide.LONG) return BigDecimal.ZERO
        val pnl = price.subtract(mutablePosition.avgPrice).multiply(mutablePosition.qty)
        val fee = price.multiply(mutablePosition.qty).multiply(feeRate)
        mutableEquity = mutableEquity.add(pnl).subtract(fee)
        trades.add(
            TradeRecord(
                entryPrice = mutablePosition.avgPrice,
                exitPrice = price,
                qty = mutablePosition.qty,
                pnl = pnl.subtract(fee),
                entryTimeMs = mutablePosition.entryTimeMs ?: 0L,
                exitTimeMs = timeMs,
            )
        )
        mutablePosition = Position.flat()
        lastMarkPrice = price
        return pnl.subtract(fee)
    }

    fun markToMarket(price: BigDecimal) {
        lastMarkPrice = price
    }

    fun unrealizedPnl(): BigDecimal {
        val mark = lastMarkPrice ?: return BigDecimal.ZERO
        if (mutablePosition.side != PositionSide.LONG) return BigDecimal.ZERO
        return mark.subtract(mutablePosition.avgPrice).multiply(mutablePosition.qty)
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
