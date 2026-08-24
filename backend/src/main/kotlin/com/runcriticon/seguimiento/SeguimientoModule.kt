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
 * `"planificacion :: events"` e `"identidad :: events"` explícitas (LAL-29): autorizar el módulo entero no
 * autoriza sus named interfaces — hay que nombrarlas una a una, mismo criterio que ya documenta
 * `PlanificacionModule`. `"auditoria :: events"` no hace falta todavía: el primer query de este módulo no emite
 * `AccesoDenegado` (mismo precedente que `ListStudentsQuery`); llegará con el barrido de LAL-120.
 *
 * Todo acceso a datos de salud se audita. El resto de la comunicación es por eventos de integración; sin llamadas
 * síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Seguimiento",
    allowedDependencies = [
        "planificacion",
        "planificacion :: events",
        "club_taxonomia",
        "identidad",
        "identidad :: events",
        "shared",
    ],
)
internal interface SeguimientoModule
