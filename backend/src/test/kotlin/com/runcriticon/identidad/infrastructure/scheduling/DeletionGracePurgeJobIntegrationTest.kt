package com.runcriticon.identidad.infrastructure.scheduling

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.identidad.application.ports.inbound.DeletionGraceExpired
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.Await
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Contrato de la purga de la categoría 1 contra Postgres real (ADR-0017 D7 y D9), por el camino real: el job publica
 * un evento interno por cuenta vencida, el outbox lo entrega tras el commit y el listener suprime la cuenta.
 *
 * Siembra cuentas en `BAJA` a 29 y a 31 días, nunca justo a 30, y una cuenta fuera de `BAJA` con fechas antiguas. Las
 * cuentas van al club que siembra la migración (`usuario` tiene clave ajena a `club`); cada caso usa las suyas y filtra
 * por su id, sin limpiar entre tests: la entrega es asíncrona y un `@BeforeEach` que borrara competiría con las
 * entregas del caso anterior.
 */
class DeletionGracePurgeJobIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var job: DeletionGracePurgeJob

    @Autowired private lateinit var events: ApplicationEventPublisher

    @Autowired private lateinit var transactions: TransactionTemplate

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `suprime la cuenta con la gracia vencida y publica su baja sin actor`() {
        val club = CLUB_ID
        val vencida = sembrar(club, Role.ALUMNO, estado = "BAJA", antiguedad = TREINTA_Y_UN_DIAS)

        job.purge()

        await("la cuenta vencida no se suprimió") { !existe(vencida) }
        bajasPublicadas(vencida, AlumnoEliminado::class.java.name) shouldBe 1
        bajasConActor(vencida) shouldBe 0
    }

    @Test
    fun `conserva la cuenta en baja dentro de la gracia y la que no esta en baja`() {
        val club = CLUB_ID
        val dentroDeLaGracia = sembrar(club, Role.ALUMNO, estado = "BAJA", antiguedad = VEINTINUEVE_DIAS)
        val activaAntigua = sembrar(club, Role.ALUMNO, estado = "ACTIVO", antiguedad = UN_ANO)
        // Testigo: cuando desaparece, el job ya publicó y el listener ya procesó las cuentas de este club.
        val vencida = sembrar(club, Role.ALUMNO, estado = "BAJA", antiguedad = TREINTA_Y_UN_DIAS)

        job.purge()

        await("la cuenta vencida no se suprimió") { !existe(vencida) }
        Await.holds(Duration.ofMillis(SETTLE_MILLIS)) {
            existe(dentroDeLaGracia) shouldBe true
            existe(activaAntigua) shouldBe true
            bajasPublicadas(dentroDeLaGracia, AlumnoEliminado::class.java.name) shouldBe 0
            // El filtro del job, por sí solo: ni siquiera envía a suprimir las cuentas que no han vencido.
            enviosASuprimir(dentroDeLaGracia) shouldBe 0
            enviosASuprimir(activaAntigua) shouldBe 0
            enviosASuprimir(vencida) shouldBe 1
        }
    }

    /** La comprobación del listener, por sí sola: una entrega para una cuenta aún dentro de la gracia no la suprime. */
    @Test
    fun `una entrega para una cuenta dentro de la gracia no la suprime`() {
        val club = CLUB_ID
        val dentroDeLaGracia = sembrar(club, Role.ALUMNO, estado = "BAJA", antiguedad = VEINTINUEVE_DIAS)
        val evento = DeletionGraceExpired(userId = dentroDeLaGracia, clubId = club, role = Role.ALUMNO)

        publicar(evento)

        await("el listener no procesó el evento") { procesado(evento.eventId) }
        existe(dentroDeLaGracia) shouldBe true
        bajasPublicadas(dentroDeLaGracia, AlumnoEliminado::class.java.name) shouldBe 0
    }

    /** Lo que pasa cuando dos instancias ejecutan el job a la vez: cada una envía la misma cuenta a suprimir. */
    @Test
    fun `dos entregas para la misma cuenta la suprimen una sola vez`() {
        val club = CLUB_ID
        val vencida = sembrar(club, Role.ENTRENADOR, estado = "BAJA", antiguedad = TREINTA_Y_UN_DIAS)

        publicar(DeletionGraceExpired(userId = vencida, clubId = club, role = Role.ENTRENADOR))
        publicar(DeletionGraceExpired(userId = vencida, clubId = club, role = Role.ENTRENADOR))

        await("la cuenta vencida no se suprimió") { !existe(vencida) }
        Await.holds(Duration.ofMillis(SETTLE_MILLIS)) {
            bajasPublicadas(vencida, EntrenadorEliminado::class.java.name) shouldBe 1
        }
    }

    /** La entrega llega cuando el admin ya canceló la baja: la cuenta volvió a su estado y no debe suprimirse. */
    @Test
    fun `si la baja se cancelo antes de la entrega no suprime la cuenta`() {
        val club = CLUB_ID
        val cancelada = sembrar(club, Role.ALUMNO, estado = "ACTIVO", antiguedad = TREINTA_Y_UN_DIAS)
        val evento = DeletionGraceExpired(userId = cancelada, clubId = club, role = Role.ALUMNO)

        publicar(evento)

        await("el listener no procesó el evento") { procesado(evento.eventId) }
        existe(cancelada) shouldBe true
        bajasPublicadas(cancelada, AlumnoEliminado::class.java.name) shouldBe 0
    }

    private fun publicar(evento: DeletionGraceExpired) {
        transactions.executeWithoutResult { events.publishEvent(evento) }
    }

    /** Siembra una cuenta creada hace [antiguedad]; si está en `BAJA`, la baja se marcó también entonces. */
    private fun sembrar(
        club: UUID,
        rol: Role,
        estado: String,
        antiguedad: Duration,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        val email = "baja-$id@runcriticon.local"
        val momento = Timestamp.from(Instant.now().minus(antiguedad))
        val enBaja = estado == "BAJA"
        jdbc.update(
            "INSERT INTO identidad.usuario (id, club_id, email, email_normalizado, nombre, rol, estado, " +
                "creado_en, modificado_en, baja_en, estado_previo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id,
            club,
            email,
            email,
            "Persona en baja",
            rol.name,
            estado,
            momento,
            momento,
            if (enBaja) momento else null,
            if (enBaja) "ACTIVO" else null,
        )
        return id
    }

    private fun existe(id: UUID): Boolean =
        jdbc.queryForObject("SELECT count(*) FROM identidad.usuario WHERE id = ?", Int::class.java, id) == 1

    private fun procesado(eventId: UUID): Boolean =
        jdbc.queryForObject(
            "SELECT count(*) FROM identidad.evento_procesado WHERE listener = 'DeletionGraceExpiredListener' " +
                "AND event_id = ?",
            Int::class.java,
            eventId,
        ) == 1

    /** Bajas distintas publicadas para la cuenta: el outbox guarda una fila por listener, todas con el mismo evento. */
    private fun bajasPublicadas(
        id: UUID,
        tipo: String,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(DISTINCT serialized_event::jsonb ->> 'eventId') FROM event_publication " +
                "WHERE event_type = ? AND serialized_event::jsonb ->> 'aggregateId' = ?",
            Int::class.java,
            tipo,
            id.toString(),
        )!!

    /** Eventos internos que el job publicó para enviar la cuenta a suprimir (una fila por listener, aquí solo uno). */
    private fun enviosASuprimir(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE event_type = ? " +
                "AND serialized_event::jsonb ->> 'userId' = ?",
            Int::class.java,
            DeletionGraceExpired::class.java.name,
            id.toString(),
        )!!

    private fun bajasConActor(id: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE serialized_event::jsonb ->> 'aggregateId' = ? " +
                "AND serialized_event::jsonb ->> 'actorId' IS NOT NULL",
            Int::class.java,
            id.toString(),
        )!!

    private fun await(
        failure: String,
        probe: () -> Boolean,
    ) = Await.until(failure, probe = probe)

    private companion object {
        val CLUB_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val VEINTINUEVE_DIAS: Duration = Duration.ofDays(29)
        val TREINTA_Y_UN_DIAS: Duration = Duration.ofDays(31)
        val UN_ANO: Duration = Duration.ofDays(365)
        const val SETTLE_MILLIS = 800L
    }
}
