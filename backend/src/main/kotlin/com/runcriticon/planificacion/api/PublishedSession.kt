package com.runcriticon.planificacion.api

import org.springframework.modulith.NamedInterface
import java.time.LocalDate

/**
 * Una sesión del plan en el momento de publicar, embebida en `PlanPublicado` (LAL-25). Mismos campos que
 * `TrainingSessionResponse` del contrato REST (LAL-24) — no se reutiliza esa clase porque vive en el módulo
 * generado del contrato, no aquí.
 *
 * Vive en `api`, no en `api.events`: no es un `IntegrationEvent` en sí mismo (solo un fragmento de payload),
 * y `DomainEventArchTest`/`IntegrationEventArchTest` exigen que **todo** lo que resida en `..api.events..`
 * implemente esa interfaz.
 *
 * Lleva `@NamedInterface("events")` igual que `PlanPublicado`: es parte del payload público del evento, y sin
 * ella un consumidor de otro módulo (Seguimiento, LAL-29) que lea `PlanPublicado.sesiones` estaría accediendo a
 * un tipo interno del módulo — `ApplicationModules.verify()` lo rechaza aunque el propio evento sea público.
 */
@NamedInterface("events")
data class PublishedSession(
    val dia: LocalDate,
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
