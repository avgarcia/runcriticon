package com.runcriticon.shared.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.runcriticon.architecture.eventNameMatchesSchemaFile
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.testing.IntegrationTestBase
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.modulith.events.core.EventSerializer
import java.io.File

/**
 * Retrocompatibilidad de los integration events (ADR-0007 D11): un payload de cada versión publicada, fijado en
 * `src/test/resources/schemas/{modulo}/{evento}-v{N}-example.json`, deserializa contra la clase actual.
 *
 * El riesgo real son las filas de `event_publication` sin completar que escribió la versión anterior de la app: tras
 * un despliegue, el outbox las reentrega y las deserializa con el código nuevo. Por eso se usa el [EventSerializer]
 * del contexto, el mismo que lee el outbox, y no un `ObjectMapper` montado para el test.
 *
 * Por cada payload:
 * - cumple el JSON Schema de su propia versión, para que el ejemplo no pueda alejarse del contrato que dice fijar;
 * - deserializa contra la clase actual;
 * - al reserializarlo conserva todos sus campos con el mismo valor. Un campo renombrado o quitado se perdería en
 *   silencio al deserializar; uno nuevo con valor por defecto es un cambio aditivo y está permitido.
 */
@Tag("contract")
class EventBackwardCompatibilityTest : IntegrationTestBase() {
    @Autowired private lateinit var serializer: EventSerializer

    private val json = ObjectMapper()

    @TestFactory
    fun `cada payload de una version publicada deserializa contra la clase actual`(): List<DynamicTest> =
        examples().map { example -> DynamicTest.dynamicTest(example.relativePath) { check(example) } }

    @Test
    fun `todo integration event tiene un payload de ejemplo de su version publicada`() {
        val names = examples().map { it.schemaFileName }

        integrationEvents
            .filter { event -> names.none { eventNameMatchesSchemaFile(event.simpleName, it) } }
            .map { "${it.name} no tiene payload de ejemplo en $EXAMPLES_DIR (ADR-0007 D11)" }
            .shouldBeEmpty()
    }

    private fun check(example: Example) {
        val payload = example.file.readText()
        val schema = File("../schemas/${example.module}/${example.schemaFileName}")
        withClue("${example.relativePath} sin JSON Schema: $schema") { schema.exists() shouldBe true }
        JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012)
            .getSchema(schema.toURI())
            .validate(json.readTree(payload))
            .map { it.message }
            .shouldBeEmpty()

        val eventClass = integrationEvents.single { eventNameMatchesSchemaFile(it.simpleName, example.schemaFileName) }
        val event = serializer.deserialize(payload, eventClass)

        event.shouldBeInstanceOf<IntegrationEvent>()
        missingOrChanged(json.readTree(payload), json.readTree(serializer.serialize(event).toString()), "$")
            .shouldBeEmpty()
    }

    /** Campos de [expected] que faltan en [actual] o tienen otro valor. Lo que [actual] tenga de más no cuenta. */
    private fun missingOrChanged(
        expected: JsonNode,
        actual: JsonNode?,
        path: String,
    ): List<String> =
        when {
            actual == null -> listOf("$path: falta")
            expected.isObject ->
                expected.properties().flatMap { (field, value) ->
                    missingOrChanged(value, actual.get(field), "$path.$field")
                }
            expected.isArray && actual.isArray && expected.size() == actual.size() ->
                (0 until expected.size()).flatMap { missingOrChanged(expected[it], actual[it], "$path[$it]") }
            expected == actual -> emptyList()
            else -> listOf("$path: se esperaba $expected y quedó $actual")
        }

    private fun examples(): List<Example> =
        File(EXAMPLES_DIR)
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(EXAMPLE_SUFFIX) }
            .map { Example(it) }
            .sortedBy { it.relativePath }
            .toList()

    /** Importar las clases es caro: una sola vez por instancia de test, no por payload. */
    private val integrationEvents: List<Class<*>> by lazy {
        ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.runcriticon")
            .filter { it.isAssignableTo(IntegrationEvent::class.java) && !it.isInterface }
            .map { it.reflect() }
    }

    private class Example(
        val file: File,
    ) {
        val module: String = file.parentFile.name
        val schemaFileName: String = file.name.removeSuffix(EXAMPLE_SUFFIX) + ".json"
        val relativePath: String = "$module/${file.name}"
    }

    private companion object {
        const val EXAMPLES_DIR = "src/test/resources/schemas"
        const val EXAMPLE_SUFFIX = "-example.json"
    }
}
