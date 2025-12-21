package io.github.yaklede.elliott.wave.principle.coin.backtest

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import io.github.yaklede.elliott.wave.principle.coin.config.DataFetchProperties
import io.github.yaklede.elliott.wave.principle.coin.exchange.bybit.BybitV5Client
import io.github.yaklede.elliott.wave.principle.coin.marketdata.IntervalUtil
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class DataFetchRunner(
    private val dataFetchProperties: DataFetchProperties,
    private val bybitProperties: BybitProperties,
    private val bybitV5Client: BybitV5Client,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun runIfEnabled() {
        if (!dataFetchProperties.enabled) return

        val endMs = dataFetchProperties.endMs ?: Instant.now().toEpochMilli()
        val startMs = dataFetchProperties.startMs
            ?: Instant.ofEpochMilli(endMs).minus(dataFetchProperties.daysBack.toLong(), ChronoUnit.DAYS).toEpochMilli()

        val outputDir = Path.of(dataFetchProperties.outputDir)
        Files.createDirectories(outputDir)

        val intervals = dataFetchProperties.intervals
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (intervals.isEmpty()) {
            log.warn("Data fetch enabled but no intervals configured")
            return
        }

        runBlocking {
            for (interval in intervals) {
                val candles = bybitV5Client.getKlinesPaged(
                    category = bybitProperties.category,
                    symbol = bybitProperties.symbol,
                    interval = interval,
                    start = startMs,
                    end = endMs,
                )
                if (candles.isEmpty()) {
                    log.warn("No candles fetched for interval {}", interval)
                    continue
                }
                val name = "bybit_${bybitProperties.symbol.lowercase()}_${interval}m_${dataFetchProperties.daysBack}d.csv"
                val path = outputDir.resolve(name)
                val lines = candles.map { candle ->
                    listOf(
                        candle.timeOpenMs,
                        candle.open.stripTrailingZeros().toPlainString(),
                        candle.high.stripTrailingZeros().toPlainString(),
                        candle.low.stripTrailingZeros().toPlainString(),
                        candle.close.stripTrailingZeros().toPlainString(),
                        candle.volume.stripTrailingZeros().toPlainString(),
                    ).joinToString(",")
                }
                Files.writeString(path, lines.joinToString("\n"))
                val intervalMs = IntervalUtil.intervalToMillis(interval)
                log.info(
                    "Saved {} candles for interval {} to {} (range {} -> {})",
                    candles.size,
                    interval,
                    path,
                    startMs,
                    endMs
                )
                log.info("Interval ms = {}", intervalMs)
            }
        }

        log.info("Data fetch complete. Stop the application when ready.")
    }
}
