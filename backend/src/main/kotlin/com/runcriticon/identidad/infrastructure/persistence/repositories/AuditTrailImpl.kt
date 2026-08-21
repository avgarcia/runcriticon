package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.security.EmailHasher
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.infrastructure.persistence.mappers.AuditEventMapper
import com.runcriticon.identidad.infrastructure.persistence.mappers.AuditEventMapperImpl
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador del puerto [AuditTrail] sobre Spring Data. Persiste el asiento en `identidad.evento_auditoria` dentro de la
 * transacción del caso de uso que lo invoca. La anonimización va por `JdbcTemplate` directo: un `UPDATE` masivo con
 * `CASE` por columna no encaja bien en un derived-query de Spring Data.
 */
@Repository
class AuditTrailImpl(
    private val jpa: AuditEventEntityRepository,
    private val jdbc: JdbcTemplate,
    private val emailHasher: EmailHasher,
) : AuditTrail {
    private val mapper: AuditEventMapper = AuditEventMapperImpl

    @NoAuthScope("escritura de auditoría del sistema; no devuelve datos de cliente")
    override fun record(entry: AuditEntry) {
        jpa.save(mapper.toEntity(entry))
    }

    @NoAuthScope(
        "invocado desde DeleteUserCommand dentro de su propia transacción; el filtro es por personId/email, no por " +
            "club",
    )
    override fun anonymize(
        personId: UUID,
        email: Email,
    ): Int {
        val porIdentificador = jdbc.update(ANONYMIZE_BY_ID_SQL, personId, personId, personId, personId)
        val porEmailHash = jdbc.update(ANONYMIZE_BY_EMAIL_HASH_SQL, emailHasher.hash(email.value))
        return porIdentificador + porEmailHash
    }

    private companion object {
        // metadata sin la clave 'ip' se deja tal cual: no se le añade una clave que no tenía. Cuando sí la
        // tiene, se sustituye por su versión truncada; sobre metadata NULL, todas las operaciones jsonb son
        // no-op (operador estricto), así que un asiento sin metadata queda sin metadata.
        // `metadata -> 'ip' IS NOT NULL` en vez del operador `?` de jsonb: JdbcTemplate interpreta cualquier
        // `?` suelto del SQL como marcador de parámetro posicional, aunque esté dentro de un operador jsonb.
        const val ANONYMIZE_METADATA_EXPR =
            """
            CASE
                WHEN metadata -> 'ip' IS NOT NULL THEN (metadata - 'email_hash' - 'email')
                                          || jsonb_build_object('ip', identidad.trunca_ip(metadata ->> 'ip'))
                ELSE metadata - 'email_hash' - 'email'
            END
            """

        // `CASE` por columna, no un UPDATE que pone ambas a NULL en cuanto coincide una: un asiento
        // `INVITACION_EMITIDA` de un admin lleva actor_id = admin, sujeto_id = invitado, y borrar al
        // admin no debe despojar el id del invitado, que no ha pedido nada.
        const val ANONYMIZE_BY_ID_SQL =
            """
            UPDATE identidad.evento_auditoria
               SET actor_id  = CASE WHEN actor_id  = ? THEN NULL ELSE actor_id  END,
                   sujeto_id = CASE WHEN sujeto_id = ? THEN NULL ELSE sujeto_id END,
                   ip        = identidad.trunca_ip(host(ip))::inet,
                   metadata  = $ANONYMIZE_METADATA_EXPR
             WHERE (actor_id = ? OR sujeto_id = ?)
            """

        // Los asientos *_RATE_LIMITED de magic-link y reseteo son de flujo anónimo: actor_id y sujeto_id
        // van a NULL desde el origen, así que solo son alcanzables por el email_hash que llevan en
        // metadata. No hay actor_id/sujeto_id que despojar aquí.
        const val ANONYMIZE_BY_EMAIL_HASH_SQL =
            """
            UPDATE identidad.evento_auditoria
               SET metadata = $ANONYMIZE_METADATA_EXPR
             WHERE metadata ->> 'email_hash' = ?
            """
    }
}
