package com.runcriticon.architecture

import com.runcriticon.shared.events.IntegrationEvent
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.springframework.modulith.NamedInterface

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
}
