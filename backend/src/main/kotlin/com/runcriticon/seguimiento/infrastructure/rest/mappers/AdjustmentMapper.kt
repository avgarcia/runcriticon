package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.AdjustmentReason
import com.runcriticon.seguimiento.domain.ConflictResolution
import com.runcriticon.seguimiento.domain.DayAdjustment
import com.runcriticon.shared.api.rest.MiReajusteRequest
import com.runcriticon.shared.api.rest.MiReajusteResponse

/** El reajuste guardado, para `PUT /me/reajustes/{dia}` (LAL-33). */
internal fun DayAdjustment.toResponse(): MiReajusteResponse =
    MiReajusteResponse(
        accion = action.toMiReajusteResponseAccion(),
        diaPlanificado = plannedDay,
        diaDestino = targetDay,
        motivo = reason.toMiReajusteResponseMotivo(),
        mensaje = message,
        marcaDolor = painFlag,
    )

internal fun MiReajusteRequest.Accion.toDomain(): AdjustmentAction =
    when (this) {
        MiReajusteRequest.Accion.MOVIDA -> AdjustmentAction.MOVIDA
        MiReajusteRequest.Accion.SALTADA -> AdjustmentAction.SALTADA
    }

internal fun MiReajusteRequest.Motivo.toDomain(): AdjustmentReason =
    when (this) {
        MiReajusteRequest.Motivo.CANSANCIO -> AdjustmentReason.CANSANCIO
        MiReajusteRequest.Motivo.MOLESTIAS -> AdjustmentReason.MOLESTIAS
        MiReajusteRequest.Motivo.IMPREVISTO -> AdjustmentReason.IMPREVISTO
    }

internal fun MiReajusteRequest.ResolucionConflicto.toDomain(): ConflictResolution =
    when (this) {
        MiReajusteRequest.ResolucionConflicto.REEMPLAZAR -> ConflictResolution.REEMPLAZAR
        MiReajusteRequest.ResolucionConflicto.INTERCAMBIAR -> ConflictResolution.INTERCAMBIAR
    }

private fun AdjustmentAction.toMiReajusteResponseAccion(): MiReajusteResponse.Accion =
    when (this) {
        AdjustmentAction.MOVIDA -> MiReajusteResponse.Accion.MOVIDA
        AdjustmentAction.SALTADA -> MiReajusteResponse.Accion.SALTADA
    }

private fun AdjustmentReason.toMiReajusteResponseMotivo(): MiReajusteResponse.Motivo =
    when (this) {
        AdjustmentReason.CANSANCIO -> MiReajusteResponse.Motivo.CANSANCIO
        AdjustmentReason.MOLESTIAS -> MiReajusteResponse.Motivo.MOLESTIAS
        AdjustmentReason.IMPREVISTO -> MiReajusteResponse.Motivo.IMPREVISTO
    }
