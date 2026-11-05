package com.runcriticon.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Reglas de arquitectura hexagonal (ADR-0008 D14, ADR-0010 D8).
 *
 * Las reglas seleccionan clases reales de los cinco módulos (domain, application, infrastructure y api). Si una de
 * ellas dejara de seleccionar nada, `allowEmptyShould` la haría pasar en vacío: ante un cambio de paquetes
 * conviene comprobar que siguen mordiendo.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class CapasArchTest {
    @ArchTest
    val `domain no depende de application ni infrastructure` =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..application..",
                "..infrastructure..",
            ).allowEmptyShould(true)

    @ArchTest
    val `domain no importa frameworks (Spring, JPA, Jackson, SDK AWS)` =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.validation..",
                "com.fasterxml.jackson..",
                "tools.jackson..",
                "software.amazon.awssdk..",
                "org.hibernate..",
                "org.slf4j..",
                "io.micrometer..",
            ).allowEmptyShould(true)

    @ArchTest
    val `application no depende de infrastructure` =
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)

    @ArchTest
    val `api no depende de domain (pasa por application)` =
        noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..")
            .allowEmptyShould(true)
}
