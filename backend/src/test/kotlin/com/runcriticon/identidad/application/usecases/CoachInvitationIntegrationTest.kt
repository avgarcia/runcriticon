package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ports.EmailSender
import com.runcriticon.identidad.application.ports.InvitationEmailRequested
import com.runcriticon.identidad.application.ports.MagicLinkEmailRequested
import com.runcriticon.identidad.application.ports.PasswordResetEmailRequested
import com.runcriticon.identidad.application.ports.TokenHasher
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.identidad.infrastructure.persistence.AuditEventEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.InvitationEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tests de integración críticos del alta con invitación (LAL-50, subtarea de LAL-7). Cierran la
 * tabla de tests de ADR-0003 (D4, D15) ejercitando el flujo completo sobre Postgres real
 * (Testcontainers): autorización + persistencia + token hasheado + rotación en reinvitación +
 * asiento de auditoría + entrega del email vía outbox de Spring Modulith.
 *
 * El adaptador de email real (`PostmarkEmailSender`) se sustituye por [FakeEmailSender] (`@Primary`)
 * para no contactar con Postmark; el `@ApplicationModuleListener` entrega sobre él tras el commit.
 *
 * Cruces: ADR-0003 (D4 invitación de un solo uso/rotación, D13 HMAC, D15 auditoría), ADR-0009
 * (RBAC), ADR-0010 (pirámide/Testcontainers).
 */
@SpringBootTest
@Testcontainers
@Import(FakeEmailConfig::class)
class CoachInvitationIntegrationTest {
    @Autowired private lateinit var inviteCoach: InviteCoach

    @Autowired private lateinit var resendInvitation: ResendInvitation

    @Autowired private lateinit var userEntityRepository: UserEntityRepository

    @Autowired private lateinit var invitationEntityRepository: InvitationEntityRepository

    @Autowired private lateinit var auditEventEntityRepository: AuditEventEntityRepository

    @Autowired private lateinit var tokenHasher: TokenHasher

    @Autowired private lateinit var emailSender: FakeEmailSender

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configure(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // El HMAC del token (TokenHasherImpl) exige clave no vacía; en CI TOKEN_HMAC_SECRET
            // no está definida y SecretKeySpec rechaza una clave vacía ("Empty key").
            registry.add("runcriticon.security.token-hmac-secret") { "test-token-hmac-secret-0123456789" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }

    private val clubId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)

    @BeforeEach
    fun limpiar() {
        // Orden: primero invitaciones (FK a usuario), luego usuarios; auditoría es independiente.
        invitationEntityRepository.deleteAll()
        userEntityRepository.deleteAll()
        auditEventEntityRepository.deleteAll()
        emailSender.sent.clear()
        // AuthScopeEnforcementAspect (ADR-0009 D11) verifica el clubId de @AuthScope(CLUB) contra el
        // principal de SecurityContextHolder; estos tests invocan los casos de uso directamente (sin
        // pasar por login HTTP), así que hay que sembrar el contexto igual que haría SecuritySessionManager.
        val authentication =
            UsernamePasswordAuthenticationToken(admin, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
    }

    @AfterEach
    fun limpiarContextoDeSeguridad() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `email ya existente en el club rechaza el alta sin duplicar fila (CA 2)`() {
        inviteCoach.execute(admin, "Ana Perez", "ana@club.test").shouldBeRight()

        inviteCoach
            .execute(admin, "Ana Duplicada", "ana@club.test")
            .shouldBeLeft()
            .shouldBeInstanceOf<IdentidadError.Conflict>()

        userEntityRepository.count() shouldBe 1
        invitationEntityRepository.count() shouldBe 1
    }

    @Test
    fun `el token se persiste hasheado (HMAC) y nunca en claro (D13)`() {
        val userId = inviteCoach.execute(admin, "Beto", "beto@club.test").shouldBeRight()

        val rawToken = awaitInvitationFor("beto@club.test").rawToken.value

        val stored = invitationEntityRepository.findTopByUserIdOrderByIssuedAtDesc(userId.value).shouldNotBeNull()
        stored.tokenHash shouldNotBe rawToken
        stored.tokenHash shouldBe tokenHasher.hash(RawToken(rawToken)).value
    }

    @Test
    fun `la reinvitacion emite token nuevo e invalida el anterior aunque no haya caducado (CA 3, D4)`() {
        val coachId = inviteCoach.execute(admin, "Caro", "caro@club.test").shouldBeRight()
        val original = invitationEntityRepository.findTopByUserIdOrderByIssuedAtDesc(coachId.value).shouldNotBeNull()
        original.consumedAt.shouldBeNull()

        resendInvitation.execute(admin, coachId).shouldBeRight()

        val invitations = invitationEntityRepository.findAll().filter { it.userId == coachId.value }
        invitations.size shouldBe 2

        // La invitación abierta es la nueva; la cerrada (consumedAt != null) es la anterior rotada.
        val fresh = invitations.single { it.consumedAt == null }
        val invalidated = invitations.single { it.consumedAt != null }
        invalidated.id shouldBe original.id
        fresh.tokenHash shouldNotBe invalidated.tokenHash
    }

    @Test
    fun `un rol distinto de ADMIN no puede invitar y no produce efectos (ADR-0009)`() {
        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            val actor = Principal(userId = UUID.randomUUID(), clubId = clubId, role = role)
            inviteCoach.execute(actor, "Intruso", "intruso@club.test").shouldBeLeft(IdentidadError.Forbidden)
        }

        userEntityRepository.count() shouldBe 0
        invitationEntityRepository.count() shouldBe 0
        auditEventEntityRepository.count() shouldBe 0
    }

    @Test
    fun `el alta emite asiento INVITACION_EMITIDA con actor y sujeto, y un alta fallida no deja rastro (D15)`() {
        val userId = inviteCoach.execute(admin, "Dani", "dani@club.test").shouldBeRight()

        val audits = auditEventEntityRepository.findAll()
        audits.size shouldBe 1
        val entry = audits.first()
        entry.type shouldBe AuditEventType.INVITACION_EMITIDA.name
        entry.actorId shouldBe admin.userId
        entry.subjectId shouldBe userId.value

        // Alta fallida (email duplicado): se rechaza y NO añade asiento de éxito.
        inviteCoach.execute(admin, "Dani Duplicado", "dani@club.test").shouldBeLeft()
        auditEventEntityRepository.count() shouldBe 1
    }

    @Test
    fun `el email de invitacion se entrega al adaptador via outbox y caduca a 7 dias (D4)`() {
        val userId = inviteCoach.execute(admin, "Eva", "eva@club.test").shouldBeRight()

        // Entregado al adaptador de email (mock) a través del @ApplicationModuleListener del outbox.
        val email = awaitInvitationFor("eva@club.test")
        email.recipientName shouldBe "Eva"
        email.rawToken.value.shouldNotBeBlank()

        // Caducidad del enlace de invitación: 7 días desde la emisión (ADR-0003 D4).
        val stored = invitationEntityRepository.findTopByUserIdOrderByIssuedAtDesc(userId.value).shouldNotBeNull()
        Duration.between(stored.issuedAt, stored.expiresAt) shouldBe Duration.ofDays(7)
    }

    /** Espera (poll corto) a que el adaptador de email reciba la invitación del destinatario indicado. */
    private fun awaitInvitationFor(email: String): InvitationEmailRequested {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(5).toNanos()
        while (System.nanoTime() < deadlineNanos) {
            emailSender.sent.firstOrNull { it.to.value == email }?.let { return it }
            Thread.sleep(25)
        }
        throw AssertionError("No se entregó el email de invitación para $email en 5 s")
    }
}

/**
 * Sustituye el adaptador de email real por un doble capturador (`@Primary`), de modo que el
 * `@ApplicationModuleListener` del outbox entregue sobre él sin contactar con Postmark.
 */
@TestConfiguration
class FakeEmailConfig {
    @Bean
    @Primary
    fun fakeEmailSender(): FakeEmailSender = FakeEmailSender()
}

/** Doble de [EmailSender] que acumula las invitaciones/magic links/reseteos entregados (thread-safe). */
class FakeEmailSender : EmailSender {
    val sent: MutableList<InvitationEmailRequested> = CopyOnWriteArrayList()
    val magicLinksSent: MutableList<MagicLinkEmailRequested> = CopyOnWriteArrayList()
    val passwordResetsSent: MutableList<PasswordResetEmailRequested> = CopyOnWriteArrayList()

    override fun sendInvitation(request: InvitationEmailRequested) {
        sent.add(request)
    }

    override fun sendMagicLink(request: MagicLinkEmailRequested) {
        magicLinksSent.add(request)
    }

    override fun sendPasswordReset(request: PasswordResetEmailRequested) {
        passwordResetsSent.add(request)
    }
}
