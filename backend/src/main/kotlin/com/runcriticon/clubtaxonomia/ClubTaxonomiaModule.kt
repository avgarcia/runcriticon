package com.runcriticon.clubtaxonomia

import org.springframework.modulith.ApplicationModule

/**
 * Bounded context **club y taxonomía**: el club, sus grupos de entrenamiento y las membresías. Puede referenciar tipos
 * expuestos de `identidad` (p. ej. el usuario que pertenece a un grupo).
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
    allowedDependencies = ["identidad"],
)
internal interface ClubTaxonomiaModule
