package com.runcriticon.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.util.UUID

/**
 * Guard de UUID v7 (ADR-0004 D8): prohíbe `UUID.randomUUID()` (v4) en código de producción. Los
 * typed IDs y los `eventId` de integration events se generan con `UuidCreator.getTimeOrderedEpoch()`
 * (v7, ordenable por tiempo — mejor localidad de índice B-tree que v4 aleatorio).
 *
 * **No cubre ADR-0008 D11** (que ningún parámetro de método en `…domain.*` sea `UUID`/`String` raw en
 * vez de un typed ID) — pese a la coincidencia de nombre, es una regla distinta. D11 no tiene guard
 * todavía: un `@JvmInline value class` se borra a `UUID` puro en bytecode, así que distinguir ambos
 * casos exige metadatos de Kotlin, no solo lo que ve ArchUnit.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class UuidV7ArchTest {
    @ArchTest
    val `ningun codigo de produccion usa UUID randomUUID` =
        noClasses()
            .should()
            .callMethod(UUID::class.java, "randomUUID")
            .because("los IDs son UUID v7 (UuidCreator.getTimeOrderedEpoch()), nunca v4 aleatorio (ADR-0004 D8)")
}
