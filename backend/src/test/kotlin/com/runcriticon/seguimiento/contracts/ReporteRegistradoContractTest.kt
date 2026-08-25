package com.runcriticon.seguimiento.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.seguimiento.api.events.ReporteRegistrado
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [ReporteRegistrado] serializado cumple su JSON Schema v1. */
@Tag("contract")
class ReporteRegistradoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/seguimiento/reporte-registrado-v1.json").toUri())

    @Test
    fun `HECHO con valoracion cumple el JSON Schema v1`() {
        val evento =
            ReporteRegistrado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T18:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                planId = UUID.randomUUID(),
                dia = LocalDate.parse("2026-08-17"),
                estado = "HECHO",
                valoracion = 4,
                motivo = null,
                marcaDolor = false,
            )

        schema.validate(mapper.valueToTree<JsonNode>(evento)).shouldBeEmpty()
    }

    @Test
    fun `NO_HECHO con motivo y sin valoracion cumple el JSON Schema v1`() {
        val evento =
            ReporteRegistrado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T18:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                planId = UUID.randomUUID(),
                dia = LocalDate.parse("2026-08-17"),
                estado = "NO_HECHO",
                valoracion = null,
                motivo = "MOLESTIAS",
                marcaDolor = true,
            )

        schema.validate(mapper.valueToTree<JsonNode>(evento)).shouldBeEmpty()
    }
}
