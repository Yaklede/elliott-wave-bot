package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import io.github.yaklede.elliott.wave.principle.coin.config.ExitModel
import io.github.yaklede.elliott.wave.principle.coin.config.ExitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyFeatures
import io.github.yaklede.elliott.wave.principle.coin.config.StrategyProperties
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExitPlanBuilderTest {
    @Test
    fun `atr dynamic exit uses atr multipliers`() {
        val props = StrategyProperties(
            features = StrategyFeatures(exitModel = ExitModel.ATR_DYNAMIC),
            exit = ExitProperties(
                atrStopMultiplier = BigDecimal("2.0"),
                atrTakeProfitMultiplier = BigDecimal("3.0"),
                trailActivationAtr = BigDecimal("1.0"),
                trailDistanceAtr = BigDecimal("1.0"),
                timeStopBars = 10,
            ),
        )
        val builder = ExitPlanBuilder(props)
        val plan = builder.build(
            entryPrice = BigDecimal("100"),
            stopCandidate = BigDecimal("90"),
            takeProfitCandidate = BigDecimal("130"),
            atrValue = BigDecimal("2"),
        )
        assertEquals(0, plan.stopPrice!!.compareTo(BigDecimal("96")))
        assertEquals(0, plan.takeProfitPrice!!.compareTo(BigDecimal("106")))
        assertEquals(0, plan.trailActivationPrice!!.compareTo(BigDecimal("102")))
        assertEquals(0, plan.trailDistance!!.compareTo(BigDecimal("2")))
    }
}
