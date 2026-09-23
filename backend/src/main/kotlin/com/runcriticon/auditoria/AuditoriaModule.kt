package com.runcriticon.auditoria

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **auditoria**: registro inmutable de eventos de auditoría y accesos sensibles. Consume
 * `AccesoDenegado`/`AccesoADatosSensibles` (ADR-0009 D15-D17) desde `shared.api.events` — viven en `shared` y no
 * aquí ni en el módulo que los publica: cualquier módulo de negocio puede producirlos y `auditoria` es su único
 * consumidor, así que ninguno de los dos extremos puede ser su dueño sin imponerle al otro una dependencia
 * (`shared` es módulo `OPEN`, exento de la detección de ciclos de `ModulithFronterasTest`; ver el KDoc de
 * `AccesoDenegado` para el ciclo real que forzó moverlo desde `auditoria.api.events`). También consume,
 * para el derecho al olvido, `AlumnoEliminado`/`EntrenadorEliminado` de `identidad.api.events` — misma
 * dependencia pública que ya usa `club_taxonomia.StudentDeletionListener`.
 *
 * Sin llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(displayName = "Auditoría")
internal interface AuditoriaModule
