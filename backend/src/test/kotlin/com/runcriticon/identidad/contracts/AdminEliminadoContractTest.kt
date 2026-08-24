package com.runcriticon.identidad.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.identidad.api.events.AdminEliminado
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Test de contrato: el [AdminEliminado] serializado cumple su JSON Schema v1. */
@Tag("contract")
class AdminEliminadoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/identidad/admin-eliminado-v1.json").toUri())

    @Test
    fun `AdminEliminado serializado cumple el JSON Schema v1`() {
        val json = mapper.valueToTree<JsonNode>(evento())

        schema.validate(json).shouldBeEmpty()
    }

    /**
     * El schema declara `additionalProperties: false`, así que este test falla en cuanto alguien añada `name` o
     * `email` al evento. Es la red que protege la decisión de no propagar PII en un evento de supresión: su payload
     * sobrevive en el outbox al dato que se acaba de borrar.
     */
    @Test
    fun `el schema rechaza que el evento lleve datos personales`() {
        val json = mapper.valueToTree<JsonNode>(evento()) as com.fasterxml.jackson.databind.node.ObjectNode
        json.put("email", "admin@club.es")

        schema.validate(json).shouldNotBeEmpty()
    }

    private fun evento() =
        AdminEliminado(
            eventId = UUID.randomUUID(),
            aggregateId = UUID.randomUUID(),
            occurredAt = Instant.parse("2026-08-01T10:15:30Z"),
            clubId = UUID.randomUUID(),
            actorId = UUID.randomUUID(),
            traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
        )
}
