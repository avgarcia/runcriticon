package com.runcriticon.planificacion.domain

/**
 * Ritmo objetivo de una sesión (ADR-0002 D6). Los nombres de las subclases (`Absoluto`, `Relativo`) son los que fija
 * esa sub-decisión — Aceptada, no se retraducen a inglés a diferencia del resto del dominio del módulo.
 */
sealed class Pace {
    data class Absoluto(
        val secondsPerKm: Int,
    ) : Pace()

    data class Relativo(
        val reference: RaceDistance,
        val deltaSecondsPerKm: Int,
    ) : Pace()
}
