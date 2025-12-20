package io.github.yaklede.elliott.wave.principle.coin.application.example

import io.github.yaklede.elliott.wave.principle.coin.application.example.extension.toDomain
import io.github.yaklede.elliott.wave.principle.coin.application.example.request.CreateExampleReqeust
import io.github.yaklede.elliott.wave.principle.coin.application.example.response.CreateExampleResponse
import org.springframework.stereotype.Service

@Service
class ExampleService(
    private val exampleOutport: ExampleOutport
) : ExampleInPort {
    override fun create(request: CreateExampleReqeust): CreateExampleResponse {
        return CreateExampleResponse(exampleOutport.save(request.toDomain()).id)
    }
}
