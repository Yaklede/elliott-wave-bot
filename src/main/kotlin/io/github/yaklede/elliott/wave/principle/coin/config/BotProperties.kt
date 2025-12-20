package io.github.yaklede.elliott.wave.principle.coin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("bot")
data class BotProperties(
    val mode: BotMode = BotMode.BACKTEST,
)

enum class BotMode {
    BACKTEST,
    PAPER,
    LIVE,
}
