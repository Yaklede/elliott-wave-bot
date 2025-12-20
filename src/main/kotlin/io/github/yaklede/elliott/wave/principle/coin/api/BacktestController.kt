package io.github.yaklede.elliott.wave.principle.coin.api

import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestReport
import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestRequest
import io.github.yaklede.elliott.wave.principle.coin.backtest.BacktestRunner
import kotlinx.coroutines.reactor.mono
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/backtest")
class BacktestController(
    private val backtestRunner: BacktestRunner,
) {
    @PostMapping("/run")
    fun run(@RequestBody(required = false) request: BacktestRequest?) = mono {
        backtestRunner.runForRequest(request ?: BacktestRequest())
    }

    @GetMapping("/last")
    fun last(): ResponseEntity<BacktestReport> {
        val report = backtestRunner.lastReport() ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        return ResponseEntity.ok(report)
    }
}
