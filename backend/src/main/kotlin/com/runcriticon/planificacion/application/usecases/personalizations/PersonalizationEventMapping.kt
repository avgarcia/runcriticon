package com.runcriticon.planificacion.application.usecases.personalizations

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.RaceDistance
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionOverride
import com.runcriticon.planificacion.domain.SessionVolume

/**
 * Traduce a la forma de payload de evento [PersonalizedSession] (LAL-26). Compartida entre
 * `SetPersonalizationCommand`/`RemovePersonalizationCommand` (eventos propios) y `PublishPlanCommand`
 * (personalizaciones ya vigentes que viajan dentro de `PlanPublicado`, AC2) — evita duplicar el `when` de
 * ritmo/volumen en tres sitios.
 */
internal fun SessionOverride.toPersonalizedSession(): PersonalizedSession {
    val volume = volume
    val pace = pace
    return PersonalizedSession(
        tipo = type.name,
        volumenTipo =
            when (volume) {
                null -> null
                is SessionVolume.Distance -> "DISTANCIA"
                is SessionVolume.Duration -> "TIEMPO"
            },
        volumenMetros = (volume as? SessionVolume.Distance)?.meters,
        volumenMinutos = (volume as? SessionVolume.Duration)?.minutes,
        ritmoTipo =
            when (pace) {
                null -> null
                is Pace.Absoluto -> "ABSOLUTO"
                is Pace.Relativo -> "RELATIVO"
            },
        ritmoSegundosPorKm = (pace as? Pace.Absoluto)?.secondsPerKm,
        ritmoReferencia = (pace as? Pace.Relativo)?.reference?.toEventLiteral(),
        ritmoDeltaSegundosPorKm = (pace as? Pace.Relativo)?.deltaSecondsPerKm,
        notas = notes,
    )
}

/**
 * Traduce la sesión base a [PersonalizedSession] (LAL-26): la usa `RemovePersonalizationCommand` para
 * embeber en `PersonalizacionRetirada.baseSession` la sesión a la que Seguimiento debe volver.
 */
internal fun Session.toPersonalizedSession(): PersonalizedSession {
    val volume = volume
    val pace = pace
    return PersonalizedSession(
        tipo = type.name,
        volumenTipo =
            when (volume) {
                null -> null
                is SessionVolume.Distance -> "DISTANCIA"
                is SessionVolume.Duration -> "TIEMPO"
            },
        volumenMetros = (volume as? SessionVolume.Distance)?.meters,
        volumenMinutos = (volume as? SessionVolume.Duration)?.minutes,
        ritmoTipo =
            when (pace) {
                null -> null
                is Pace.Absoluto -> "ABSOLUTO"
                is Pace.Relativo -> "RELATIVO"
            },
        ritmoSegundosPorKm = (pace as? Pace.Absoluto)?.secondsPerKm,
        ritmoReferencia = (pace as? Pace.Relativo)?.reference?.toEventLiteral(),
        ritmoDeltaSegundosPorKm = (pace as? Pace.Relativo)?.deltaSecondsPerKm,
        notas = notes,
    )
}

internal fun RaceDistance.toEventLiteral(): String =
    when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "21K"
        RaceDistance.MARATHON -> "42K"
    }
