package com.runcriticon.architecture

import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.rgpd.AuditAccess
import com.runcriticon.shared.rgpd.RgpdCategory
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import jakarta.persistence.Entity
import org.springframework.transaction.annotation.Transactional

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
     * (panel de alertas del entrenador). Un `@AuditAccess` fuera de un `@ApplicationService` no dispararía el aspecto
     * igual, pero
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

    /**
     * `@AuditAccess` publica el evento de auditoría escribiendo en el outbox dentro de la misma
     * transacción del método anotado (ver el KDoc de
     * [AuditAccessAspect][com.runcriticon.shared.rgpd.AuditAccessAspect]). `readOnly = true` se propaga a la
     * conexión JDBC y PostgreSQL rechaza esa escritura sin lanzar ninguna excepción visible — el bug
     * detectado en `ListCoachAlertsQuery` antes de este guard: el aspecto se disparaba, calculaba los
     * sujetos correctos, y aun así no quedaba ningún rastro en `event_publication`.
     */
    @ArchTest
    val `ningun metodo @AuditAccess es @Transactional readOnly` =
        methods()
            .that()
            .areAnnotatedWith(AuditAccess::class.java)
            .should(noSerDeSoloLectura())
            .allowEmptyShould(true)

    private fun noSerDeSoloLectura(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("no ser @Transactional(readOnly = true)") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                val transactional = method.reflect().getAnnotation(Transactional::class.java) ?: return
                if (transactional.readOnly) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} es @AuditAccess + @Transactional(readOnly = true): el " +
                                "evento de auditoría nunca llegaría al outbox",
                        ),
                    )
                }
            }
        }
}
