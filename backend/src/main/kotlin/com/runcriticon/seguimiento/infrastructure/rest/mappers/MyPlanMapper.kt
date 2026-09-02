package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.application.usecases.plan.GetMyWeekQuery
import com.runcriticon.seguimiento.domain.NotDoneReason
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.seguimiento.domain.ResolvedPace
import com.runcriticon.seguimiento.domain.ResolvedSession
import com.runcriticon.seguimiento.domain.SessionReport
import com.runcriticon.seguimiento.domain.SessionType
import com.runcriticon.seguimiento.domain.SessionVolume
import com.runcriticon.shared.api.rest.MiPlanSemanalResponse
import com.runcriticon.shared.api.rest.MiReporteRequest
import com.runcriticon.shared.api.rest.MiReporteResponse
import com.runcriticon.shared.api.rest.MiResolvedSessionResponse
import com.runcriticon.shared.api.rest.MiRitmoResueltoResponse
import com.runcriticon.shared.api.rest.Volumen
import java.time.ZoneOffset

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
        reporte = report?.toResponse(),
    )

/** Sin `descripcionDolor`: no se captura todavía (ver `SessionReport`). */
private fun SessionReport.toResponse(): MiReporteResponse =
    MiReporteResponse(
        estado = status.toMiReporteResponseEstado(),
        marcaDolor = painFlag,
        reportadoEn = reportedAt.atOffset(ZoneOffset.UTC),
        valoracion = rating,
        motivo = reason?.toMiReporteResponseMotivo(),
        notas = notes,
    )

/** El estado y el motivo del cuerpo de la petición, ya en tipos de dominio — [SessionReport.create] valida el
 * resto de invariantes (valoración/motivo obligatorios según el estado). */
internal fun MiReporteRequest.Estado.toDomain(): ReportStatus =
    when (this) {
        MiReporteRequest.Estado.HECHO -> ReportStatus.HECHO
        MiReporteRequest.Estado.PARCIAL -> ReportStatus.PARCIAL
        MiReporteRequest.Estado.NO_HECHO -> ReportStatus.NO_HECHO
    }

internal fun MiReporteRequest.Motivo.toDomain(): NotDoneReason =
    when (this) {
        MiReporteRequest.Motivo.CANSANCIO -> NotDoneReason.CANSANCIO
        MiReporteRequest.Motivo.TRABAJO -> NotDoneReason.TRABAJO
        MiReporteRequest.Motivo.VIAJE -> NotDoneReason.VIAJE
        MiReporteRequest.Motivo.ENFERMEDAD -> NotDoneReason.ENFERMEDAD
        MiReporteRequest.Motivo.SIN_TIEMPO -> NotDoneReason.SIN_TIEMPO
        MiReporteRequest.Motivo.MOLESTIAS -> NotDoneReason.MOLESTIAS
        MiReporteRequest.Motivo.OTRA -> NotDoneReason.OTRA
    }

private fun ReportStatus.toMiReporteResponseEstado(): MiReporteResponse.Estado =
    when (this) {
        ReportStatus.HECHO -> MiReporteResponse.Estado.HECHO
        ReportStatus.PARCIAL -> MiReporteResponse.Estado.PARCIAL
        ReportStatus.NO_HECHO -> MiReporteResponse.Estado.NO_HECHO
    }

private fun NotDoneReason.toMiReporteResponseMotivo(): MiReporteResponse.Motivo =
    when (this) {
        NotDoneReason.CANSANCIO -> MiReporteResponse.Motivo.CANSANCIO
        NotDoneReason.TRABAJO -> MiReporteResponse.Motivo.TRABAJO
        NotDoneReason.VIAJE -> MiReporteResponse.Motivo.VIAJE
        NotDoneReason.ENFERMEDAD -> MiReporteResponse.Motivo.ENFERMEDAD
        NotDoneReason.SIN_TIEMPO -> MiReporteResponse.Motivo.SIN_TIEMPO
        NotDoneReason.MOLESTIAS -> MiReporteResponse.Motivo.MOLESTIAS
        NotDoneReason.OTRA -> MiReporteResponse.Motivo.OTRA
    }

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
    when (this) {
        is ResolvedPace.Absolute ->
            MiRitmoResueltoResponse(segundosPorKm = secondsPerKm, referenciaDistancia = null, faltaMarca = null)
        is ResolvedPace.Relative ->
            secondsPerKm?.let {
                MiRitmoResueltoResponse(
                    segundosPorKm = it,
                    referenciaDistancia = reference.toReferenciaDistancia(),
                    faltaMarca = null,
                )
            } ?: MiRitmoResueltoResponse(
                segundosPorKm = null,
                referenciaDistancia = null,
                faltaMarca = reference.toFaltaMarca(),
            )
    }

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
