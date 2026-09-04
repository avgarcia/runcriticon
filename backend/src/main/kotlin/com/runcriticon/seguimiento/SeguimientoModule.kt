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
 * `"planificacion :: events"`, `"club_taxonomia :: events"` e `"identidad :: events"` explícitas: autorizar el
 * módulo entero no autoriza sus named interfaces — hay que nombrarlas una a una, mismo criterio que ya
 * documenta `PlanificacionModule`. `"club_taxonomia :: events"` llegó con LAL-116 (`CoachGroupProjectionListener`
 * consume `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo`) — antes solo hacía falta el módulo entero
 * para tipos no versionados. `"auditoria :: events"` llegó con el mismo ticket: `ListCoachAlertsQuery`
 * publica `AccesoADatosSensibles` (ver su KDoc) al ser el primer caso de uso de este módulo que expone datos
 * de un tercero.
 *
 * El acceso a datos de salud **de un tercero** se audita (`@AuditAccess`, `shared.rgpd.AuditAccessAspect`,
 * LAL-116); el alumno leyendo o reportando sus propios datos no se audita (`rgpd-en-modulos.md` §5, "lectura
 * del propio perfil del usuario"), por eso `SubmitSessionReportCommand` (LAL-30) no emite
 * `AccesoADatosSensibles`. El resto de la comunicación es por eventos de integración; sin llamadas síncronas
 * cruzadas.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    displayName = "Seguimiento",
    allowedDependencies = [
        "planificacion",
        "planificacion :: events",
        "club_taxonomia",
        "club_taxonomia :: events",
        "identidad",
        "identidad :: events",
        "auditoria :: events",
        "shared",
    ],
)
internal interface SeguimientoModule
