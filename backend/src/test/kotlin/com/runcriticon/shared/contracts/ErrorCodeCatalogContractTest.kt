package com.runcriticon.shared.contracts

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Cada `code` que el backend puede devolver tiene su mensaje en el catálogo del frontend (ADR-0012 D19). Si falta,
 * el usuario ve el mensaje genérico de fallback en lugar del específico, y nada lo avisa: el frontend no sabe qué
 * códigos emite el backend.
 *
 * Los códigos se leen de los `ErrorMapper` de cada módulo, del controlador de sesión y del advice global. Se toma
 * todo literal en mayúsculas con guiones bajos de esos ficheros: en ellos solo aparecen códigos de error.
 */
@Tag("contract")
class ErrorCodeCatalogContractTest {
    private val emisores =
        listOf(
            "clubtaxonomia/infrastructure/rest/mappers/ErrorMapper.kt",
            "identidad/infrastructure/rest/mappers/ErrorMapper.kt",
            "planificacion/infrastructure/rest/mappers/PlanErrorMapper.kt",
            "seguimiento/infrastructure/rest/mappers/ErrorMapper.kt",
            "identidad/infrastructure/rest/SessionController.kt",
            "shared/config/web/GlobalRestExceptionHandler.kt",
        ).map { Path.of("src/main/kotlin/com/runcriticon").resolve(it) }

    private val catalogo = Path.of("../frontend/src/app/core/api/error-codes.ts")

    private fun codigosEmitidos(): Set<String> =
        emisores
            .flatMap { fichero -> LITERAL.findAll(Files.readString(fichero)).map { it.groupValues[1] }.toList() }
            .toSet()

    /** Claves del catálogo; las compuestas `CODE:motivo` aportan su `CODE`. */
    private fun codigosCatalogados(): Set<String> =
        CLAVE
            .findAll(Files.readString(catalogo))
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun `hay códigos que comprobar`() {
        codigosEmitidos().shouldNotBeEmpty()
        codigosCatalogados().shouldNotBeEmpty()
    }

    @Test
    fun `todo código que emite el backend tiene mensaje en el catálogo del frontend`() {
        val sinMensaje = (codigosEmitidos() - codigosCatalogados()).sorted()

        sinMensaje.shouldBeEmpty()
    }

    private companion object {
        val LITERAL = Regex(""""([A-Z][A-Z0-9_]{2,})"""")
        val CLAVE = Regex("""^\s+'?([A-Z][A-Z0-9_]+)""", RegexOption.MULTILINE)
    }
}
