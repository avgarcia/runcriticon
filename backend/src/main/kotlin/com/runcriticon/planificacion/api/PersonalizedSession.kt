package com.runcriticon.planificacion.api

import org.springframework.modulith.NamedInterface

/**
 * El [com.runcriticon.planificacion.domain.SessionOverride] de una personalización, embebido en
 * `PersonalizacionAplicada`/`PersonalizacionRetirada` y en `PlanPublicado.personalizaciones` (LAL-26). Mismos
 * campos que [PublishedSession] sin `dia`: el override no tiene fecha propia, la fija la sesión que sobrescribe.
 *
 * Vive en `api`, no en `api.events`, mismo motivo que [PublishedSession]: no es un `IntegrationEvent` en sí
 * mismo, solo un fragmento de payload — `IntegrationEventArchTest` exige que todo lo que resida en
 * `..api.events..` implemente esa interfaz. Lleva `@NamedInterface("events")` por el mismo motivo que
 * [PublishedSession]: sin ella, un consumidor de otro módulo que lea este tipo estaría accediendo a un tipo
 * interno — `ApplicationModules.verify()` lo rechaza aunque el evento contenedor sea público.
 */
@NamedInterface("events")
data class PersonalizedSession(
    val tipo: String,
    val volumenTipo: String?,
    val volumenMetros: Int?,
    val volumenMinutos: Int?,
    val ritmoTipo: String?,
    val ritmoSegundosPorKm: Int?,
    val ritmoReferencia: String?,
    val ritmoDeltaSegundosPorKm: Int?,
    val notas: String?,
)
