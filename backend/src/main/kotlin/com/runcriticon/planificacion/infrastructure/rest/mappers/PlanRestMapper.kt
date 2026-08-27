package com.runcriticon.planificacion.infrastructure.rest.mappers

import com.runcriticon.planificacion.application.usecases.plans.PublishPlanCommand
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.Personalization
import com.runcriticon.planificacion.domain.PlanStatus
import com.runcriticon.planificacion.domain.RaceDistance
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.api.rest.PersonalizationRequest
import com.runcriticon.shared.api.rest.PersonalizationResponse
import com.runcriticon.shared.api.rest.PlanDetalleResponse
import com.runcriticon.shared.api.rest.PlanResponse
import com.runcriticon.shared.api.rest.PlanesResponse
import com.runcriticon.shared.api.rest.PublicacionResponse
import com.runcriticon.shared.api.rest.Ritmo
import com.runcriticon.shared.api.rest.TrainingSessionRequest
import com.runcriticon.shared.api.rest.TrainingSessionResponse
import com.runcriticon.shared.api.rest.TrainingSessionUpdateRequest
import com.runcriticon.shared.api.rest.Volumen

/** Traduce `WeeklyPlan` a su modelo del contrato. Sin sesiones ni personalizaciones: ver `PlanResponse` en el spec. */
internal fun WeeklyPlan.toResponse(): PlanResponse =
    PlanResponse(
        id = id.value,
        grupoId = groupId.value,
        semana = week,
        estado = status.toPlanResponse(),
    )

internal fun List<WeeklyPlan>.toResponse(): PlanesResponse = PlanesResponse(planes = map { it.toResponse() })

/** El plan completo con sus sesiones y personalizaciones (LAL-24/LAL-26), para `GET /planes/{planId}` y para
 * el detalle recalculado que devuelven `PUT`/`DELETE` de una personalización. */
internal fun WeeklyPlan.toDetailResponse(): PlanDetalleResponse =
    PlanDetalleResponse(
        id = id.value,
        grupoId = groupId.value,
        semana = week,
        estado = status.toPlanDetalleResponse(),
        sesiones = sessions.map { it.toResponse() },
        personalizaciones = personalizations.map { it.toResponse() },
    )

/** El plan tras publicarse, con el tamaño del snapshot congelado (LAL-25). */
internal fun PublishPlanCommand.Result.toPublicacionResponse(): PublicacionResponse =
    PublicacionResponse(
        plan = plan.toDetailResponse(),
        alumnosEnSnapshot = studentsInSnapshot,
    )

private fun PlanStatus.toPlanResponse(): PlanResponse.Estado =
    when (this) {
        PlanStatus.BORRADOR -> PlanResponse.Estado.BORRADOR
        PlanStatus.PUBLICADO -> PlanResponse.Estado.PUBLICADO
    }

private fun PlanStatus.toPlanDetalleResponse(): PlanDetalleResponse.Estado =
    when (this) {
        PlanStatus.BORRADOR -> PlanDetalleResponse.Estado.BORRADOR
        PlanStatus.PUBLICADO -> PlanDetalleResponse.Estado.PUBLICADO
    }

internal fun Session.toResponse(): TrainingSessionResponse =
    TrainingSessionResponse(
        id = id.value,
        dia = day,
        tipo = type.toTrainingSessionResponseTipo(),
        volumen = volume?.toResponse(),
        ritmo = pace?.toResponse(),
        notas = notes,
    )

internal fun TrainingSessionRequest.Tipo.toDomain(): SessionType =
    when (this) {
        TrainingSessionRequest.Tipo.RODAJE -> SessionType.RODAJE
        TrainingSessionRequest.Tipo.SERIES -> SessionType.SERIES
        TrainingSessionRequest.Tipo.TEMPO -> SessionType.TEMPO
        TrainingSessionRequest.Tipo.TIRADA_LARGA -> SessionType.TIRADA_LARGA
        TrainingSessionRequest.Tipo.FARTLEK -> SessionType.FARTLEK
        TrainingSessionRequest.Tipo.CUESTAS -> SessionType.CUESTAS
        TrainingSessionRequest.Tipo.PROGRESIVO -> SessionType.PROGRESIVO
        TrainingSessionRequest.Tipo.FUERZA_CROSS -> SessionType.FUERZA_CROSS
        TrainingSessionRequest.Tipo.COMPETICION -> SessionType.COMPETICION
        TrainingSessionRequest.Tipo.DESCANSO -> SessionType.DESCANSO
    }

internal fun TrainingSessionUpdateRequest.Tipo.toDomain(): SessionType =
    when (this) {
        TrainingSessionUpdateRequest.Tipo.RODAJE -> SessionType.RODAJE
        TrainingSessionUpdateRequest.Tipo.SERIES -> SessionType.SERIES
        TrainingSessionUpdateRequest.Tipo.TEMPO -> SessionType.TEMPO
        TrainingSessionUpdateRequest.Tipo.TIRADA_LARGA -> SessionType.TIRADA_LARGA
        TrainingSessionUpdateRequest.Tipo.FARTLEK -> SessionType.FARTLEK
        TrainingSessionUpdateRequest.Tipo.CUESTAS -> SessionType.CUESTAS
        TrainingSessionUpdateRequest.Tipo.PROGRESIVO -> SessionType.PROGRESIVO
        TrainingSessionUpdateRequest.Tipo.FUERZA_CROSS -> SessionType.FUERZA_CROSS
        TrainingSessionUpdateRequest.Tipo.COMPETICION -> SessionType.COMPETICION
        TrainingSessionUpdateRequest.Tipo.DESCANSO -> SessionType.DESCANSO
    }

private fun SessionType.toTrainingSessionResponseTipo(): TrainingSessionResponse.Tipo =
    when (this) {
        SessionType.RODAJE -> TrainingSessionResponse.Tipo.RODAJE
        SessionType.SERIES -> TrainingSessionResponse.Tipo.SERIES
        SessionType.TEMPO -> TrainingSessionResponse.Tipo.TEMPO
        SessionType.TIRADA_LARGA -> TrainingSessionResponse.Tipo.TIRADA_LARGA
        SessionType.FARTLEK -> TrainingSessionResponse.Tipo.FARTLEK
        SessionType.CUESTAS -> TrainingSessionResponse.Tipo.CUESTAS
        SessionType.PROGRESIVO -> TrainingSessionResponse.Tipo.PROGRESIVO
        SessionType.FUERZA_CROSS -> TrainingSessionResponse.Tipo.FUERZA_CROSS
        SessionType.COMPETICION -> TrainingSessionResponse.Tipo.COMPETICION
        SessionType.DESCANSO -> TrainingSessionResponse.Tipo.DESCANSO
    }

/**
 * `metros`/`minutos`/`referencia`/`deltaSegundosPorKm` son "presentes solo si `tipo` es X" por contrato
 * (documentado en el spec), pero el schema no lo puede exigir de forma declarativa (no hay `oneOf` aquí). Un
 * request que rompa esa pareja es una precondición imposible para un cliente que respete el contrato — se
 * usa `requireNotNull` (ADR-0008: "require/check para precondiciones imposibles"), no un `PlanificacionError`:
 * `GlobalRestExceptionHandler` lo convierte en 500 neutro, igual que `error(...)` en `WeeklyPlanRepositoryJdbc`.
 */
internal fun Volumen?.toDomain(): SessionVolume? =
    when (this?.tipo) {
        null -> null
        Volumen.Tipo.DISTANCIA ->
            SessionVolume.Distance(meters = requireNotNull(metros) { "metros requerido para volumen DISTANCIA" })
        Volumen.Tipo.TIEMPO ->
            SessionVolume.Duration(minutes = requireNotNull(minutos) { "minutos requerido para volumen TIEMPO" })
    }

internal fun Ritmo?.toDomain(): Pace? =
    when (this?.tipo) {
        null -> null
        Ritmo.Tipo.ABSOLUTO ->
            Pace.Absoluto(
                secondsPerKm = requireNotNull(segundosPorKm) { "segundosPorKm requerido para ritmo ABSOLUTO" },
            )
        Ritmo.Tipo.RELATIVO ->
            Pace.Relativo(
                reference = requireNotNull(referencia) { "referencia requerida para ritmo RELATIVO" }.toDomain(),
                deltaSecondsPerKm =
                    requireNotNull(deltaSegundosPorKm) { "deltaSegundosPorKm requerido para ritmo RELATIVO" },
            )
    }

private fun Ritmo.Referencia.toDomain(): RaceDistance =
    when (this) {
        Ritmo.Referencia._5_K -> RaceDistance.FIVE_K
        Ritmo.Referencia._10_K -> RaceDistance.TEN_K
        Ritmo.Referencia._21_K -> RaceDistance.HALF_MARATHON
        Ritmo.Referencia._42_K -> RaceDistance.MARATHON
    }

private fun SessionVolume.toResponse(): Volumen =
    when (this) {
        is SessionVolume.Distance -> Volumen(tipo = Volumen.Tipo.DISTANCIA, metros = meters, minutos = null)
        is SessionVolume.Duration -> Volumen(tipo = Volumen.Tipo.TIEMPO, metros = null, minutos = minutes)
    }

private fun Pace.toResponse(): Ritmo =
    when (this) {
        is Pace.Absoluto ->
            Ritmo(
                tipo = Ritmo.Tipo.ABSOLUTO,
                segundosPorKm = secondsPerKm,
                referencia = null,
                deltaSegundosPorKm = null,
            )
        is Pace.Relativo ->
            Ritmo(
                tipo = Ritmo.Tipo.RELATIVO,
                segundosPorKm = null,
                referencia = reference.toRitmoReferencia(),
                deltaSegundosPorKm = deltaSecondsPerKm,
            )
    }

private fun RaceDistance.toRitmoReferencia(): Ritmo.Referencia =
    when (this) {
        RaceDistance.FIVE_K -> Ritmo.Referencia._5_K
        RaceDistance.TEN_K -> Ritmo.Referencia._10_K
        RaceDistance.HALF_MARATHON -> Ritmo.Referencia._21_K
        RaceDistance.MARATHON -> Ritmo.Referencia._42_K
    }

/** Una personalización vigente, embebida en `PlanDetalleResponse.personalizaciones` (LAL-26). */
internal fun Personalization.toResponse(): PersonalizationResponse =
    PersonalizationResponse(
        sesionId = sessionId.value,
        alumnoId = studentId.value,
        tipo = override.type.toPersonalizationResponseTipo(),
        volumen = override.volume?.toResponse(),
        ritmo = override.pace?.toResponse(),
        notas = override.notes,
        mensajeAlAlumno = messageToStudent,
    )

internal fun PersonalizationRequest.Tipo.toDomain(): SessionType =
    when (this) {
        PersonalizationRequest.Tipo.RODAJE -> SessionType.RODAJE
        PersonalizationRequest.Tipo.SERIES -> SessionType.SERIES
        PersonalizationRequest.Tipo.TEMPO -> SessionType.TEMPO
        PersonalizationRequest.Tipo.TIRADA_LARGA -> SessionType.TIRADA_LARGA
        PersonalizationRequest.Tipo.FARTLEK -> SessionType.FARTLEK
        PersonalizationRequest.Tipo.CUESTAS -> SessionType.CUESTAS
        PersonalizationRequest.Tipo.PROGRESIVO -> SessionType.PROGRESIVO
        PersonalizationRequest.Tipo.FUERZA_CROSS -> SessionType.FUERZA_CROSS
        PersonalizationRequest.Tipo.COMPETICION -> SessionType.COMPETICION
        PersonalizationRequest.Tipo.DESCANSO -> SessionType.DESCANSO
    }

private fun SessionType.toPersonalizationResponseTipo(): PersonalizationResponse.Tipo =
    when (this) {
        SessionType.RODAJE -> PersonalizationResponse.Tipo.RODAJE
        SessionType.SERIES -> PersonalizationResponse.Tipo.SERIES
        SessionType.TEMPO -> PersonalizationResponse.Tipo.TEMPO
        SessionType.TIRADA_LARGA -> PersonalizationResponse.Tipo.TIRADA_LARGA
        SessionType.FARTLEK -> PersonalizationResponse.Tipo.FARTLEK
        SessionType.CUESTAS -> PersonalizationResponse.Tipo.CUESTAS
        SessionType.PROGRESIVO -> PersonalizationResponse.Tipo.PROGRESIVO
        SessionType.FUERZA_CROSS -> PersonalizationResponse.Tipo.FUERZA_CROSS
        SessionType.COMPETICION -> PersonalizationResponse.Tipo.COMPETICION
        SessionType.DESCANSO -> PersonalizationResponse.Tipo.DESCANSO
    }
