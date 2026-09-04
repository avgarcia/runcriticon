package com.runcriticon.architecture

import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.rgpd.AuditAccess
import com.runcriticon.shared.rgpd.RgpdCategory
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import jakarta.persistence.Entity

/**
 * Guard de clasificación RGPD (ADR-0013, ADR-0014, docs/arquitectura/rgpd-en-modulos.md §2, §5).
 *
 * Toda entidad persistente debe declarar su categoría de datos para que el patrón de borrado mixto
 * sepa cómo tratarla. En H0 no hay `@Entity`, así que la regla pasa de forma vacía
 * (`allowEmptyShould(true)`) y empezará a morder en cuanto se cree la primera entidad.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class RgpdArchTest {
    @ArchTest
    val `toda @Entity declara su @RgpdCategory` =
        classes()
            .that()
            .areAnnotatedWith(Entity::class.java)
            .should()
            .beAnnotatedWith(RgpdCategory::class.java)
            .allowEmptyShould(true)

    /**
     * [AuditAccessAspect][com.runcriticon.shared.rgpd.AuditAccessAspect] liga `args(actor,..)` a un primer
     * parámetro `Principal` — la misma firma que ya exige `AuthorizationArchTest` en todo `@ApplicationService`
     * (LAL-116). Un `@AuditAccess` fuera de un `@ApplicationService` no dispararía el aspecto igual, pero
     * quedaría ahí como documentación engañosa de que el acceso se audita.
     */
    @ArchTest
    val `todo metodo @AuditAccess esta declarado en un @ApplicationService` =
        methods()
            .that()
            .areAnnotatedWith(AuditAccess::class.java)
            .should()
            .beDeclaredInClassesThat()
            .areAnnotatedWith(ApplicationService::class.java)
            .allowEmptyShould(true)
}
