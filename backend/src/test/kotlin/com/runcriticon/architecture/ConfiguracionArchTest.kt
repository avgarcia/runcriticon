package com.runcriticon.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Configuración leída sin SDK de AWS (ADR-0013): la app lee env vars vía `Environment` de Spring
 * — App Runner ya las resuelve desde SSM/Secrets Manager en el arranque del contenedor, así que
 * ningún módulo necesita el SDK para leer su propia configuración. `CapasArchTest` ya prohíbe el SDK
 * de AWS en `domain`; esta regla cubre el resto de `com.runcriticon..` (application/infrastructure),
 * con la única excepción reservada para un futuro `shared.aws` si algún día hiciera falta un cliente
 * AWS real (hoy no existe ningún caso de uso que lo necesite).
 *
 * Cubre todo `software.amazon.awssdk..` (LAL-175 P2-3), no solo SSM/Secrets Manager: la regla original
 * dejaba pasar cualquier otro servicio del SDK (S3, SES, …) sin que ADR-0013 distinga entre ellos.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ConfiguracionArchTest {
    @ArchTest
    val `ningun modulo importa el SDK de AWS para leer configuracion` =
        noClasses()
            .that()
            .resideInAPackage("com.runcriticon..")
            .and()
            .resideOutsideOfPackage("com.runcriticon.shared.aws..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "software.amazon.awssdk..",
            ).allowEmptyShould(true)
}
