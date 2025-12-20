package io.github.yaklede.elliott.wave.principle.coin.exchange.bybit

import io.github.yaklede.elliott.wave.principle.coin.config.BybitProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class BybitWebClientConfig(
    private val properties: BybitProperties,
) {
    @Bean
    fun bybitWebClient(): WebClient {
        return WebClient.builder().baseUrl(properties.baseUrl).build()
    }
}
