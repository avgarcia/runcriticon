package com.runcriticon.shared.contracts

import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.testing.ContractTestMapper
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

/**
 * Cada schema declara su versión con `const` (ADR-0007 D11). Con `type: integer` a secas, un payload de la v2 valida
 * contra el schema v1, y es justo durante el dual-publishing de una versión nueva cuando el test de contrato tiene
 * que distinguirlas.
 */
@Tag("contract")
class SchemaVersionContractTest {
    private val mapper = ContractTestMapper.create()
    private val schemas =
        Files.walk(Path.of("../schemas")).use { paths ->
            paths.filter { it.name.matches(VERSIONED) }.toList()
        }

    @Test
    fun `hay schemas versionados que comprobar`() {
        schemas.shouldNotBeEmpty()
    }

    @Test
    fun `el const de version de cada schema coincide con el vN de su nombre`() {
        val incoherentes =
            schemas.mapNotNull { schema ->
                val esperada = VERSIONED.matchEntire(schema.name)!!.groupValues[1].toInt()
                val declarada =
                    mapper
                        .readTree(schema.toFile())
                        .path("properties")
                        .path("version")
                        .path("const")
                if (declarada.isInt && declarada.asInt() == esperada) null else "${schema.name}: const=$declarada"
            }

        incoherentes.shouldBeEmpty()
    }

    @Test
    fun `un payload con otra version no valida contra el schema v1`() {
        val schema =
            JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(Path.of("../schemas/shared/acceso-denegado-v1.json").toUri())
        val payload =
            mapper.readTree(
                """
                {"eventId":"0198d2c4-0000-7000-8000-000000000001","aggregateId":"0198d2c4-0000-7000-8000-000000000002",
                 "occurredAt":"2026-11-05T10:00:00Z","version":2,"clubId":"0198d2c4-0000-7000-8000-000000000003",
                 "actorId":null,"traceparent":null,"recurso":"PLAN:PUBLISH","motivo":"RBAC","sujetoId":null}
                """.trimIndent(),
            )

        val errores = schema.validate(payload)

        errores.any { it.message.contains("version") || it.message.contains("const") } shouldBe true
    }

    private companion object {
        val VERSIONED = Regex(""".+-v(\d+)\.json""")
    }
}
