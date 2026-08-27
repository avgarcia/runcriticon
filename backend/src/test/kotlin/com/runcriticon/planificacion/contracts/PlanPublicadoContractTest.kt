package com.runcriticon.planificacion.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.api.PublishedPersonalization
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.planificacion.api.events.PlanPublicado
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [PlanPublicado] serializado cumple su JSON Schema v1. */
@Tag("contract")
class PlanPublicadoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/planificacion/plan-publicado-v1.json").toUri())

    @Test
    fun `PlanPublicado con sesiones y snapshot cumple el JSON Schema v1`() {
        val evento =
            PlanPublicado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                grupoId = UUID.randomUUID(),
                snapshotAlumnos = listOf(UUID.randomUUID(), UUID.randomUUID()),
                sesiones = listOf(sesionSeries(), sesionDescanso()),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }

    private fun sesionSeries() =
        PublishedSession(
            dia = LocalDate.of(2026, 8, 18),
            tipo = "SERIES",
            volumenTipo = "DISTANCIA",
            volumenMetros = 3200,
            volumenMinutos = null,
            ritmoTipo = "ABSOLUTO",
            ritmoSegundosPorKm = 225,
            ritmoReferencia = null,
            ritmoDeltaSegundosPorKm = null,
            notas = "8x400",
        )

    private fun sesionDescanso() =
        PublishedSession(
            dia = LocalDate.of(2026, 8, 19),
            tipo = "DESCANSO",
            volumenTipo = null,
            volumenMetros = null,
            volumenMinutos = null,
            ritmoTipo = null,
            ritmoSegundosPorKm = null,
            ritmoReferencia = null,
            ritmoDeltaSegundosPorKm = null,
            notas = null,
        )

    @Test
    fun `PlanPublicado con snapshot vacio cumple el JSON Schema v1`() {
        val evento =
            PlanPublicado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = null,
                traceparent = null,
                grupoId = UUID.randomUUID(),
                snapshotAlumnos = emptyList(),
                sesiones =
                    listOf(
                        PublishedSession(
                            dia = LocalDate.of(2026, 8, 18),
                            tipo = "RODAJE",
                            volumenTipo = "TIEMPO",
                            volumenMetros = null,
                            volumenMinutos = 45,
                            ritmoTipo = "RELATIVO",
                            ritmoSegundosPorKm = null,
                            ritmoReferencia = "10K",
                            ritmoDeltaSegundosPorKm = 10,
                            notas = null,
                        ),
                    ),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }

    /** LAL-26 AC2: personalizaciones creadas antes de publicar viajan dentro del propio `PlanPublicado`. */
    @Test
    fun `PlanPublicado con personalizaciones cumple el JSON Schema v1`() {
        val evento =
            PlanPublicado(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-17T09:00:00Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = null,
                grupoId = UUID.randomUUID(),
                snapshotAlumnos = listOf(UUID.randomUUID()),
                sesiones = listOf(sesionSeries()),
                personalizaciones =
                    listOf(
                        PublishedPersonalization(
                            sesionId = UUID.randomUUID(),
                            dia = LocalDate.of(2026, 8, 18),
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
                        ),
                    ),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
