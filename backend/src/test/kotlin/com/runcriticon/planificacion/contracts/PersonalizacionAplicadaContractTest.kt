package com.runcriticon.planificacion.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.events.PersonalizacionAplicada
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [PersonalizacionAplicada] serializado cumple su JSON Schema v1. */
@Tag("contract")
class PersonalizacionAplicadaContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/planificacion/personalizacion-aplicada-v1.json").toUri())

    @Test
    fun `PersonalizacionAplicada con override y mensaje cumple el JSON Schema v1`() {
        val evento =
            PersonalizacionAplicada(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-20T07:30:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                grupoId = UUID.randomUUID(),
                sesionId = UUID.randomUUID(),
                dia = LocalDate.of(2026, 8, 20),
                alumnoId = UUID.randomUUID(),
                override =
                    PersonalizedSession(
                        tipo = "SERIES",
                        volumenTipo = "DISTANCIA",
                        volumenMetros = 2400,
                        volumenMinutos = null,
                        ritmoTipo = "ABSOLUTO",
                        ritmoSegundosPorKm = 240,
                        ritmoReferencia = null,
                        ritmoDeltaSegundosPorKm = null,
                        notas = "vuelve de lesión, recorte de series",
                    ),
                mensajeAlAlumno = "Hoy solo 6 series, si molesta paras.",
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }

    @Test
    fun `PersonalizacionAplicada sin mensaje ni ritmo cumple el JSON Schema v1`() {
        val evento =
            PersonalizacionAplicada(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-20T07:30:00Z"),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                grupoId = UUID.randomUUID(),
                sesionId = UUID.randomUUID(),
                dia = LocalDate.of(2026, 8, 20),
                alumnoId = UUID.randomUUID(),
                override =
                    PersonalizedSession(
                        tipo = "DESCANSO",
                        volumenTipo = null,
                        volumenMetros = null,
                        volumenMinutos = null,
                        ritmoTipo = null,
                        ritmoSegundosPorKm = null,
                        ritmoReferencia = null,
                        ritmoDeltaSegundosPorKm = null,
                        notas = null,
                    ),
                mensajeAlAlumno = null,
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
