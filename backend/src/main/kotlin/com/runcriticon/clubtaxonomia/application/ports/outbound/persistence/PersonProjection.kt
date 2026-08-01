package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.Person
import java.time.Instant
import java.util.UUID

/**
 * Puerto de escritura de la proyección local de personas del club. Lo alimentan los listeners de eventos de
 * `identidad`; ninguna ruta de usuario escribe aquí.
 */
interface PersonProjection {
    /**
     * Materializa [person] en la proyección, insertándola o actualizándola. Aplica la **guarda de orden**: si la fila
     * ya recogía un evento igual o más reciente que [occurredAt], la escritura se descarta y devuelve `false`.
     *
     * La guarda es necesaria porque la entrega del outbox es asíncrona y no garantiza orden: `AlumnoActivado` puede
     * llegar antes que `AlumnoInvitado`, y un reintento puede reentregar el evento viejo después del nuevo. Sin ella,
     * el último en llegar gana y el estado de la persona retrocedería a `INVITADO`.
     *
     * También se descarta la escritura si la persona fue **suprimida**: quien ejerció su derecho al olvido no vuelve a
     * materializarse, ni siquiera por un evento de alta rezagado que llegue después del borrado (ver [PersonErasure]).
     *
     * @return `true` si la proyección cambió; `false` si se descartó — por ser el evento más antiguo que el ya
     *  aplicado, o por tratarse de una persona ya suprimida.
     */
    fun upsert(
        person: Person,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean

    /**
     * Retraso de la proyección en segundos: `now()` menos el `occurredAt` del evento más reciente ya aplicado. `0` si
     * la proyección está vacía, porque una proyección sin filas no está retrasada — no hay nada que le falte.
     *
     * Alimenta el gauge `projection_lag_seconds`. El umbral de 60 s que da la proyección por obsoleta es una puerta de
     * lectura: la aplicará el primer caso de uso que lea de esta proyección, que todavía no existe.
     */
    fun lagSeconds(): Long
}
