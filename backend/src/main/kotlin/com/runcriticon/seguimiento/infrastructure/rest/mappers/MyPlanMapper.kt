package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.application.usecases.plan.GetMyWeekQuery
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.shared.api.rest.MiPlanSemanalResponse
import com.runcriticon.shared.api.rest.MiResolvedSessionResponse
import com.runcriticon.shared.api.rest.MiRitmoResueltoResponse
import com.runcriticon.shared.api.rest.Volumen

/** La semana resuelta, para `GET /me/plan`. */
internal fun GetMyWeekQuery.WeekResult.toResponse(): MiPlanSemanalResponse =
    MiPlanSemanalResponse(
        semana = week,
        sesiones = sessions.map { it.toResponse() },
    )

/** Sin `esPersonalizada`: es uso interno del módulo, nunca se traduce al contrato (ver `ResolvedSession`). */
internal fun ResolvedSession.toResponse(): MiResolvedSessionResponse =
    MiResolvedSessionResponse(
        dia = day,
        tipo = type.toMiResolvedSessionResponseTipo(),
        volumen = volume?.toResponse(),
        ritmo = pace?.toResponse(),
        notas = notes,
        mensajeDelEntrenador = messageToStudent,
    )

private fun SessionType.toMiResolvedSessionResponseTipo(): MiResolvedSessionResponse.Tipo =
    when (this) {
        SessionType.RODAJE -> MiResolvedSessionResponse.Tipo.RODAJE
        SessionType.SERIES -> MiResolvedSessionResponse.Tipo.SERIES
        SessionType.TEMPO -> MiResolvedSessionResponse.Tipo.TEMPO
        SessionType.TIRADA_LARGA -> MiResolvedSessionResponse.Tipo.TIRADA_LARGA
        SessionType.FARTLEK -> MiResolvedSessionResponse.Tipo.FARTLEK
        SessionType.CUESTAS -> MiResolvedSessionResponse.Tipo.CUESTAS
        SessionType.PROGRESIVO -> MiResolvedSessionResponse.Tipo.PROGRESIVO
        SessionType.FUERZA_CROSS -> MiResolvedSessionResponse.Tipo.FUERZA_CROSS
        SessionType.COMPETICION -> MiResolvedSessionResponse.Tipo.COMPETICION
        SessionType.DESCANSO -> MiResolvedSessionResponse.Tipo.DESCANSO
    }

private fun SessionVolume.toResponse(): Volumen =
    when (this) {
        is SessionVolume.Distance -> Volumen(tipo = Volumen.Tipo.DISTANCIA, metros = meters, minutos = null)
        is SessionVolume.Duration -> Volumen(tipo = Volumen.Tipo.TIEMPO, metros = null, minutos = minutes)
    }

private fun ResolvedPace.toResponse(): MiRitmoResueltoResponse =
    MiRitmoResueltoResponse(
        segundosPorKm = secondsPerKm,
        referenciaDistancia = referenceDistance?.toReferenciaDistancia(),
        faltaMarca = missingMark?.toFaltaMarca(),
    )

private fun RaceDistance.toReferenciaDistancia(): MiRitmoResueltoResponse.ReferenciaDistancia =
    when (this) {
        RaceDistance.FIVE_K -> MiRitmoResueltoResponse.ReferenciaDistancia._5_K
        RaceDistance.TEN_K -> MiRitmoResueltoResponse.ReferenciaDistancia._10_K
        RaceDistance.HALF_MARATHON -> MiRitmoResueltoResponse.ReferenciaDistancia._21_K
        RaceDistance.MARATHON -> MiRitmoResueltoResponse.ReferenciaDistancia._42_K
    }

private fun RaceDistance.toFaltaMarca(): MiRitmoResueltoResponse.FaltaMarca =
    when (this) {
        RaceDistance.FIVE_K -> MiRitmoResueltoResponse.FaltaMarca._5_K
        RaceDistance.TEN_K -> MiRitmoResueltoResponse.FaltaMarca._10_K
        RaceDistance.HALF_MARATHON -> MiRitmoResueltoResponse.FaltaMarca._21_K
        RaceDistance.MARATHON -> MiRitmoResueltoResponse.FaltaMarca._42_K
    }
