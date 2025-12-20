package io.github.yaklede.elliott.wave.principle.coin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("portfolio")
data class PortfolioProperties(
    val statePath: String? = "data/portfolio-state.json",
)
