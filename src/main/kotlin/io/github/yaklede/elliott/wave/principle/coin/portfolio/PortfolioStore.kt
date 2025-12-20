package io.github.yaklede.elliott.wave.principle.coin.portfolio

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.yaklede.elliott.wave.principle.coin.config.PortfolioProperties
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.springframework.stereotype.Component

@Component
class PortfolioStore(
    private val properties: PortfolioProperties,
    private val objectMapper: ObjectMapper,
) {
    private val lock = ReentrantLock()

    fun load(): PortfolioSnapshot? {
        val path = properties.statePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return lock.withLock {
            runCatching { objectMapper.readValue(file, PortfolioSnapshot::class.java) }.getOrNull()
        }
    }

    fun save(snapshot: PortfolioSnapshot) {
        val path = properties.statePath ?: return
        val file = File(path)
        file.parentFile?.mkdirs()
        lock.withLock {
            objectMapper.writeValue(file, snapshot)
        }
    }
}
