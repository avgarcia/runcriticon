package com.runcriticon.architecture

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty

/**
 * Guard real de capas (ADR-0008 D3), complementario a la regla de bytecode de `CapasArchTest`: ningún
 * fichero de `application` importa una clase de `infrastructure` (H-4/H-5 de la auditoría de
 * arquitectura — un `const val`/literal `@Qualifier` referenciando la constante de un adaptador concreto
 * se inlinea y queda invisible para ArchUnit).
 */
class LayerImportHygieneTest :
    FunSpec({
        test("ningun fichero de application importa una clase de infrastructure") {
            val violaciones =
                kotlinSourceFiles()
                    .mapNotNull { file -> file.packageDeclaration()?.let { file to it } }
                    .flatMap { (file, paquete) ->
                        importsDeInfraestructuraEnApplication(paquete, file.readText()).map { import ->
                            "${file.path}: importa '$import' desde application (ADR-0008 D3)"
                        }
                    }

            violaciones.shouldBeEmpty()
        }
    })
