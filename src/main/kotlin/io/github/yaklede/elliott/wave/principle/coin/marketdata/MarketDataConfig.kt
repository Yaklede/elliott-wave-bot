package io.github.yaklede.elliott.wave.principle.coin.marketdata

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MarketDataConfig {
    @Bean
    fun candleRepository(): CandleRepository = InMemoryCandleRepository()
}
