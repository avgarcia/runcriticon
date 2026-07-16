package com.runcriticon.auditoria

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **auditoria**: registro inmutable de eventos de auditoría y accesos sensibles. Consume eventos de
 * integración de forma polimórfica a través del contrato `IntegrationEvent` del núcleo compartido, por lo que no
 * necesita depender de ningún otro módulo de negocio.
 *
 * Sin llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(displayName = "Auditoría")
internal interface AuditoriaModule
