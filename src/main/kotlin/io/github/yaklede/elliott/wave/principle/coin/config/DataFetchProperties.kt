package io.github.yaklede.elliott.wave.principle.coin.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("data-fetch")
data class DataFetchProperties(
    val enabled: Boolean = false,
    val intervals: List<String> = listOf("5"),
    val daysBack: Int = 365,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val outputDir: String = "data",
)
