package com.runcriticon.seguimiento.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.time.Instant

/**
 * La marca del alumno en una distancia estándar (LAL-31, ADR-0002 D7): el mejor tiempo del corredor,
 * privado, sin histórico en MVP — cada actualización sobreescribe la anterior.
 *
 * Agregado pequeño (DDD táctico): su identidad es la PK compuesta `(alumnoId, distancia)`, resuelta por
 * el caso de uso contra `actor.userId` — nunca forma parte de este value object. Su único invariante es
 * `timeSeconds > 0`; no hay invariantes cruzadas entre las cuatro distancias de un mismo alumno.
 */
data class StudentMark(
    val distance: RaceDistance,
    val timeSeconds: Int,
    val modifiedAt: Instant,
) {
    companion object {
        fun create(
            distance: RaceDistance,
            timeSeconds: Int,
            modifiedAt: Instant,
        ): Either<SeguimientoError, StudentMark> =
            either {
                ensure(timeSeconds > 0) {
                    SeguimientoError.InvalidInput(field = "tiempoSegundos", reason = "not_positive")
                }
                StudentMark(distance, timeSeconds, modifiedAt)
            }
    }
}
