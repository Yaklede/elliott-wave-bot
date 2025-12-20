package io.github.yaklede.elliott.wave.principle.coin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("research")
data class ResearchProperties(
    val enabled: Boolean = false,
    val mode: ResearchMode = ResearchMode.REPORT,
    val outputDir: String = "build/reports",
    val walkForward: WalkForwardProperties = WalkForwardProperties(),
)

enum class ResearchMode {
    REPORT,
    ABLATION,
    WALK_FORWARD,
}

data class WalkForwardProperties(
    val trainDays: Int = 90,
    val testDays: Int = 30,
    val minTrades: Int = 8,
    val maxTrials: Int = 12,
    val enableRegimeGate: Boolean = true,
)
