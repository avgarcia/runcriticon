package com.runcriticon.seguimiento

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **seguimiento**: seguimiento del alumno —reportes de sesión y datos de salud
 * (categoría especial RGPD art. 9)— ligados a su planificación. Puede referenciar tipos expuestos de `planificacion`,
 * `club_taxonomia` e `identidad`.
 *
 * `shared` aparece en `allowedDependencies` pese a ser un módulo `OPEN`: al declarar allowlist, **todo** destino debe
 * estar listado; `OPEN` solo exime de la detección de ciclos y del bootstrap.
 *
 * Todo acceso a datos de salud se audita. El resto de la comunicación es por eventos de integración; sin llamadas
 * síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Seguimiento",
    allowedDependencies = ["planificacion", "club_taxonomia", "identidad", "shared"],
)
internal interface SeguimientoModule
