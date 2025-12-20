package io.github.yaklede.elliott.wave.principle.coin.marketdata

object IntervalUtil {
    fun intervalToMillis(interval: String): Long {
        val normalized = interval.trim().uppercase()
        return when (normalized) {
            "D" -> 86_400_000L
            "W" -> 604_800_000L
            "M" -> 2_592_000_000L
            else -> normalized.toLong() * 60_000L
        }
    }
}
