package com.runcriticon.planificacion.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.time.LocalDate

private const val MAX_NOTES_LENGTH = 1000

/**
 * Una sesión de entrenamiento del plan, para un día concreto (LAL-24, editor de sesión).
 *
 * [volume] y [pace] son `null` para `DESCANSO`: una sesión de descanso no lleva carga. Para el resto de
 * tipos pueden quedar `null` mientras el entrenador todavía no los ha rellenado — el AC no exige que estén
 * completos desde el alta.
 */
data class Session(
    val id: SessionId,
    val day: LocalDate,
    val type: SessionType,
    val volume: SessionVolume? = null,
    val pace: Pace? = null,
    val notes: String? = null,
) {
    companion object {
        /**
         * Crea una sesión validando lo **intrínseco** a ella (volumen positivo, notas dentro de longitud,
         * descanso sin carga). Lo relativo al plan que la contiene —día dentro de la semana, día ya
         * ocupado— lo valida `WeeklyPlan.addSession`, no esta factoría.
         */
        fun create(
            day: LocalDate,
            type: SessionType,
            volume: SessionVolume? = null,
            pace: Pace? = null,
            notes: String? = null,
            id: SessionId = SessionId.new(),
        ): Either<PlanificacionError, Session> =
            either {
                ensure(type != SessionType.DESCANSO || (volume == null && pace == null)) {
                    PlanificacionError.InvalidInput(
                        field = "tipo",
                        reason = "una sesión de descanso no lleva volumen ni ritmo",
                    )
                }
                ensure(volumePositive(volume)) {
                    PlanificacionError.InvalidInput(field = "volumen", reason = "debe ser mayor que cero")
                }
                ensure(notes == null || notes.length <= MAX_NOTES_LENGTH) {
                    PlanificacionError.InvalidInput(
                        field = "notas",
                        reason = "no puede pasar de 1000 caracteres",
                    )
                }
                Session(id = id, day = day, type = type, volume = volume, pace = pace, notes = notes)
            }

        private fun volumePositive(volume: SessionVolume?): Boolean =
            when (volume) {
                null -> true
                is SessionVolume.Distance -> volume.meters > 0
                is SessionVolume.Duration -> volume.minutes > 0
            }
    }
}
