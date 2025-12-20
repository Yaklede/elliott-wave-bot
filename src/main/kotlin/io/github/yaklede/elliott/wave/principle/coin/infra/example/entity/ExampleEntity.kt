package io.github.yaklede.elliott.wave.principle.coin.infra.example.entity

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "example")
class ExampleEntity(
    @[Id GeneratedValue(strategy = GenerationType.IDENTITY)]
    val id: Long = 0L,
    val example: String
)
