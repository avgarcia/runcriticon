package com.runcriticon.planificacion.api

import org.springframework.modulith.NamedInterface
import java.time.LocalDate
import java.util.UUID

/**
 * Una personalización vigente en el momento de publicar, embebida en `PlanPublicado.personalizaciones`
 * (LAL-26). Sin esto, una personalización creada **antes** de publicar (AC2 — no emite evento propio porque
 * todavía no hay snapshot) se perdería: `ResolvedPlanProjectionListener` escribe el producto cartesiano
 * alumno×sesión desde cero al procesar `PlanPublicado`, así que tiene que conocerlas para no machacarlas.
 *
 * Campo aditivo del schema `plan-publicado-v1.json` (`default: []`, no en `required`): un evento v1 ya
 * serializado en el outbox antes de esta historia sigue deserializando sin este campo.
 */
@NamedInterface("events")
data class PublishedPersonalization(
    val sesionId: UUID,
    val dia: LocalDate,
    val alumnoId: UUID,
    val override: PersonalizedSession,
    val mensajeAlAlumno: String?,
)
