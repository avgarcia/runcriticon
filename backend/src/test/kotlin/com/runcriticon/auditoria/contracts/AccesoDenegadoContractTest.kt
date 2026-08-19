package com.runcriticon.auditoria.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.auditoria.api.events.AccesoDenegado
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [AccesoDenegado] serializado cumple su JSON Schema v1. */
@Tag("contract")
class AccesoDenegadoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/auditoria/acceso-denegado-v1.json").toUri())

    @Test
    fun `AccesoDenegado con sujeto cumple el JSON Schema v1`() {
        val evento =
            AccesoDenegado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                recurso = "PLAN:PUBLISH",
                motivo = "NotCoachOfGroup",
                sujetoId = UUID.randomUUID(),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }

    @Test
    fun `AccesoDenegado sin sujeto ni traceparent cumple el JSON Schema v1`() {
        val evento =
            AccesoDenegado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                recurso = "PLAN:PUBLISH",
                motivo = "RBAC",
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
