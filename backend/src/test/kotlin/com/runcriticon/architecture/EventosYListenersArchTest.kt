package com.runcriticon.architecture

import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.modulith.events.ApplicationModuleListener

/**
 * Guards que exige `CLAUDE.md` para listeners y métricas por módulo y que no existían (LAL-175 P2-3, huecos
 * detectados por la auditoría de testing 2026-09 tras encontrar tres listeners de `identidad` fuera de sitio
 * y sin idempotencia, y `planificacion` sin ningún bean de métricas). El check de idempotencia/MDC es a nivel
 * de **clase**, no de método: las clases con varios métodos `@ApplicationModuleListener` (p. ej.
 * `GroupMembersProjectionListener`) delegan en un `private inline fun` compartido, que el compilador inlinea
 * en cada método — a nivel de bytecode el efecto es el mismo, pero verificarlo así es más simple y suficiente
 * para cazar el defecto real (una clase entera sin ninguna llamada).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class EventosYListenersArchTest {
    /** `application/listeners/` es donde CLAUDE.md documenta que viven los `@ApplicationModuleListener`. */
    @ArchTest
    val `todo metodo @ApplicationModuleListener esta declarado en application-listeners` =
        methods()
            .that()
            .areAnnotatedWith(ApplicationModuleListener::class.java)
            .should(residirEnPaqueteDeListeners())

    /** Sin esto, una reentrega del outbox reprocesa el evento (ADR-0007 D9): ver LAL-144. */
    @ArchTest
    val `todo metodo @ApplicationModuleListener verifica idempotencia con ProcessedEventTracker` =
        methods()
            .that()
            .areAnnotatedWith(ApplicationModuleListener::class.java)
            .should(llamaA(ProcessedEventTracker::class.java, "markIfNew"))

    /** Sin esto, los logs del listener pierden `trace_id`/`club_id` y rompen la correlación (ADR-0011 D4). */
    @ArchTest
    val `todo metodo @ApplicationModuleListener restaura y limpia el MDC` =
        methods()
            .that()
            .areAnnotatedWith(ApplicationModuleListener::class.java)
            .should(llamaA(MdcRestorerForEvents::class.java, "restore"))
            .andShould(llamaA(MdcRestorerForEvents::class.java, "clear"))

    /**
     * Cada módulo expone al menos un bean `{Modulo}Metrics`/`*Metrics` con `MeterRegistry` (CLAUDE.md,
     * "Métricas obligatorias"). `planificacion` no tenía ninguno hasta este PR.
     */
    @ArchTest
    val `cada modulo del backend tiene al menos un bean de metricas con MeterRegistry` =
        classes()
            .that()
            .resideInAPackage("com.runcriticon..")
            .should(tenerUnBeanDeMetricasPorModulo())

    /**
     * Los módulos que consumen la baja de un alumno/entrenador de `identidad` llevan su propio
     * `{Modulo}DeletionListener` (borrado mixto, ADR-0014 D6). `identidad` gestiona su propia baja de forma
     * síncrona (no por listener) y queda fuera; `auditoria` anonimiza en vez de borrar
     * (`AuditTrailAnonymizationListener`) y también queda fuera.
     */
    @ArchTest
    val `clubtaxonomia planificacion y seguimiento tienen su DeletionListener de borrado RGPD` =
        classes()
            .that()
            .resideInAPackage("com.runcriticon..")
            .should(tenerDeletionListenerPorModulo())

    private fun residirEnPaqueteDeListeners(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("residir en un paquete application.listeners") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                if (!method.owner.packageName.contains(".application.listeners")) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} no está en un paquete application.listeners",
                        ),
                    )
                }
            }
        }

    private fun llamaA(
        objetivo: Class<*>,
        metodo: String,
    ): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("llamar a ${objetivo.simpleName}.$metodo") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                val llama =
                    method.owner.accessesFromSelf.any {
                        it.targetOwner.isEquivalentTo(objetivo) && it.target.name == metodo
                    }
                if (!llama) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} no llama a ${objetivo.simpleName}.$metodo",
                        ),
                    )
                }
            }
        }

    private fun tenerUnBeanDeMetricasPorModulo(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("tener un bean *Metrics con MeterRegistry por módulo") {
            private val vistos = mutableSetOf<String>()
            private val checked = mutableSetOf<JavaClass>()

            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                checked += clazz
                val modulo = clazz.packageName.moduleOrNull() ?: return
                val esBeanDeMetricas =
                    clazz.simpleName.endsWith("Metrics") &&
                        clazz.directDependenciesFromSelf.any {
                            it.targetClass.isEquivalentTo(
                                MeterRegistry::class.java,
                            )
                        }
                if (esBeanDeMetricas) vistos += modulo
            }

            override fun finish(events: ConditionEvents) {
                MODULOS.forEach { modulo ->
                    if (modulo !in vistos) {
                        events.add(
                            SimpleConditionEvent.violated(
                                checked.firstOrNull { it.packageName.moduleOrNull() == modulo } ?: return@forEach,
                                "el módulo '$modulo' no tiene ningún bean *Metrics con MeterRegistry",
                            ),
                        )
                    }
                }
            }
        }

    private fun tenerDeletionListenerPorModulo(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("tener un {Modulo}DeletionListener") {
            private val vistos = mutableSetOf<String>()
            private val checked = mutableSetOf<JavaClass>()

            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                checked += clazz
                val modulo = clazz.packageName.moduleOrNull() ?: return
                if (modulo in MODULOS_CON_DELETION_LISTENER && clazz.simpleName.endsWith("DeletionListener")) {
                    vistos += modulo
                }
            }

            override fun finish(events: ConditionEvents) {
                MODULOS_CON_DELETION_LISTENER.forEach { modulo ->
                    if (modulo !in vistos) {
                        events.add(
                            SimpleConditionEvent.violated(
                                checked.firstOrNull { it.packageName.moduleOrNull() == modulo } ?: return@forEach,
                                "el módulo '$modulo' no tiene ningún *DeletionListener",
                            ),
                        )
                    }
                }
            }
        }

    private fun String.moduleOrNull(): String? =
        MODULOS.firstOrNull {
            startsWith("com.runcriticon.$it.") ||
                this == "com.runcriticon.$it"
        }

    private companion object {
        val MODULOS = setOf("identidad", "clubtaxonomia", "planificacion", "seguimiento", "auditoria")
        val MODULOS_CON_DELETION_LISTENER = setOf("clubtaxonomia", "planificacion", "seguimiento")
    }
}
