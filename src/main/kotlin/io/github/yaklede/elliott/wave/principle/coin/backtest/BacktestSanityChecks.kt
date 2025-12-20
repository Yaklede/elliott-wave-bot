package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.marketdata.Candle
import org.springframework.stereotype.Component

@Component
class BacktestSanityChecks {
    fun validate(candles: List<Candle>, intervalMs: Long): SanityCheckResult {
        if (candles.isEmpty()) return SanityCheckResult(false, listOf("No candles provided"))
        val issues = mutableListOf<String>()
        for (i in 1 until candles.size) {
            val prev = candles[i - 1]
            val curr = candles[i]
            if (curr.timeOpenMs <= prev.timeOpenMs) {
                issues.add("Candle order not strictly ascending at index $i")
                break
            }
            val delta = curr.timeOpenMs - prev.timeOpenMs
            if (delta % intervalMs != 0L) {
                issues.add("Candle gap misaligned at index $i: delta=$delta expected multiple of $intervalMs")
                break
            }
        }
        for (candle in candles) {
            if (candle.timeOpenMs % intervalMs != 0L) {
                issues.add("Candle not aligned to interval boundary at ${candle.timeOpenMs}")
                break
            }
        }
        return SanityCheckResult(issues.isEmpty(), issues)
    }

    fun validateOrThrow(candles: List<Candle>, intervalMs: Long) {
        val result = validate(candles, intervalMs)
        if (!result.ok) {
            throw IllegalStateException("Backtest sanity checks failed: ${result.issues.joinToString("; ")}")
        }
    }
}

data class SanityCheckResult(
    val ok: Boolean,
    val issues: List<String>,
)
