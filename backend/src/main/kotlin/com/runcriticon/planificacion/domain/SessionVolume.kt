package com.runcriticon.planificacion.domain

/**
 * Volumen de una sesión: distancia **o** tiempo, nunca los dos (AC1 de LAL-24). A diferencia de las
 * subclases de [Pace], estos nombres no los fija ningún ADR — van en inglés como el resto del dominio del
 * módulo (ADR-0008 D4).
 */
sealed class SessionVolume {
    data class Distance(
        val meters: Int,
    ) : SessionVolume()

    data class Duration(
        val minutes: Int,
    ) : SessionVolume()
}
