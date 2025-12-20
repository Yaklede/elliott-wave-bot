package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class StrategyEngine(
    private val properties: StrategyProperties,
) {
    private val zigZagExtractor = ZigZagExtractor(properties.zigzag)
    private val detector = ElliottWaveDetector()
    private val scorer = ElliottScorer()

    fun evaluate(candles: List<Candle>, htfCandles: List<Candle>): TradeSignal {
        if (candles.size < 10) return TradeSignal(SignalType.HOLD)
        val swings = zigZagExtractor.extract(candles)
        val wave2 = detector.findWave2Setup(swings) ?: return TradeSignal(SignalType.HOLD)
        val score = scorer.scoreWave2(wave2, htfCandles, properties.elliott)
        if (score < properties.elliott.minScoreToTrade) {
            return TradeSignal(SignalType.HOLD, score = score)
        }
        val lastClose = candles.last().close
        if (lastClose > wave2.wave1End.price) {
            val wave1Size = wave2.wave1End.price.subtract(wave2.wave1Start.price)
            val takeProfit = wave2.wave1End.price.add(
                wave1Size.multiply(properties.elliott.fib.takeProfitExtension)
            )
            return TradeSignal(
                type = SignalType.ENTER_LONG,
                entryPrice = lastClose,
                stopPrice = wave2.wave1Start.price,
                takeProfitPrice = takeProfit,
                score = score,
            )
        }
        return TradeSignal(SignalType.HOLD, score = score)
    }
}
