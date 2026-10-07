package com.runcriticon.architecture

import com.runcriticon.shared.events.IntegrationEvent
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.modulith.NamedInterface
import java.io.File

/**
 * Guard de la frontera de integration events (ADR-0007 D12): la distinción entre eventos internos
 * de dominio (futuros, hoy inexistentes) y eventos públicos de integración se hace por convención
 * de paquete + `@NamedInterface`, verificada aquí — no por tipos `sealed`, que Kotlin exige en el
 * mismo paquete que la interfaz sellada y por tanto son incompatibles con eventos que viven en el
 * paquete `api.events` de cada módulo (comprobado empíricamente al escribir este test: un
 * `sealed interface IntegrationEvent` en `shared.events` no puede ser implementado desde
 * `identidad.api.events`, error real del compilador).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class IntegrationEventArchTest {
    @ArchTest
    val `todo IntegrationEvent vive en el paquete api-events de su modulo` =
        classes()
            .that()
            .implement(IntegrationEvent::class.java)
            .should()
            .resideInAPackage("..api.events..")

    @ArchTest
    val `todo IntegrationEvent esta marcado como NamedInterface` =
        classes()
            .that()
            .implement(IntegrationEvent::class.java)
            .should()
            .beAnnotatedWith(NamedInterface::class.java)

    /**
     * Sin esto, un evento nuevo sin su JSON Schema no rompe nada en CI — hueco real detectado en la
     * auditoría de arquitectura (los 22 eventos existentes sí tienen el suyo, verificado contra
     * `schemas/`). El emparejamiento es por palabras significativas, no por derivación mecánica exacta:
     * ver [eventNameMatchesSchemaFile].
     */
    @ArchTest
    val `todo IntegrationEvent tiene su JSON Schema en schemas-` =
        classes()
            .that()
            .implement(IntegrationEvent::class.java)
            .should(tenerSuJsonSchema())

    private fun tenerSuJsonSchema(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("tener su JSON Schema en schemas/") {
            override fun check(
                event: JavaClass,
                events: ConditionEvents,
            ) {
                val dir = event.packageName.schemaDirectoryOrNull()
                if (dir == null) {
                    events.add(
                        SimpleConditionEvent.violated(
                            event,
                            "${event.name} vive en un paquete sin módulo/schema reconocido " +
                                "(${event.packageName}) — no se puede comprobar su JSON Schema.",
                        ),
                    )
                    return
                }
                val schemaDir = File("../schemas/$dir")
                val tieneSchema =
                    schemaDir
                        .listFiles { f -> f.extension == "json" }
                        ?.any { eventNameMatchesSchemaFile(event.simpleName, it.name) }
                        ?: false
                if (!tieneSchema) {
                    events.add(
                        SimpleConditionEvent.violated(
                            event,
                            "${event.name} no tiene JSON Schema en schemas/$dir/ (ADR-0007 D11).",
                        ),
                    )
                }
            }
        }

    private fun String.schemaDirectoryOrNull(): String? =
        if (startsWith("com.runcriticon.shared.api.events")) "shared" else schemaOfPackageOrNull()
}
