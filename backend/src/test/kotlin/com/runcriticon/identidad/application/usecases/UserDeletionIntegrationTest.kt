package com.runcriticon.identidad.application.usecases

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.security.EmailHasher
import com.runcriticon.identidad.application.usecases.account.DeleteUserCommand
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.persistence.entities.AuditEventEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.ConsentEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.InvitationEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.MagicLinkEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.PasswordHistoryEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.AuditEventEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.ConsentEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.MagicLinkEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.PasswordHistoryEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

/**
 * Borrado físico sobre Postgres real: las cuatro tablas con datos personales del usuario quedan sin rastro suyo y las
 * claves ajenas —que no cascadean— no lo impiden.
 *
 * Cubre también la anonimización de `identidad.evento_auditoria`: los asientos que mencionaban al sujeto suprimido
 * pierden sus identificadores pero siguen existiendo — es el rastro de auditoría, no un dato personal más a borrar.
 */
class UserDeletionIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var deleteUser: DeleteUserCommand

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

    @Autowired private lateinit var consentEntityRepository: ConsentEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

    @Autowired private lateinit var auditTrail: AuditTrail

    @Autowired private lateinit var emailHasher: EmailHasher

    @Autowired private lateinit var jdbc: JdbcTemplate

    /** Club canónico que siembra la migración; la clave ajena `usuario.club_id` exige que exista. */
    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

    @BeforeEach
    fun preparaElContexto() {
        magicLinkEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        passwordHistoryEntityRepository.deleteAll()
        consentEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        auditEventEntityRepository.deleteAll()
        // El aspecto de la malla anti-IDOR contrasta el clubId de @AuthScope(CLUB) contra el principal del contexto de
        // seguridad; estos tests invocan el caso de uso directamente, sin pasar por el login HTTP.
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `eliminar a un alumno borra sus datos personales de las cinco tablas`() {
        val alumno = sembrarAlumnoConSusDatos()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        userEntityRepository.findById(alumno).isPresent shouldBe false
        contarPor("invitacion", alumno) shouldBe 0
        contarPor("magic_link", alumno) shouldBe 0
        contarPor("password_historico", alumno) shouldBe 0
        contarPor("consentimiento", alumno) shouldBe 0
    }

    @Test
    fun `el borrado deja asiento de auditoria de la supresion sin el id del sujeto`() {
        val alumno = sembrarAlumnoConSusDatos()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val asientos = auditEventEntityRepository.findAll().filter { it.type == AuditEventType.CUENTA_ELIMINADA.name }
        asientos shouldHaveSize 1
        asientos.single().subjectId shouldBe null
        asientos.single().actorId shouldBe admin.userId
    }

    /**
     * Cierra la deuda RGPD abierta a propósito al implementar el borrado: los asientos previos que mencionaban al
     * sujeto suprimido quedan anonimizados, pero **siguen existiendo** — es el rastro de auditoría que debe sobrevivir
     * a la persona que menciona, no un borrado.
     */
    @Test
    fun `la auditoria previa del sujeto suprimido queda anonimizada pero no se borra`() {
        val alumno = sembrarAlumnoConSusDatos()
        val asientoPrevio = sembrarAsientoDeAuditoria(subjectId = alumno)

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val tras = auditEventEntityRepository.findById(asientoPrevio).get()
        tras.subjectId shouldBe null
        tras.actorId shouldBe null
    }

    @Test
    fun `la auditoria de un tercero no se toca al suprimir a otra persona`() {
        val alumno = sembrarAlumnoConSusDatos()
        val terceroId = UuidCreator.getTimeOrderedEpoch()
        val asientoTercero = sembrarAsientoDeAuditoria(subjectId = terceroId, actorId = admin.userId)

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val tras = auditEventEntityRepository.findById(asientoTercero).get()
        tras.subjectId shouldBe terceroId
        tras.actorId shouldBe admin.userId
    }

    @Test
    fun `un asiento anonimo de rate-limiting con el email_hash del sujeto queda sin email_hash y con la ip truncada`() {
        val alumno = sembrarAlumnoConSusDatos()
        val emailAlumno = userEntityRepository.findById(alumno).get().email
        val asiento =
            sembrarAsientoDeAuditoria(
                type = AuditEventType.MAGIC_LINK_RATE_LIMITED,
                metadata = mapOf("email_hash" to emailHasher.hash(emailAlumno), "ip" to "203.0.113.55"),
            )

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val tras = auditEventEntityRepository.findById(asiento).get()
        tras.metadata?.get("email_hash") shouldBe null
        tras.metadata?.get("ip") shouldBe "203.0.113.0/24"
    }

    /**
     * La columna `ip` (INET) nunca la escribe el código actual — las IPs reales viven en `metadata`—, pero el
     * truncado también cubre esa columna por si algún día se usa. Sin esta siembra directa por JDBC, esa rama del
     * `UPDATE` quedaría sin cobertura.
     */
    @Test
    fun `la columna ip (INET), si estuviera poblada, tambien queda truncada`() {
        val alumno = sembrarAlumnoConSusDatos()
        val asiento = sembrarAsientoDeAuditoria(subjectId = alumno)
        jdbc.update("UPDATE identidad.evento_auditoria SET ip = ?::inet WHERE id = ?", "198.51.100.7", asiento)

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val ipTruncada =
            jdbc.queryForObject(
                "SELECT host(ip) FROM identidad.evento_auditoria WHERE id = ?",
                String::class.java,
                asiento,
            )
        ipTruncada shouldBe "198.51.100.0"
    }

    @Test
    fun `anonimizar dos veces es idempotente`() {
        val alumno = sembrarAlumnoConSusDatos()
        sembrarAsientoDeAuditoria(subjectId = alumno)
        val email = Email.of(userEntityRepository.findById(alumno).get().email)

        auditTrail.anonymize(alumno, email)
        val segundaPasada = auditTrail.anonymize(alumno, email)

        segundaPasada shouldBe 0
    }

    @Test
    fun `repetir el borrado devuelve NotFound porque el usuario ya no existe`() {
        val alumno = sembrarAlumnoConSusDatos()
        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeLeft()
    }

    /**
     * La consulta que sostiene esta regla solo se ejercita aquí: los tests unitarios stubean su respuesta, así que
     * verifican el `<= 1` pero no que el filtro por estado haga lo que dice. Fallar en la dirección permisiva deja al
     * club sin ningún administrador capaz de entrar, y eso no tiene vuelta atrás.
     */
    @Test
    fun `no se puede eliminar al unico admin activo aunque quede otro desactivado`() {
        val otroAdmin = sembrarUsuario(Role.ADMIN)
        val adminDesactivado = sembrarUsuario(Role.ADMIN, estado = "DESACTIVADO")

        deleteUser.execute(admin, UserId.of(otroAdmin)).shouldBeLeft()

        userEntityRepository.findById(otroAdmin).isPresent shouldBe true
        userEntityRepository.findById(adminDesactivado).isPresent shouldBe true
    }

    @Test
    fun `con dos admins activos se puede eliminar a uno`() {
        val unAdmin = sembrarUsuario(Role.ADMIN)
        sembrarUsuario(Role.ADMIN)

        deleteUser.execute(admin, UserId.of(unAdmin)).shouldBeRight()

        userEntityRepository.findById(unAdmin).isPresent shouldBe false
    }

    @Test
    fun `un usuario sin datos asociados tambien se elimina`() {
        val alumno = sembrarUsuario(Role.ALUMNO)

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        userEntityRepository.findById(alumno).isPresent shouldBe false
    }

    private fun sembrarAlumnoConSusDatos(): UUID {
        val alumno = sembrarUsuario(Role.ALUMNO)
        val now = Instant.now()
        invitationEntityRepository.save(
            InvitationEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                userId = alumno,
                clubId = clubId,
                tokenHash = "hash-invitacion",
                issuedAt = now,
                expiresAt = now.plusSeconds(SIETE_DIAS),
                consumedAt = null,
            ),
        )
        magicLinkEntityRepository.save(
            MagicLinkEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                userId = alumno,
                clubId = clubId,
                tokenHash = "hash-magic-link",
                purpose = "LOGIN",
                issuedAt = now,
                expiresAt = now.plusSeconds(QUINCE_MINUTOS),
                consumedAt = null,
            ),
        )
        passwordHistoryEntityRepository.save(
            PasswordHistoryEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                userId = alumno,
                clubId = clubId,
                passwordHash = "hash-password",
                createdAt = now,
            ),
        )
        sembrarConsentimiento(alumno, now)
        return alumno
    }

    private fun sembrarConsentimiento(
        alumno: UUID,
        now: Instant,
    ) {
        consentEntityRepository.save(
            ConsentEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                userId = alumno,
                clubId = clubId,
                textVersion = "v2026-08-25",
                grantedAt = now,
                revokedAt = null,
                ip = "203.0.113.10",
                userAgent = "test-agent",
            ),
        )
    }

    private fun sembrarUsuario(
        role: Role,
        estado: String = "ACTIVO",
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        val now = Instant.now()
        userEntityRepository.save(
            UserEntity(
                id = id,
                clubId = clubId,
                email = "borrable-$id@club.test",
                normalizedEmail = "borrable-$id@club.test",
                name = "Persona Borrable",
                role = role.name,
                passwordHash = "hash",
                status = estado,
                createdAt = now,
                modifiedAt = now,
            ),
        )
        return id
    }

    private fun sembrarAsientoDeAuditoria(
        type: AuditEventType = AuditEventType.SESION_REVOCADA,
        actorId: UUID? = null,
        subjectId: UUID? = null,
        metadata: Map<String, String>? = null,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        auditEventEntityRepository.save(
            AuditEventEntity(
                id = id,
                type = type.name,
                actorId = actorId,
                subjectId = subjectId,
                occurredAt = Instant.now(),
                metadata = metadata,
            ),
        )
        return id
    }

    private fun contarPor(
        tabla: String,
        userId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM identidad.$tabla WHERE usuario_id = ?",
            Int::class.java,
            userId,
        ) ?: 0

    private companion object {
        const val SIETE_DIAS = 604_800L
        const val QUINCE_MINUTOS = 900L
    }
}
