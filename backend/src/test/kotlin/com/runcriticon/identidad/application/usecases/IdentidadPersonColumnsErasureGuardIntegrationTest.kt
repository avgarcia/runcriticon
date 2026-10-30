package com.runcriticon.identidad.application.usecases

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Vigila que ninguna columna de persona del esquema `identidad` quede fuera de la supresión. `RgpdArchTest` obliga a
 * que cada `@Entity` declare su categoría, pero no comprueba que el borrado alcance sus columnas: una columna nueva
 * con un usuario, un nombre o una IP en una tabla ya clasificada pasaría sin que nada lo notara. Eso es justo lo que
 * ocurrió con `invitacion.invitado_por`, que sobrevivió al borrado de quien invitó hasta que se cubrió.
 *
 * Enumera las columnas desde `information_schema` y las compara con las que el borrado cubre más las que se declaran
 * expresamente ajenas a una persona. Si falla, hay una columna nueva: si identifica a una persona, trátala en
 * `DeleteUserCommand` (borrado físico) o en la anonimización de `AuditTrailImpl` (auditoría, ADR-0014 D6), siémbrala en
 * [UserDeletionIntegrationTest] y escríbela en [COVERED]; si no la identifica, justifícalo en [NOT_PERSON].
 *
 * La lista de nombres es explícita —no un patrón sobre `email`— porque este esquema tiene `email_normalizado` además de
 * `email`, y `invitado_por` no acaba en `_id`.
 */
class IdentidadPersonColumnsErasureGuardIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `toda columna de persona del esquema identidad esta cubierta por el borrado`() {
        val found =
            jdbc
                .queryForList(
                    """
                    SELECT table_name || '.' || column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'identidad'
                      AND (column_name ~ '(alumno|persona|entrenador|usuario|autor|creador|actor|sujeto)_id$'
                           OR column_name IN ('email', 'email_normalizado', 'nombre', 'name', 'telefono', 'phone',
                                              'invitado_por', 'ip', 'user_agent'))
                    ORDER BY 1
                    """.trimIndent(),
                    String::class.java,
                ).toSet()

        withClue(
            "El esquema identidad tiene columnas de persona que el borrado no conoce (o el borrado cubre una que ya " +
                "no existe). Revisa DeleteUserCommand y AuditTrailImpl, y actualiza COVERED o NOT_PERSON:",
        ) {
            found shouldBe COVERED.keys + NOT_PERSON.keys
        }
    }

    private companion object {
        /** Columna de persona → qué la vacía. */
        val COVERED =
            mapOf(
                "usuario.email" to "UserRepository.deleteById (la fila entera)",
                "usuario.email_normalizado" to "UserRepository.deleteById (la fila entera)",
                "usuario.nombre" to "UserRepository.deleteById (la fila entera)",
                "invitacion.usuario_id" to "InvitationRepository.deleteByUserId",
                "invitacion.invitado_por" to "InvitationRepository.clearInviter (anulada, la invitación es de otro)",
                "magic_link.usuario_id" to "MagicLinkRepository.deleteByUserId",
                "password_historico.usuario_id" to "PasswordHistory.deleteByUserId",
                "consentimiento.usuario_id" to "ConsentRepository.deleteByUserId",
                "consentimiento.ip" to "ConsentRepository.deleteByUserId",
                "consentimiento.user_agent" to "ConsentRepository.deleteByUserId",
                "evento_auditoria.actor_id" to "anonimizada a NULL por AuditTrailImpl.ANONYMIZE_BY_ID_SQL",
                "evento_auditoria.sujeto_id" to "anonimizada a NULL por AuditTrailImpl.ANONYMIZE_BY_ID_SQL",
                "evento_auditoria.ip" to "truncada por AuditTrailImpl.ANONYMIZE_BY_ID_SQL",
            )

        /** Columnas que casan con la lista sin identificar a una persona, y por qué. */
        val NOT_PERSON =
            mapOf(
                "club.nombre" to "nombre del club, no el de una persona",
            )
    }
}
