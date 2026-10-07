package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Guard real de la superficie SQL del backend (ADR-0004 D4): el código usa `JdbcTemplate` con SQL en
 * cadenas de Kotlin, no `@Query(nativeQuery = true)` (que [SchemaFronterasArchTest] cubre y que hoy
 * selecciona cero clases). Escanea el código fuente en vez de bytecode porque un `const val`/`val` de
 * tipo `String` no deja rastro verificable en el grafo de ArchUnit más allá de su valor literal.
 */
class JdbcTemplateCrossSchemaTest :
    FunSpec({
        test("ningun uso de JdbcTemplate referencia el schema de otro modulo en su SQL") {
            val violaciones =
                kotlinSourceFiles()
                    .filter { it.readText().contains("JdbcTemplate") }
                    .flatMap { file ->
                        val ownSchema = file.packageDeclaration()?.schemaOfPackageOrNull() ?: return@flatMap emptyList()
                        crossSchemaReferences(ownSchema, file.readText()).map { ajeno ->
                            "${file.path}: referencia el schema '$ajeno' desde un fichero de '$ownSchema' " +
                                "(ADR-0004 D4 — ninguna FK ni consulta cruza esquemas)"
                        }
                    }

            violaciones.shouldBeEmpty()
        }
    })
