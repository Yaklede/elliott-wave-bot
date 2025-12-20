package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import org.springframework.stereotype.Component

@Component
class BybitRateLimiter {
    private val lastCallMs = AtomicLong(0)
    private val minIntervalMs: Long = 75

    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val last = lastCallMs.get()
        val waitMs = minIntervalMs - (now - last)
        if (waitMs > 0) {
            delay(waitMs)
        }
        lastCallMs.set(System.currentTimeMillis())
    }
}
