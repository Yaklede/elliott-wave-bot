package io.github.yaklede.elliott.wave.principle.coin.api

import io.github.yaklede.elliott.wave.principle.coin.execution.BotStateStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApiController(
    private val botStateStore: BotStateStore,
) {
    @GetMapping("/health")
    fun health(): Map<String, String> = mapOf("status" to "OK")

    @GetMapping("/status")
    fun status(): BotStatus = botStateStore.snapshot()
}
