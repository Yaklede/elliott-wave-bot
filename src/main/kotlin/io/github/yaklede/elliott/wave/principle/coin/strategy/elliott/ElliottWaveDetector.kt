package io.github.yaklede.elliott.wave.principle.coin.strategy.elliott

import java.math.BigDecimal

class ElliottWaveDetector {
    fun findWave2Setup(swings: List<SwingPoint>): Wave2Setup? {
        if (swings.size < 3) return null
        for (i in swings.size - 3 downTo 0) {
            val s0 = swings[i]
            val s1 = swings[i + 1]
            val s2 = swings[i + 2]
            if (s0.type == SwingType.LOW && s1.type == SwingType.HIGH && s2.type == SwingType.LOW) {
                if (s2.price >= s0.price) {
                    return Wave2Setup(s0, s1, s2)
                }
            }
        }
        return null
    }

    fun findWave2SetupDown(swings: List<SwingPoint>): Wave2Setup? {
        if (swings.size < 3) return null
        for (i in swings.size - 3 downTo 0) {
            val s0 = swings[i]
            val s1 = swings[i + 1]
            val s2 = swings[i + 2]
            if (s0.type == SwingType.HIGH && s1.type == SwingType.LOW && s2.type == SwingType.HIGH) {
                if (s2.price <= s0.price) {
                    return Wave2Setup(s0, s1, s2)
                }
            }
        }
        return null
    }

    fun findLatestImpulse(swings: List<SwingPoint>, enforceNoOverlap: Boolean): WaveImpulse? {
        if (swings.size < 6) return null
        val lastSix = swings.takeLast(6)
        val pattern = listOf(
            SwingType.LOW,
            SwingType.HIGH,
            SwingType.LOW,
            SwingType.HIGH,
            SwingType.LOW,
            SwingType.HIGH,
        )
        if (lastSix.map { it.type } != pattern) return null
        val impulse = WaveImpulse(
            wave1Start = lastSix[0],
            wave1End = lastSix[1],
            wave2End = lastSix[2],
            wave3End = lastSix[3],
            wave4End = lastSix[4],
            wave5End = lastSix[5],
        )
        return if (isValidImpulse(impulse, enforceNoOverlap)) impulse else null
    }

    fun isValidImpulse(impulse: WaveImpulse, enforceNoOverlap: Boolean): Boolean {
        if (impulse.wave2End.price < impulse.wave1Start.price) return false
        val wave1 = impulse.wave1End.price.subtract(impulse.wave1Start.price).abs()
        val wave3 = impulse.wave3End.price.subtract(impulse.wave2End.price).abs()
        val wave5 = impulse.wave5End.price.subtract(impulse.wave4End.price).abs()
        if (wave3 <= minOf(wave1, wave5)) return false
        if (enforceNoOverlap) {
            if (impulse.wave4End.price <= impulse.wave1End.price) return false
        }
        return true
    }
}

data class Wave2Setup(
    val wave1Start: SwingPoint,
    val wave1End: SwingPoint,
    val wave2End: SwingPoint,
)

data class WaveImpulse(
    val wave1Start: SwingPoint,
    val wave1End: SwingPoint,
    val wave2End: SwingPoint,
    val wave3End: SwingPoint,
    val wave4End: SwingPoint,
    val wave5End: SwingPoint,
)
