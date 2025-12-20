package io.github.yaklede.elliott.wave.principle.coin.application.example

import io.github.yaklede.elliott.wave.principle.coin.domain.example.model.Example

interface ExampleOutport {
    fun save(example: Example): Example
}
