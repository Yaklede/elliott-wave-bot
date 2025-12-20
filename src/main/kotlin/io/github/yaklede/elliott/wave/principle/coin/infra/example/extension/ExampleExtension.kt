package io.github.yaklede.elliott.wave.principle.coin.infra.example.extension

import io.github.yaklede.elliott.wave.principle.coin.domain.example.model.Example
import io.github.yaklede.elliott.wave.principle.coin.infra.example.entity.ExampleEntity

fun Example.toEntity() = ExampleEntity(
    id = id,
    example = example
)

fun ExampleEntity.toDomain() = Example(
    id = id,
    example = example
)
