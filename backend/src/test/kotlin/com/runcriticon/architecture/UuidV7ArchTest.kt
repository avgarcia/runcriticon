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
