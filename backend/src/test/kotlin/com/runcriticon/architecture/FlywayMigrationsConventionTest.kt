package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Guardarraíles de las migraciones Flyway que exige ADR-0004 D9 (nombre, versión única y rollback documentado). Sin
 * Docker: recorren los ficheros. El test de CI que migra desde cero no los cubre —aplica todo en orden sobre un
 * esquema vacío—, y una versión repetida entre carpetas o un nombre fuera de patrón solo aparece al arrancar en un
 * entorno ya migrado.
 *
 * La primera mitad aplica las reglas a las migraciones reales; la segunda prueba cada regla con entradas sintéticas,
 * para que un cambio que la desactive no pase desapercibido aunque hoy no haya ninguna violación que la ejercite.
 */
class FlywayMigrationsConventionTest :
    FunSpec({
        val migrations = migrationFiles()

        test("encuentra las migraciones de las seis carpetas, o ninguna de las reglas comprobaría nada") {
            migrations.size shouldBeGreaterThan 0
            migrations.map { it.folder }.toSet().shouldContainExactlyInAnyOrder(
                "_shared",
                "identidad",
                "club_taxonomia",
                "planificacion",
                "seguimiento",
                "auditoria",
            )
        }

        test("toda migración sigue la convención de nombres V{YYYYMMDD}{NNNN}__descripcion.sql") {
            val violaciones =
                migrations.mapNotNull { file -> nameViolation(file.name)?.let { "${file.path}: $it" } }

            violaciones.shouldBeEmpty()
        }

        test("ninguna versión se repite, tampoco entre carpetas distintas") {
            val repetidas =
                duplicateVersions(migrations).map { (version, paths) ->
                    "V$version la comparten ${paths.joinToString()}"
                }

            repetidas.shouldBeEmpty()
        }

        test("toda migración posterior al corte documenta su rollback con un comentario `-- Rollback:`") {
            val violaciones =
                migrations.mapNotNull { file -> rollbackViolation(file)?.let { "${file.path}: $it" } }

            violaciones.shouldBeEmpty()
        }

        context("nameViolation") {
            test("acepta un nombre conforme") {
                nameViolation("V202611030001__comenta_categoria_rgpd_event_publication.sql") shouldBe null
            }

            test("rechaza una V en minúscula") {
                nameViolation("v202611030001__crea_tabla.sql") shouldBe
                    "no sigue V{YYYYMMDD}{NNNN}__descripcion.sql (descripción en minúsculas, dígitos y '_')"
            }

            test("rechaza una secuencia de tres dígitos") {
                nameViolation("V20261103001__crea_tabla.sql").shouldNotBeNull()
            }

            test("rechaza una secuencia de cinco dígitos") {
                nameViolation("V2026110300001__crea_tabla.sql").shouldNotBeNull()
            }

            test("rechaza un solo guion bajo antes de la descripción") {
                nameViolation("V202611030001_crea_tabla.sql").shouldNotBeNull()
            }

            test("rechaza mayúsculas en la descripción") {
                nameViolation("V202611030001__CreaTabla.sql").shouldNotBeNull()
            }

            test("rechaza guiones y espacios en la descripción") {
                nameViolation("V202611030001__crea-tabla.sql").shouldNotBeNull()
                nameViolation("V202611030001__crea tabla.sql").shouldNotBeNull()
            }

            test("rechaza una extensión que no sea .sql") {
                nameViolation("V202611030001__crea_tabla.txt").shouldNotBeNull()
            }

            test("rechaza una fecha que no existe aunque tenga ocho dígitos") {
                nameViolation("V202613010001__crea_tabla.sql") shouldBe "la fecha 20261301 no existe"
                nameViolation("V202602300001__crea_tabla.sql") shouldBe "la fecha 20260230 no existe"
            }
        }

        context("duplicateVersions") {
            val a = MigrationFile("identidad", "V202611030002__crea_a.sql", "")
            val b = MigrationFile("planificacion", "V202611030002__crea_b.sql", "")
            val c = MigrationFile("seguimiento", "V202611030003__crea_c.sql", "")

            test("detecta la misma versión en carpetas distintas") {
                duplicateVersions(listOf(a, b, c)) shouldBe
                    mapOf(202_611_030_002L to listOf("identidad/${a.name}", "planificacion/${b.name}"))
            }

            test("detecta la misma versión en la misma carpeta") {
                val otra = MigrationFile("identidad", "V202611030002__crea_otra.sql", "")

                duplicateVersions(listOf(a, otra)).keys shouldBe setOf(202_611_030_002L)
            }

            test("no marca versiones distintas") {
                duplicateVersions(listOf(a, c)) shouldBe emptyMap()
            }
        }

        context("rollbackViolation") {
            fun migration(
                version: String,
                text: String,
            ) = MigrationFile("identidad", "V${version}__crea_tabla.sql", text)

            test("exige el comentario a una migración posterior al corte") {
                rollbackViolation(migration("202611030002", "CREATE TABLE x ();")) shouldBe
                    "no lleva una línea `-- Rollback: <texto>`"
            }

            test("acepta una migración posterior al corte que lo lleva") {
                val sql = "CREATE TABLE x ();\n-- Rollback: DROP TABLE x;\n"

                rollbackViolation(migration("202611030002", sql)) shouldBe null
            }

            test("acepta el texto «no reversible», que D9 admite para las migraciones que destruyen información") {
                val sql = "ALTER TABLE x DROP COLUMN y;\n-- Rollback: no reversible; restaurar desde backup.\n"

                rollbackViolation(migration("202611040001", sql)) shouldBe null
            }

            test("acepta el comentario con sangría") {
                rollbackViolation(migration("202611030002", "SELECT 1;\n    --   Rollback: nada que revertir")) shouldBe
                    null
            }

            test("rechaza un comentario vacío") {
                rollbackViolation(migration("202611030002", "SELECT 1;\n-- Rollback:\n")) shouldBe
                    "no lleva una línea `-- Rollback: <texto>`"
                rollbackViolation(migration("202611030002", "SELECT 1;\n-- Rollback:   \n")).shouldNotBeNull()
            }

            test("rechaza la palabra «Rollback» dentro de una cadena o de un comentario que no la declara") {
                val comentario = "SELECT 'Rollback: no cuenta';\n-- el Rollback: va aparte\n"

                rollbackViolation(migration("202611030002", comentario)).shouldNotBeNull()
            }

            test("exime a la migración del corte y a las anteriores, que ya están aplicadas") {
                rollbackViolation(migration("202611030001", "CREATE TABLE x ();")) shouldBe null
                rollbackViolation(migration("202606020001", "CREATE TABLE x ();")) shouldBe null
            }

            test("el corte es un parámetro: con uno anterior, la misma migración falla") {
                rollbackViolation(migration("202611030001", "SELECT 1;"), requiredAfter = 202_611_030_000L)
                    .shouldNotBeNull()
            }
        }
    })
