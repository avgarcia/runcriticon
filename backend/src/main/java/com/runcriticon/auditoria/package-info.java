/**
 * Bounded context <strong>auditoria</strong>: registro inmutable de eventos de auditoría y accesos
 * sensibles (ADR-0013, ADR-0014). Consume eventos de integración de forma polimórfica a través del
 * contrato {@code IntegrationEvent} del núcleo compartido, por lo que no necesita depender de
 * ningún otro módulo de negocio.
 *
 * <p>Sin llamadas síncronas cruzadas (ADR-0005, ADR-0011).
 */
@ApplicationModule(displayName = "Auditoría")
package com.runcriticon.auditoria;

import org.springframework.modulith.ApplicationModule;
