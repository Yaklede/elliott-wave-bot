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
    val maxAtrPercent: BigDecimal = BigDecimal("0.05"),
)


data class VolumeProperties(
    val period: Int = 20,
    val minMultiplier: BigDecimal = BigDecimal("1.0"),
)

data class StrategyFeatures(
    val enableWaveFilter: Boolean = true,
    val enableTrendFilter: Boolean = true,
    val enableVolumeFilter: Boolean = true,
    val enableRegimeGate: Boolean = false,
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
)

data class RegimeProperties(
    val slopeLookbackBars: Int = 20,
    val weakSlope: BigDecimal = BigDecimal("0.002"),
    val strongSlope: BigDecimal = BigDecimal("0.01"),
    val minTradesPerBucket: Int = 12,
)
