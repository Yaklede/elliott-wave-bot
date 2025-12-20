package io.github.yaklede.elliott.wave.principle.coin.application.example.response

import com.fasterxml.jackson.annotation.JsonCreator

data class CreateExampleResponse @JsonCreator constructor(
    val id: Long,
)
