package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

/**
 * Prueba que las funciones puras de [SchemaScanning] detectan la violación real que motivó H-2 y H-3 de la
 * auditoría de arquitectura, y que no disparan en falso con los dos patrones que sí aparecen en el repo: una
 * mención en KDoc y una consulta dentro del propio schema.
 */
class SchemaScanningTest :
    FunSpec({
        test("clubtaxonomia resuelve a club_taxonomia, no al mismo string") {
            "com.runcriticon.clubtaxonomia.infrastructure.scheduling".schemaOfPackageOrNull() shouldBe "club_taxonomia"
        }

        test("un paquete fuera de los cinco modulos no resuelve a ningun schema") {
            "com.runcriticon.shared.events".schemaOfPackageOrNull() shouldBe null
        }

        test("una consulta DELETE en el propio schema no es violacion") {
            val fuente =
                """
                val sql = "DELETE FROM club_taxonomia.evento_auditoria WHERE ts < now()"
                """.trimIndent()
            crossSchemaReferences(ownSchema = "club_taxonomia", sourceText = fuente).shouldBeEmpty()
        }

        test("una mencion en KDoc al schema de otro modulo no es violacion") {
            val fuente =
                """
                /** Purga identidad.evento_auditoria a los 12 meses, mismo criterio que club_taxonomia. */
                class Foo
                """.trimIndent()
            crossSchemaReferences(ownSchema = "club_taxonomia", sourceText = fuente).shouldBeEmpty()
        }

        test("un SELECT literal que referencia el schema de otro modulo es violacion") {
            val fuente =
                """
                val sql = "SELECT id FROM identidad.usuario WHERE club_id = ?"
                """.trimIndent()
            crossSchemaReferences(ownSchema = "club_taxonomia", sourceText = fuente) shouldBe setOf("identidad")
        }

        test("un DELETE triple-quoted que referencia el schema de otro modulo es violacion") {
            val fuente =
                """
                val sql =
                    ${"\"\"\""}
                    DELETE FROM identidad.evento_auditoria WHERE ts < now() - INTERVAL '12 months'
                    ${"\"\"\""}
                """.trimIndent()
            crossSchemaReferences(ownSchema = "club_taxonomia", sourceText = fuente) shouldBe setOf("identidad")
        }
    })
