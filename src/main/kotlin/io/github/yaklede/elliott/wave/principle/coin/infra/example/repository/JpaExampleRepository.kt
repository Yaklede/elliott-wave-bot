package io.github.yaklede.elliott.wave.principle.coin.infra.example.repository

import io.github.yaklede.elliott.wave.principle.coin.infra.example.entity.ExampleEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaExampleRepository: JpaRepository<ExampleEntity, Long>
