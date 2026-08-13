package com.runcriticon.clubtaxonomia.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.clubtaxonomia.api.events.AlumnoEliminadoDeGrupo
import io.kotest.matchers.collections.shouldBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

/** Test de contrato (ADR-0007 D11): el [AlumnoEliminadoDeGrupo] serializado cumple su JSON Schema v1. */
@Tag("contract")
class AlumnoEliminadoDeGrupoContractTest {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    private val schema =
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(Path.of("../schemas/club_taxonomia/alumno-eliminado-de-grupo-v1.json").toUri())

    @Test
    fun `AlumnoEliminadoDeGrupo serializado cumple el JSON Schema v1`() {
        val evento =
            AlumnoEliminadoDeGrupo(
                eventId = UUID.randomUUID(),
                aggregateId = UUID.randomUUID(),
                occurredAt = Instant.parse("2026-08-13T10:15:30Z"),
                clubId = UUID.randomUUID(),
                actorId = UUID.randomUUID(),
                traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                groupId = UUID.randomUUID(),
            )

        val json = mapper.valueToTree<JsonNode>(evento)

        schema.validate(json).shouldBeEmpty()
    }
}
