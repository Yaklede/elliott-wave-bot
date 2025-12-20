package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import java.io.File
import java.math.BigDecimal

class CsvCandleLoader {
    fun load(path: String): List<Candle> {
        val file = File(path)
        if (!file.exists()) return emptyList()
        return file.readLines()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split(',')
                if (parts.size < 6) return@mapNotNull null
                if (!parts[0].first().isDigit()) return@mapNotNull null
                Candle(
                    timeOpenMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
                    open = parts[1].toBigDecimalOrNull() ?: return@mapNotNull null,
                    high = parts[2].toBigDecimalOrNull() ?: return@mapNotNull null,
                    low = parts[3].toBigDecimalOrNull() ?: return@mapNotNull null,
                    close = parts[4].toBigDecimalOrNull() ?: return@mapNotNull null,
                    volume = parts[5].toBigDecimalOrNull() ?: BigDecimal.ZERO,
                )
            }
            .sortedBy { it.timeOpenMs }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? =
    try {
        BigDecimal(this)
    } catch (ex: NumberFormatException) {
        null
    }
