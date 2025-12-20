package io.github.yaklede.elliott.wave.principle.coin.risk

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.yaklede.elliott.wave.principle.coin.config.RiskProperties
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.springframework.stereotype.Component

@Component
class RiskStateStore(
    private val properties: RiskProperties,
    private val objectMapper: ObjectMapper,
) {
    private val lock = ReentrantLock()

    fun load(): RiskState? {
        val path = properties.statePath ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return lock.withLock {
            runCatching { objectMapper.readValue(file, RiskState::class.java) }.getOrNull()
        }
    }

    fun save(state: RiskState) {
        val path = properties.statePath ?: return
        val file = File(path)
        file.parentFile?.mkdirs()
        lock.withLock {
            objectMapper.writeValue(file, state)
        }
    }
}
