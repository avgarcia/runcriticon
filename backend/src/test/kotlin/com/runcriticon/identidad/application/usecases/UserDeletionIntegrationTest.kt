package com.runcriticon.identidad.application.usecases

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.application.usecases.account.DeleteUserCommand
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.persistence.entities.InvitationEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.MagicLinkEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.PasswordHistoryEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.AuditEventEntityRepository
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
import io.kotest.matchers.nulls.shouldNotBeNull
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
 * Documenta también lo que **no** se borra: el asiento de auditoría sobrevive con el id del sujeto. Es una limitación
 * conocida, así que el test la fija para que un cambio futuro de ese comportamiento sea deliberado y no accidental.
 */
class UserDeletionIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var deleteUser: DeleteUserCommand

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var magicLinkEntityRepository: MagicLinkEntityRepository

    @Autowired private lateinit var passwordHistoryEntityRepository: PasswordHistoryEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    /** Club canónico que siembra la migración; la clave ajena `usuario.club_id` exige que exista. */
    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

    @BeforeEach
    fun preparaElContexto() {
        magicLinkEntityRepository.deleteAll()
        invitationEntityRepository.deleteAll()
        passwordHistoryEntityRepository.deleteAll()
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
    fun `eliminar a un alumno borra sus datos personales de las cuatro tablas`() {
        val alumno = sembrarAlumnoConSusDatos()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        userEntityRepository.findById(alumno).isPresent shouldBe false
        contarPor("invitacion", alumno) shouldBe 0
        contarPor("magic_link", alumno) shouldBe 0
        contarPor("password_historico", alumno) shouldBe 0
    }

    @Test
    fun `el borrado deja asiento de auditoria de la supresion`() {
        val alumno = sembrarAlumnoConSusDatos()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        val asientos = auditEventEntityRepository.findAll().filter { it.type == AuditEventType.CUENTA_ELIMINADA.name }
        asientos shouldHaveSize 1
        asientos.single().subjectId shouldBe alumno
        asientos.single().actorId shouldBe admin.userId
    }

    /**
     * Fija la limitación conocida: la auditoría **no** se anonimiza todavía, así que tras el borrado sobrevive el id
     * del sujeto suprimido. Cuando se implemente la anonimización, este test tendrá que cambiar — y ese cambio debe
     * ser una decisión explícita, no un efecto colateral.
     */
    @Test
    fun `la auditoria conserva hoy el identificador del sujeto suprimido`() {
        val alumno = sembrarAlumnoConSusDatos()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        auditEventEntityRepository
            .findAll()
            .firstOrNull { it.subjectId == alumno }
            .shouldNotBeNull()
    }

    @Test
    fun `repetir el borrado devuelve NotFound porque el usuario ya no existe`() {
        val alumno = sembrarAlumnoConSusDatos()
        deleteUser.execute(admin, UserId.of(alumno)).shouldBeRight()

        deleteUser.execute(admin, UserId.of(alumno)).shouldBeLeft()
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
        return alumno
    }

    private fun sembrarUsuario(role: Role): UUID {
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
                status = "ACTIVO",
                createdAt = now,
                modifiedAt = now,
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
