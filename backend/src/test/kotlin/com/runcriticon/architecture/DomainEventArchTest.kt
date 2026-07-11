package com.runcriticon.architecture

import com.runcriticon.shared.events.IntegrationEvent
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes

/**
 * Guard de la separación entre domain event interno e integration event público (ADR-0008 D2/D3/D4):
 * un domain event (`…domain.events..`) NO es un contrato externo versionado, así que nunca puede
 * implementar [IntegrationEvent] — la vista pública correspondiente es una clase aparte en
 * `…api.events..`, que sí lo implementa. Verificado hoy contra `UserInvited`/`UserActivated`
 * (domain) y `AlumnoInvitado`/`AlumnoActivado`/`EntrenadorActivado` (api).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class DomainEventArchTest {
    @ArchTest
    val `las clases en domain-events no implementan IntegrationEvent` =
        classes()
            .that()
            .resideInAPackage("..domain.events..")
            .should()
            .notImplement(IntegrationEvent::class.java)
            .allowEmptyShould(true)

    @ArchTest
    val `las clases en api-events implementan IntegrationEvent` =
        classes()
            .that()
            .resideInAPackage("..api.events..")
            .should()
            .implement(IntegrationEvent::class.java)
            .allowEmptyShould(true)
}
