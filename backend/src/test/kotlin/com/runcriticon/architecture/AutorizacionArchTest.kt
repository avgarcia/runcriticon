package com.runcriticon.architecture

import com.runcriticon.shared.autorizacion.anotaciones.AuthScope
import com.runcriticon.shared.autorizacion.anotaciones.NoAuthScope
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.stereotype.Repository

/**
 * Guards de la malla de autorización (ADR-0009 D6, D10, D11).
 *
 * En H0 no hay repositorios ni acceso a sesión, así que estas reglas pasan de forma vacía
 * (`allowEmptyShould(true)`): demuestran que el guard está cableado y empezarán a morder en cuanto
 * aparezca el primer `@Repository` o cualquier acceso a `SecurityContextHolder`/`HttpSession`.
 *
 * La regla "todo `@ApplicationService` invoca autorización" requiere una `ArchCondition` propia y el
 * `AutorizacionService` real; se difiere a Fase 1 (cf. docs/arquitectura/testing-de-modulos.md §6).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class AutorizacionArchTest {
    @ArchTest
    val `cada metodo publico de un @Repository declara @AuthScope o @NoAuthScope` =
        methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Repository::class.java)
            .and()
            .arePublic()
            .should()
            .beAnnotatedWith(AuthScope::class.java)
            .orShould()
            .beAnnotatedWith(NoAuthScope::class.java)
            .allowEmptyShould(true)

    @ArchTest
    val `no se accede a SecurityContextHolder fuera del nucleo de autorizacion` =
        noClasses()
            .that()
            .resideOutsideOfPackage("..shared.autorizacion..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("SecurityContextHolder")
            .allowEmptyShould(true)

    @ArchTest
    val `nadie usa HttpSession directa (la app es stateless)` =
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveSimpleName("HttpSession")
            .allowEmptyShould(true)
}
