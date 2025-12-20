package io.github.yaklede.elliott.wave.principle.coin.application.example.request

import com.fasterxml.jackson.annotation.JsonCreator

data class CreateExampleReqeust @JsonCreator constructor(
    val example: String
)
