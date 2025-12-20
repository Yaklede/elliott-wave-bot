package io.github.yaklede.elliott.wave.principle.coin.execution

import io.github.yaklede.elliott.wave.principle.coin.config.BotMode
import io.github.yaklede.elliott.wave.principle.coin.config.BotProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LiveModeGuard(
    private val botProperties: BotProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ensureLiveAllowed() {
        val env = System.getenv("BOT_ENABLE_LIVE")
        if (botProperties.mode != BotMode.LIVE || env != "YES") {
            log.warn("Live trading blocked. Set bot.mode=LIVE and BOT_ENABLE_LIVE=YES to enable.")
            throw IllegalStateException("LIVE mode is disabled by default. Explicitly enable to place real orders.")
        }
    }
}
