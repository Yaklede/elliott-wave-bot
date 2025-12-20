package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ElliottRulesTest {
    private val detector = ElliottWaveDetector()

    @Test
    fun `invalid when wave2 goes below wave1 start`() {
        val impulse = WaveImpulse(
            wave1Start = swing(0, 100, SwingType.LOW),
            wave1End = swing(1, 120, SwingType.HIGH),
            wave2End = swing(2, 90, SwingType.LOW),
            wave3End = swing(3, 150, SwingType.HIGH),
            wave4End = swing(4, 140, SwingType.LOW),
            wave5End = swing(5, 170, SwingType.HIGH),
        )
        assertFalse(detector.isValidImpulse(impulse, enforceNoOverlap = true))
    }

    @Test
    fun `invalid when wave3 is shortest`() {
        val impulse = WaveImpulse(
            wave1Start = swing(0, 100, SwingType.LOW),
            wave1End = swing(1, 120, SwingType.HIGH),
            wave2End = swing(2, 110, SwingType.LOW),
            wave3End = swing(3, 120, SwingType.HIGH),
            wave4End = swing(4, 115, SwingType.LOW),
            wave5End = swing(5, 140, SwingType.HIGH),
        )
        assertFalse(detector.isValidImpulse(impulse, enforceNoOverlap = false))
    }

    @Test
    fun `invalid when wave4 overlaps wave1 territory`() {
        val impulse = WaveImpulse(
            wave1Start = swing(0, 100, SwingType.LOW),
            wave1End = swing(1, 120, SwingType.HIGH),
            wave2End = swing(2, 110, SwingType.LOW),
            wave3End = swing(3, 150, SwingType.HIGH),
            wave4End = swing(4, 115, SwingType.LOW),
            wave5End = swing(5, 160, SwingType.HIGH),
        )
        assertFalse(detector.isValidImpulse(impulse, enforceNoOverlap = true))
    }

    @Test
    fun `valid impulse passes rules`() {
        val impulse = WaveImpulse(
            wave1Start = swing(0, 100, SwingType.LOW),
            wave1End = swing(1, 120, SwingType.HIGH),
            wave2End = swing(2, 110, SwingType.LOW),
            wave3End = swing(3, 150, SwingType.HIGH),
            wave4End = swing(4, 125, SwingType.LOW),
            wave5End = swing(5, 165, SwingType.HIGH),
        )
        assertTrue(detector.isValidImpulse(impulse, enforceNoOverlap = true))
    }

    private fun swing(time: Long, price: Int, type: SwingType): SwingPoint =
        SwingPoint(timeMs = time, price = BigDecimal(price), type = type)
}
