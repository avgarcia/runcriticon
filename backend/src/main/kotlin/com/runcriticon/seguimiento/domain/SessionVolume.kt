package com.runcriticon.seguimiento.domain

/**
 * Volumen de una sesión resuelta: distancia o tiempo, nunca los dos — refleja
 * `PublishedSession.volumenTipo/volumenMetros/volumenMinutos` del evento `PlanPublicado`.
 */
sealed class SessionVolume {
    data class Distance(
        val meters: Int,
    ) : SessionVolume()

    data class Duration(
        val minutes: Int,
    ) : SessionVolume()
}
