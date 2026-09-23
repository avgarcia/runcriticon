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
 * documenta `PlanificacionModule`. `"club_taxonomia :: events"` llegó con el panel de alertas del entrenador
 * (`CoachGroupProjectionListener` consume `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo`) — antes
 * solo hacía falta el módulo entero para tipos no versionados. Sin entrada propia para
 * `AccesoADatosSensibles`: lo publica `shared.rgpd.AuditAccessAspect` (ver su KDoc) sobre
 * `ListCoachAlertsQuery`, no este módulo directamente, y `shared.api.events` ya cae bajo la entrada `shared`
 * de esta lista.
 *
 * El acceso a datos de salud **de un tercero** se audita (`@AuditAccess`, `shared.rgpd.AuditAccessAspect`,
 * desde el panel de alertas del entrenador); el alumno leyendo o reportando sus propios datos no se audita
 * (`rgpd-en-modulos.md` §5, "lectura del propio perfil del usuario"), por eso `SubmitSessionReportCommand`
 * (el reporte de sesión) no emite `AccesoADatosSensibles`. El resto de la comunicación es por eventos de
 * integración; sin llamadas síncronas cruzadas.
 *
 * `"shared :: events"` (llegó con el aviso de lesión desde el reajuste de día) se suma junto a la entrada
 * plana `shared`: `RescheduleDayCommand` importa y
 * publica `LesionDeclarada` directamente (a diferencia de `AccesoADatosSensibles`, que un aspecto de `shared`
 * publica sin que este módulo lo importe) — vive en `shared.api.events` porque `club_taxonomia`, su
 * consumidor, está aguas arriba de `seguimiento` en el orden de dependencia habitual; ver el KDoc del propio
 * evento.
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
        "shared",
        "shared :: events",
    ],
)
internal interface SeguimientoModule
