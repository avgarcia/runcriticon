package com.runcriticon.clubtaxonomia

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **club y taxonomía**: sus grupos de entrenamiento y las membresías. El club en sí (ficha editable)
 * vive en `identidad`: que este módulo fuera el dueño del club habría creado una dependencia inversa que
 * `ApplicationModules.verify()` rechaza. Puede referenciar tipos expuestos de `identidad` (p. ej. el usuario que
 * pertenece a un grupo, o el propio club).
 *
 * `allowedDependencies` lista `identidad` y `shared`: aunque `shared` sea un módulo `OPEN`, cuando un módulo declara
 * `allowedDependencies` **todo** destino debe aparecer en la lista — `OPEN`/`sharedModules` solo eximen de la
 * detección de ciclos y del bootstrap, no del allowlist (Spring Modulith 2.x). El dominio usa `shared.tenancy.ClubId`.
 *
 * `identidad :: events` va **además** de `identidad`: los integration events de ese módulo están en una named interface
 * (`@NamedInterface("events")`), y autorizar el módulo entero no autoriza sus named interfaces — hay que nombrarlas una
 * a una. Es lo que consumen los listeners de la proyección local de personas.
 *
 * `auditoria :: events` (sin el módulo entero) es lo que importa `ClubTaxonomiaAccessAuditor` para publicar
 * `AccesoDenegado` (ADR-0009 D15-D17, LAL-120) — mismo motivo que documenta `PlanificacionModule` para su propio
 * caso: `AccesoDenegado` vive en `auditoria.api.events` porque `IntegrationEventArchTest` exige un único paquete
 * `api.events` por tipo de evento y `auditoria` es su único consumidor estable. Dependencia deliberada
 * `club_taxonomia → auditoria`, no un ciclo: `auditoria` no depende de `club_taxonomia`.
 *
 * El resto de la comunicación es por eventos de integración; sin llamadas síncronas cruzadas.
 *
 * El paquete es `clubtaxonomia` (sin guion bajo, requisito de ktlint/detekt para nombres de paquete), pero el
 * identificador lógico del módulo se fija a `club_taxonomia` vía `id` para que coincida con el esquema PostgreSQL y con
 * las `allowedDependencies` del resto de módulos.
 *
 * Descriptor de módulo Spring Modulith (sustituye al antiguo `package-info.java`).
 */
@ApplicationModule(
    id = "club_taxonomia",
    displayName = "Club y taxonomía",
    allowedDependencies = ["auditoria :: events", "identidad", "identidad :: events", "shared"],
)
internal interface ClubTaxonomiaModule
