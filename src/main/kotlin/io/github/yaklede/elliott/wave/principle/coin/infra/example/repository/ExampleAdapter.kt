package io.github.yaklede.elliott.wave.principle.coin.infra.example.repository

import io.github.yaklede.elliott.wave.principle.coin.application.example.ExampleOutport
import io.github.yaklede.elliott.wave.principle.coin.domain.example.model.Example
import io.github.yaklede.elliott.wave.principle.coin.infra.example.extension.toDomain
import io.github.yaklede.elliott.wave.principle.coin.infra.example.extension.toEntity
import org.springframework.stereotype.Repository

@Repository
class ExampleAdapter(
    private val jpaExampleRepository: JpaExampleRepository
): ExampleOutport {
    override fun save(example: Example): Example {
        return jpaExampleRepository.save(example.toEntity()).toDomain()
    }
}
