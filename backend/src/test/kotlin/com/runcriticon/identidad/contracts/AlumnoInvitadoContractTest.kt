package com.runcriticon.identidad.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.identidad.api.events.AlumnoInvitado
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/**
 * Test de contrato (ADR-0007 D11): el [AlumnoInvitado] serializado cumple su JSON Schema v1.
 * Es el primer integration event del backend, así que establece el patrón del job `contractTest`
 * (`@Tag("contract")`). Solo serializa (no necesita el módulo Kotlin de Jackson); el `JavaTimeModule`
 * fuerza `occurredAt` a ISO-8601 (`date-time`) en vez de epoch.
 */
@Tag("contract")
class AlumnoInvitadoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/identidad/alumno-invitado-v1.json").toUri())

    @Test
    fun `AlumnoInvitado serializado cumple el JSON Schema v1`() {
        val evento =
            AlumnoInvitado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-06-24T10:15:30Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                name = "Marta Ruiz",
                email = "marta@club.es",
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
