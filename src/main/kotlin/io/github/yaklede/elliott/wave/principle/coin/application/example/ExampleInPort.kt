package io.github.yaklede.elliott.wave.principle.coin.application.example

import io.github.yaklede.elliott.wave.principle.coin.application.example.request.CreateExampleReqeust
import io.github.yaklede.elliott.wave.principle.coin.application.example.response.CreateExampleResponse

interface ExampleInPort {
    fun create(request: CreateExampleReqeust): CreateExampleResponse
}
