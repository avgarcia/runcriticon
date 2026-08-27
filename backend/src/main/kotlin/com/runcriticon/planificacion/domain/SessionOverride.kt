package com.runcriticon.planificacion.domain

import arrow.core.Either
import arrow.core.raise.either

/**
 * El ajuste que una [Personalization] aplica sobre la sesión base para un alumno concreto (ADR-0002 D9,
 * LAL-26): **misma forma que [Session]**, sin [Session.id] ni [Session.day] — el día lo fija la sesión que
 * sobrescribe, el override no tiene fecha propia. Es reemplazo completo, no patch parcial: aplicar un
 * override sustituye tipo/volumen/ritmo/notas enteros, nunca combina campo a campo con la base.
 *
 * Comparte las validaciones intrínsecas con [Session] vía [ensureValidSessionContent]: un override de
 * `DESCANSO` tampoco lleva volumen ni ritmo, el volumen tiene que ser positivo y las notas respetan el
 * mismo tope de longitud.
 */
data class SessionOverride(
    val type: SessionType,
    val volume: SessionVolume? = null,
    val pace: Pace? = null,
    val notes: String? = null,
) {
    companion object {
        fun create(
            type: SessionType,
            volume: SessionVolume? = null,
            pace: Pace? = null,
            notes: String? = null,
        ): Either<PlanificacionError, SessionOverride> =
            either {
                ensureValidSessionContent(type, volume, pace, notes)
                SessionOverride(type = type, volume = volume, pace = pace, notes = notes)
            }
    }
}
