package com.runcriticon.auditoria

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **auditoria**: registro inmutable de eventos de auditoría y accesos sensibles. Consume sus
 * propios `AccesoDenegado`/`AccesoADatosSensibles` (ADR-0009 D15-D17, `auditoria.api.events` — viven aquí y no
 * en el módulo que los publica, porque `IntegrationEventArchTest` exige un único paquete `api.events` por tipo
 * de evento y este es el único consumidor estable; cada módulo productor los importa) y, para el derecho al
 * olvido, `AlumnoEliminado`/`EntrenadorEliminado` de `identidad.api.events` — misma dependencia pública que ya
 * usa `club_taxonomia.StudentDeletionListener`.
 *
 * Sin llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(displayName = "Auditoría")
internal interface AuditoriaModule
