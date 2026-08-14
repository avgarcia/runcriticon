package com.runcriticon.clubtaxonomia.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [MembresiaDeGrupoCambiada] serializado cumple su JSON Schema v1. */
@Tag("contract")
class MembresiaDeGrupoCambiadaContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/club_taxonomia/membresia-de-grupo-cambiada-v1.json").toUri())

    @Test
    fun `MembresiaDeGrupoCambiada con alumnos cumple el JSON Schema v1`() {
        val evento =
            MembresiaDeGrupoCambiada(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-13T10:15:30Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                alumnos = listOf(UUID.randomUUID(), UUID.randomUUID()),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }

    @Test
    fun `MembresiaDeGrupoCambiada con el grupo vacio cumple el JSON Schema v1`() {
        val evento =
            MembresiaDeGrupoCambiada(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-13T10:15:30Z"),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                alumnos = emptyList(),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
