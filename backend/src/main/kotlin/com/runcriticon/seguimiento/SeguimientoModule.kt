package com.runcriticon.seguimiento

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **seguimiento**: seguimiento del alumno —reportes de sesión y datos de salud
 * (categoría especial RGPD art. 9)— ligados a su planificación (ADR-0003, ADR-0013, ADR-0014).
 * Puede referenciar tipos expuestos de `planificacion`, `club_taxonomia` e `identidad`.
 *
 * Todo acceso a datos de salud se audita (ADR-0013). El resto de la comunicación es por eventos de
 * integración (ADR-0005, ADR-0011); sin llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Seguimiento",
    allowedDependencies = ["planificacion", "club_taxonomia", "identidad"],
)
internal interface SeguimientoModule
