package io.github.yaklede.elliott.wave.principle.coin.bootstrap.controller

import bz.bix.agency.approval.system.application.common.response.toApplicationResponse
import io.github.yaklede.elliott.wave.principle.coin.application.example.ExampleInPort
import io.github.yaklede.elliott.wave.principle.coin.application.example.request.CreateExampleReqeust
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/example")
class ExampleController(
    private val exampleInPort: ExampleInPort
) {
    @PostMapping
    fun createExample(
        reqeust: CreateExampleReqeust
    ) = exampleInPort.create(reqeust)
        .toApplicationResponse()
}
