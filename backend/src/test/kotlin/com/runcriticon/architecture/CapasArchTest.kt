package com.runcriticon.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Reglas de arquitectura hexagonal (ADR-0008 D14, ADR-0010 D8).
 *
 * En H0 Bloque 2A todavía no hay módulos ni paquetes domain/application/infrastructure,
 * así que estas reglas pasan de forma vacía (allowEmptyShould). Demuestran que el stack
 * ArchUnit está cableado y empezarán a morder en cuanto se cree el primer módulo (Bloque 3).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class CapasArchTest {

    @ArchTest
    val `domain no depende de application ni infrastructure` =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..",
                "..infrastructure..",
            )
            .allowEmptyShould(true)

    @ArchTest
    val `domain no importa frameworks (Spring, JPA, Jackson, SDK AWS)` =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "com.fasterxml.jackson..",
                "software.amazon.awssdk..",
            )
            .allowEmptyShould(true)

    @ArchTest
    val `application no depende de infrastructure` =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)

    @ArchTest
    val `api no depende de domain (pasa por application)` =
        noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAPackage("..domain..")
            .allowEmptyShould(true)
}
