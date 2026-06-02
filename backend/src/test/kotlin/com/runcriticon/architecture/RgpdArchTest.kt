package com.runcriticon.architecture

import com.runcriticon.shared.rgpd.CategoriaRGPD
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import jakarta.persistence.Entity

/**
 * Guard de clasificación RGPD (ADR-0013, ADR-0014, docs/arquitectura/rgpd-en-modulos.md §2).
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
    val `toda @Entity declara su @CategoriaRGPD` =
        classes()
            .that().areAnnotatedWith(Entity::class.java)
            .should().beAnnotatedWith(CategoriaRGPD::class.java)
            .allowEmptyShould(true)
}
