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
    allowedDependencies = ["identidad", "shared"],
)
internal interface ClubTaxonomiaModule
