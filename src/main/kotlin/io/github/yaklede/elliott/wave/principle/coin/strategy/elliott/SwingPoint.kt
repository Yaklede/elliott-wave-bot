package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import java.math.BigDecimal


enum class SwingType {
    HIGH,
    LOW,
}

data class SwingPoint(
    val timeMs: Long,
    val price: BigDecimal,
    val type: SwingType,
)
