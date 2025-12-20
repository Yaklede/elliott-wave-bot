package io.github.yaklede.elliott.wave.principle.coin.application.example.extension

import io.github.yaklede.elliott.wave.principle.coin.application.example.request.CreateExampleReqeust
import io.github.yaklede.elliott.wave.principle.coin.domain.example.model.Example

fun CreateExampleReqeust.toDomain() = Example(
    id = 0L,
    example = example
)
