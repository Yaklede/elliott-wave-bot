package io.github.yaklede.elliott.wave.principle.coin.portfolio

import java.math.BigDecimal


data class PortfolioSnapshot(
    val equity: BigDecimal,
    val position: Position,
    val lastMarkPrice: BigDecimal?,
)
