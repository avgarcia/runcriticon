package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/**
 * Sin esto, un JSON Schema nuevo sin test `@Tag("contract")` no rompe nada en CI — segundo hueco real
 * del área de eventos detectado en la auditoría de arquitectura (los 22 schemas existentes sí tienen su
 * test, verificado contra los paquetes `contracts` de cada módulo bajo `backend/src/test/kotlin`).
 * Escanea el sistema de ficheros, no bytecode: un fichero JSON no es una clase que ArchUnit pueda analizar.
 */
class EventSchemaHasContractTestTest :
    FunSpec({
        test("todo JSON Schema de evento tiene su test con @Tag(\"contract\")") {
            val testPackagePorSchemaDir =
                mapOf(
                    "identidad" to "identidad",
                    "club_taxonomia" to "clubtaxonomia",
                    "planificacion" to "planificacion",
                    "seguimiento" to "seguimiento",
                    "auditoria" to "auditoria",
                    "shared" to "shared",
                )

            val violaciones =
                File("../schemas")
                    .listFiles { f -> f.isDirectory }
                    .orEmpty()
                    .flatMap { schemaDir ->
                        val testPackage = testPackagePorSchemaDir[schemaDir.name] ?: return@flatMap emptyList()
                        val contractTests = contractTestFiles(testPackage)
                        schemaDir
                            .listFiles { f -> f.extension == "json" }
                            .orEmpty()
                            .filterNot { schema ->
                                contractTests.any { test ->
                                    eventNameMatchesSchemaFile(
                                        test.nameWithoutExtension.removeSuffix("ContractTest"),
                                        schema.name,
                                    ) &&
                                        test.readText().contains("""@Tag("contract")""")
                                }
                            }.map { schema ->
                                "schemas/${schemaDir.name}/${schema.name} no tiene test @Tag(\"contract\") en " +
                                    "com.runcriticon.$testPackage.contracts (ADR-0007 D11)"
                            }
                    }

            violaciones.shouldBeEmpty()
        }
    })

private fun contractTestFiles(testPackage: String): List<File> =
    File("src/test/kotlin/com/runcriticon/$testPackage/contracts")
        .listFiles { f -> f.isFile && f.name.endsWith("ContractTest.kt") && !f.name.endsWith("OpenApiContractTest.kt") }
        .orEmpty()
        .toList()
