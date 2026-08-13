package com.runcriticon.planificacion

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **planificacion**: planes de entrenamiento y su asignación a grupos y alumnos. Puede referenciar
 * tipos expuestos de `club_taxonomia` (grupos) e `identidad` (usuarios).
 *
 * `club_taxonomia :: events` e `identidad :: events` van **además** de sus módulos: los integration events de cada
 * uno están en una named interface (`@NamedInterface("events")`), y autorizar el módulo entero no autoriza sus
 * named interfaces — hay que nombrarlas una a una (mismo motivo que documenta `ClubTaxonomiaModule` para
 * `identidad :: events`). `club_taxonomia :: events` es lo que consume `GroupMembersProjectionListener`
 * (`AlumnoAsignadoAGrupo`, `EntrenadorAsignadoAGrupo`, ...); `identidad :: events` es lo que consume
 * `PlanificacionDeletionListener` (`AlumnoEliminado`, `EntrenadorEliminado`) para aplicar el derecho de supresión.
 *
 * `shared` aparece en `allowedDependencies` pese a ser un módulo `OPEN`: al declarar allowlist, **todo** destino debe
 * estar listado; `OPEN` solo exime de la detección de ciclos y del bootstrap.
 *
 * El resto de la comunicación es por eventos de integración; sin llamadas síncronas cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Planificación",
    allowedDependencies = [
        "club_taxonomia",
        "club_taxonomia :: events",
        "identidad",
        "identidad :: events",
        "shared",
    ],
)
internal interface PlanificacionModule
