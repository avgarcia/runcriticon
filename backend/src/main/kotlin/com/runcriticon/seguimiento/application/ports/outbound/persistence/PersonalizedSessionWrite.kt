package com.runcriticon.seguimiento.application.ports.outbound.persistence

/**
 * Desenlace de [ResolvedPlanProjection.writePersonalizedSession]. El `UPDATE` no toca ninguna fila por tres
 * motivos distintos, y cada uno pide al listener una reacción distinta: confirmar el evento, descartarlo o
 * dejarlo sin completar para que el outbox lo reentregue. Un `Boolean` los juntaba, y el listener los tragaba
 * los tres en silencio.
 */
enum class PersonalizedSessionWrite {
    /** La fila del alumno ese día recoge la sesión. */
    APPLIED,

    /**
     * La fila existe pero ya recogía un evento más reciente: descarte correcto de la guarda de orden, como en
     * los demás listeners de proyección.
     */
    STALE,

    /**
     * El plan está proyectado pero sin fila de ese alumno ese día: el alumno se suprimió (el listener de
     * borrado RGPD purga sus filas) o nunca estuvo en el snapshot. Reentregar no lo arreglaría nunca.
     */
    STUDENT_NOT_IN_PLAN,

    /**
     * El plan todavía no tiene ninguna fila: la personalización llegó antes que su `PlanPublicado`, algo que
     * pasa al reentregar el outbox tras un redespliegue, sin orden entre listeners. Hay que reintentarla.
     */
    PLAN_NOT_PROJECTED,
}
