package com.runcriticon.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * Guard de idioma de los identificadores (ADR-0008 D4, CLAUDE.md §Lenguaje ubicuo).
 *
 * La regla del proyecto es: **los identificadores de código van en inglés**; en castellano solo se
 * quedan, por diseño, los paquetes raíz de bounded context (`identidad`, `planificacion`, …), los
 * identificadores SQL y los valores de enum persistidos, y los textos de UI. Este guard impide que
 * reaparezca la mezcla castellano/inglés que venía causando retrabajo de nomenclatura.
 *
 * **Es un backstop por denylist, no una prueba exhaustiva de "todo en inglés"**: corta la
 * reincidencia de los tokens técnicos castellanos concretos que ya causaron el problema. No persigue
 * el vocabulario de dominio (algún nombre de clase del dominio sigue en castellano por mandato de la
 * arquitectura, p. ej. `PlanSemanal`, `Ritmo`). Los paquetes raíz de bounded context y el núcleo
 * `shared.autorizacion` quedan deliberadamente fuera de la lista.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class NamingConventionArchTest {
    @ArchTest
    val `ningun nombre de clase usa tokens tecnicos en castellano` =
        classes()
            .should()
            .haveSimpleNameNotContaining("Usuario")
            .andShould()
            .haveSimpleNameNotContaining("Sesion")
            .andShould()
            .haveSimpleNameNotContaining("Credencial")
            .andShould()
            .haveSimpleNameNotContaining("Modulo")
            .andShould()
            .haveSimpleNameNotContaining("Autorizacion")
            .andShould()
            .haveSimpleNameNotContaining("Matriz")
            .andShould()
            .haveSimpleNameNotContaining("Metricas") // LAL-53: {Modulo}Metricas -> {Modulo}Metrics
            .allowEmptyShould(true)

    @ArchTest
    val `ningun sub-paquete tecnico va en castellano` =
        noClasses()
            .should()
            .resideInAPackage("..persistencia..")
            .orShould()
            .resideInAPackage("..seguridad..")
            .orShould()
            .resideInAPackage("..errores..")
            .orShould()
            .resideInAPackage("..anotaciones..")
            .orShould()
            .resideInAPackage("..modelo..")
            .orShould()
            .resideInAPackage("..eventos..")
            .orShould()
            .resideInAPackage("..observabilidad..")
            .orShould()
            .resideInAPackage("..usuario..")
            .allowEmptyShould(true)
}
