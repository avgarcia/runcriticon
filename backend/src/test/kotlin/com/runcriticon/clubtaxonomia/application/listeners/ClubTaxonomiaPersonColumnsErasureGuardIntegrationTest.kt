package com.runcriticon.clubtaxonomia.application.listeners

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Vigila que ninguna columna de persona del esquema `club_taxonomia` quede fuera del borrado. Aquí `persona` guarda
 * nombre y email en claro y `RgpdArchTest`, que engancha en `@Entity`, no la ve: `persona`, `alumno_tag` y el resto de
 * proyecciones son JDBC puro. Los tests de flujo de [StudentDeletionEventFlowIntegrationTest] solo prueban las tablas
 * que ya conocen, así que una tabla nueva con un alumno o un entrenador no rompería nada y el derecho de supresión se
 * degradaría en silencio.
 *
 * Enumera las columnas desde `information_schema` y las compara con las que el borrado cubre más las que se declaran
 * expresamente ajenas a una persona. Si falla, hay una columna nueva: si identifica a una persona, añádela a
 * `PersonErasureJdbc` (borrado físico) o a la anonimización de `ClubTaxonomiaAuditTrailImpl` (auditoría, ADR-0014 D6),
 * siémbrala en el test de flujo y escríbela en [COVERED]; si no la identifica, justifícalo en [NOT_PERSON].
 */
class ClubTaxonomiaPersonColumnsErasureGuardIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `toda columna de persona del esquema club_taxonomia esta cubierta por el borrado`() {
        val found =
            jdbc
                .queryForList(
                    """
                    SELECT table_name || '.' || column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'club_taxonomia'
                      AND (column_name ~ '(alumno|persona|entrenador|usuario|autor|creador|actor|sujeto)_id$'
                           OR column_name IN ('email', 'nombre', 'name', 'telefono', 'phone'))
                    ORDER BY 1
                    """.trimIndent(),
                    String::class.java,
                ).toSet()

        withClue(
            "El esquema club_taxonomia tiene columnas de persona que el borrado no conoce (o el borrado cubre una " +
                "que ya no existe). Revisa PersonErasureJdbc y ClubTaxonomiaAuditTrailImpl, y actualiza COVERED o " +
                "NOT_PERSON:",
        ) {
            found shouldBe COVERED.keys + NOT_PERSON.keys
        }
    }

    private companion object {
        /** Columna de persona → qué la vacía (`DELETE` de `PersonErasureJdbc` salvo que se indique otro). */
        val COVERED =
            mapOf(
                "persona.nombre" to "DELETE_PERSON_SQL",
                "persona.email" to "DELETE_PERSON_SQL",
                "alumno_tag.alumno_id" to "DELETE_TAGS_SQL",
                "grupo_alumno_override.alumno_id" to "DELETE_GROUP_OVERRIDES_SQL",
                "grupo_entrenador.entrenador_id" to "DELETE_GROUP_COACH_ASSIGNMENTS_SQL",
                "consentimiento_alumno.alumno_id" to "DELETE_CONSENT_SQL",
                "evento_auditoria.actor_id" to "anonimizada a NULL por ANONYMIZE_SQL de ClubTaxonomiaAuditTrailImpl",
                "evento_auditoria.sujeto_id" to "anonimizada a NULL por ANONYMIZE_SQL de ClubTaxonomiaAuditTrailImpl",
            )

        /** Columnas que casan con el patrón sin identificar a una persona, y por qué. */
        val NOT_PERSON =
            mapOf(
                "tag_key.nombre" to "nombre de la clave de tag que define el club, no el de una persona",
                "tag_value.nombre" to "nombre del valor de tag que define el club, no el de una persona",
                "grupo.nombre" to "nombre del grupo de entrenamiento, no el de una persona",
            )
    }
}
