package io.github.yaklede.elliott.wave.principle.coin.config

import java.math.BigDecimal
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("strategy")
data class StrategyProperties(
    val zigzag: ZigZagProperties = ZigZagProperties(),
    val elliott: ElliottProperties = ElliottProperties(),
    val volatility: VolatilityProperties = VolatilityProperties(),
    val volume: VolumeProperties = VolumeProperties(),
    val features: StrategyFeatures = StrategyFeatures(),
    val exit: ExitProperties = ExitProperties(),
    val regime: RegimeProperties = RegimeProperties(),
    val entry: EntryProperties = EntryProperties(),
    val feeAware: FeeAwareProperties = FeeAwareProperties(),
    val trendStrength: TrendStrengthProperties = TrendStrengthProperties(),
    val volExpansion: VolExpansionProperties = VolExpansionProperties(),
    val pyramiding: PyramidingProperties = PyramidingProperties(),
    val shortGate: ShortGateProperties = ShortGateProperties(),
)

data class ZigZagProperties(
    val mode: ZigZagMode = ZigZagMode.PERCENT,
    val percentThreshold: BigDecimal = BigDecimal("0.015"),
    val atrPeriod: Int = 14,
    val atrMultiplier: BigDecimal = BigDecimal("2.0"),
)

enum class ZigZagMode {
    PERCENT,
    ATR,
}


data class ElliottProperties(
    val enforceWave4NoOverlap: Boolean = true,
    val minScoreToTrade: BigDecimal = BigDecimal("0.60"),
    val swingAtrMultiplier: BigDecimal = BigDecimal("2.0"),
    val fib: FibProperties = FibProperties(),
)


data class FibProperties(
    val wave2PreferredMin: BigDecimal = BigDecimal("0.5"),
    val wave2PreferredMax: BigDecimal = BigDecimal("0.618"),
    val takeProfitExtension: BigDecimal = BigDecimal("1.618"),
)


data class VolatilityProperties(
    val atrPeriod: Int = 14,
    val minAtrPercent: BigDecimal = BigDecimal("0.001"),
    val maxAtrPercent: BigDecimal = BigDecimal("0.05"),
)


data class VolumeProperties(
    val period: Int = 20,
    val minMultiplier: BigDecimal = BigDecimal("1.0"),
)

data class StrategyFeatures(
    val enableWaveFilter: Boolean = true,
    val enableShortWave: Boolean = false,
    val enableTrendFilter: Boolean = true,
    val enableVolumeFilter: Boolean = true,
    val enableRegimeGate: Boolean = false,
    val enableSwingFallback: Boolean = false,
    val entryModel: EntryModel = EntryModel.BASELINE,
    val exitModel: ExitModel = ExitModel.FIXED,
)

enum class EntryModel {
    BASELINE,
    CONFIDENCE_THRESHOLD,
    MOMENTUM_CONFIRM,
}

enum class ExitModel {
    FIXED,
    ATR_DYNAMIC,
    TIME_STOP,
    HYBRID,
}

data class ExitProperties(
    val atrStopMultiplier: BigDecimal = BigDecimal("1.5"),
    val atrTakeProfitMultiplier: BigDecimal = BigDecimal("3.0"),
    val trailActivationAtr: BigDecimal = BigDecimal("1.0"),
    val trailDistanceAtr: BigDecimal = BigDecimal("1.0"),
    val timeStopBars: Int = 32,
    val maxStopAtrMultiplier: BigDecimal = BigDecimal("4.0"),
    val breakEvenAtr: BigDecimal = BigDecimal("1.0"),
)

data class EntryProperties(
    val minRewardRisk: BigDecimal = BigDecimal("1.2"),
)

data class FeeAwareProperties(
    val enabled: Boolean = true,
    val minEdgeMultiple: BigDecimal = BigDecimal("2.0"),
    val bufferBps: BigDecimal = BigDecimal("1.0"),
)

data class TrendStrengthProperties(
    val enabled: Boolean = true,
    val model: TrendStrengthModel = TrendStrengthModel.ER,
    val n: Int = 20,
    val threshold: BigDecimal = BigDecimal("0.35"),
)

enum class TrendStrengthModel {
    ER,
    ADX,
}

data class VolExpansionProperties(
    val enabled: Boolean = true,
    val lookback: Int = 120,
    val compressionQuantile: BigDecimal = BigDecimal("0.2"),
    val requireRising: Boolean = true,
    val recentCompressionBars: Int = 40,
    val period: Int = 20,
    val stdDevMultiplier: BigDecimal = BigDecimal("2.0"),
)

data class ShortGateProperties(
    val enabled: Boolean = false,
    val requireDowntrend: Boolean = true,
    val allowed: List<String> = emptyList(),
    val blocked: List<String> = emptyList(),
)

data class PyramidingProperties(
    val enabled: Boolean = false,
    val maxAdds: Int = 1,
    val addOnRiskFraction: BigDecimal = BigDecimal("0.5"),
    val minBarsBetweenAdds: Int = 4,
    val triggerModel: PyramidingTrigger = PyramidingTrigger.ATR_MOVE,
    val minMoveAtr: BigDecimal = BigDecimal("0.8"),
)

enum class PyramidingTrigger {
    ATR_MOVE,
}

data class RegimeProperties(
    val slopeLookbackBars: Int = 20,
    val weakSlope: BigDecimal = BigDecimal("0.002"),
    val strongSlope: BigDecimal = BigDecimal("0.01"),
    val minTradesPerBucket: Int = 12,
    val thresholds: RegimeThresholdProperties = RegimeThresholdProperties(),
    val blocked: List<String> = emptyList(),
    val allowed: List<String> = emptyList(),
)

data class RegimeThresholdProperties(
    val atrLow: BigDecimal = BigDecimal.ZERO,
    val atrHigh: BigDecimal = BigDecimal.ZERO,
    val volumeLow: BigDecimal = BigDecimal.ZERO,
    val volumeHigh: BigDecimal = BigDecimal.ZERO,
)
