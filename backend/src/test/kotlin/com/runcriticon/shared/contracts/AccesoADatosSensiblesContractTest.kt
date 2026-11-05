package com.runcriticon.shared.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.shared.api.events.AccesoADatosSensibles
import com.runcriticon.testing.ContractTestMapper
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [AccesoADatosSensibles] serializado cumple su JSON Schema v1. */
@Tag("contract")
class AccesoADatosSensiblesContractTest {
    private val mapper = ContractTestMapper.create()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/shared/acceso-datos-sensibles-v1.json").toUri())

    @Test
    fun `AccesoADatosSensibles cumple el JSON Schema v1`() {
        val evento =
            AccesoADatosSensibles(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = null,
                recurso = "alumno_perfil",
                sujetoId = UUID.randomUUID(),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
