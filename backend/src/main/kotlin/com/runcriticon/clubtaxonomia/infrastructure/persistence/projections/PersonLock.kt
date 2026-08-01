package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.ResultSet
import java.util.UUID

/**
 * Serializa por persona las dos rutas que se disputan su fila en la proyección: la que la materializa desde un evento
 * de alta y la que la borra al ejercer el derecho de supresión.
 *
 * **Por qué no basta con consultar la lápida.** Sin lock, la escritura puede comprobar que no hay lápida, la
 * transacción de borrado commitear justo después, y la escritura insertar igualmente: la persona borrada reaparece.
 * Es una carrera entre comprobación y uso que ninguna reescritura de la sentencia cierra, porque la instantánea de la
 * consulta se toma antes del commit ajeno. Tomar el lock antes de mirar la lápida convierte las dos rutas en
 * secuenciales para esa persona.
 *
 * ⚠️ **Depende del nivel de aislamiento `READ COMMITTED`** (el de por defecto): es lo que hace que, tras esperar al
 * lock, la siguiente sentencia tome una instantánea nueva y vea la lápida recién commiteada. Si alguien elevara el
 * aislamiento de estos listeners a `REPEATABLE READ`, la guarda dejaría de funcionar **en silencio**, sin que ningún
 * test falle.
 *
 * El lock es `_xact_`: lo libera el commit o el rollback de la transacción, nunca hay que soltarlo a mano.
 */
internal fun lockPerson(
    jdbc: JdbcTemplate,
    personId: UUID,
) {
    jdbc.query(LOCK_SQL, { _: ResultSet -> }, PERSON_LOCK_NAMESPACE, personId.toString())
}

/**
 * Primer argumento del lock: el espacio de claves de los advisory locks es global a la base de datos, así que la
 * pareja (namespace, clave) es lo que evita colisionar con otro uso futuro. Este namespace queda reservado para
 * `club_taxonomia.persona`.
 */
private const val PERSON_LOCK_NAMESPACE = 8_101

/**
 * `hashtext` reduce el UUID a los 32 bits que admite el lock. Una colisión solo haría esperar de más a dos personas
 * distintas —cuesta rendimiento, nunca corrección—, y la clave real de la comprobación sigue siendo el `id`.
 */
private const val LOCK_SQL = "SELECT pg_advisory_xact_lock(?, hashtext(?::text))"
