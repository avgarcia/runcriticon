package com.runcriticon.planificacion.infrastructure.persistence.repositories

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.planificacion.application.ports.outbound.persistence.WeeklyPlanRepository
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.Pace
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.planificacion.domain.PlanId
import com.runcriticon.planificacion.domain.RaceDistance
import com.runcriticon.planificacion.domain.Session
import com.runcriticon.planificacion.domain.SessionId
import com.runcriticon.planificacion.domain.SessionType
import com.runcriticon.planificacion.domain.SessionVolume
import com.runcriticon.planificacion.domain.WeeklyPlan
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * `insertSession`/`updateSession`/`deleteSession` (LAL-24) contra Postgres real: es lo único que verifica que el
 * filtro anti-IDOR de la query realmente no escribe fuera de club, y que `sesion_plan_dia_uk` (`UNIQUE (plan_id,
 * dia)`) muerde de verdad — ninguno de los dos se puede comprobar con el doble en memoria de los tests de caso de
 * uso (`InMemoryWeeklyPlanRepository` no repite esta validación a propósito).
 */
class WeeklyPlanRepositoryJdbcIntegrationTest : IntegrationTestBase() {
    @Autowired private lateinit var plans: WeeklyPlanRepository

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var transactions: TransactionTemplate

    private val club = ClubId.of(UuidCreator.getTimeOrderedEpoch())
    private val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
    private val monday = LocalDate.of(2026, 8, 17)

    @BeforeEach
    fun prepara() {
        autenticar(admin)
    }

    @AfterEach
    fun limpiaElContexto() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `insertSession persiste tipo, volumen, ritmo relativo y notas`() {
        val plan = crearPlan()
        val session =
            Session
                .create(
                    day = monday.plusDays(1),
                    type = SessionType.SERIES,
                    volume = SessionVolume.Distance(meters = 4000),
                    pace = Pace.Relativo(reference = RaceDistance.TEN_K, deltaSecondsPerKm = 10),
                    notes = "8x400m",
                ).shouldBeRight()

        enTransaccion { plans.insertSession(club, plan.id, session) }

        val loaded = enTransaccion { plans.findById(club, plan.id) }.shouldNotBeNull()
        loaded.sessions shouldBe listOf(session)
    }

    @Test
    fun `insertSession con ritmo absoluto y volumen en tiempo`() {
        val plan = crearPlan()
        val session =
            Session
                .create(
                    day = monday.plusDays(2),
                    type = SessionType.TEMPO,
                    volume = SessionVolume.Duration(minutes = 30),
                    pace = Pace.Absoluto(secondsPerKm = 270),
                ).shouldBeRight()

        enTransaccion { plans.insertSession(club, plan.id, session) }

        enTransaccion { plans.findById(club, plan.id) }.shouldNotBeNull().sessions shouldBe listOf(session)
    }

    /**
     * Anti-IDOR, mismo criterio que `GroupRepositoryIntegrationTest`: la llamada usa siempre el club del propio
     * principal autenticado (`club`), apuntando a un plan que en realidad pertenece a `otroClub` — así se verifica
     * la guarda `p.club_id = ?` de la query, no `AuthScopeEnforcementAspect` (que saltaría antes si el parámetro
     * `clubId` no coincidiera con el principal).
     */
    @Test
    fun `insertSession en un plan de otro club no escribe nada`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val planAjeno = sembrarPlanCrudo(otroClub)
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()

        enTransaccion { plans.insertSession(club, planAjeno, session) }

        contarSesiones(planAjeno) shouldBe 0
    }

    @Test
    fun `dos sesiones el mismo dia violan la constraint UNIQUE`() {
        val plan = crearPlan()
        val first = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        val second = Session.create(day = monday.plusDays(1), type = SessionType.SERIES).shouldBeRight()
        enTransaccion { plans.insertSession(club, plan.id, first) }

        shouldThrow<DataIntegrityViolationException> {
            enTransaccion { plans.insertSession(club, plan.id, second) }
        }
    }

    @Test
    fun `updateSession sustituye tipo y notas sin tocar el dia`() {
        val plan = crearPlan()
        val original = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        enTransaccion { plans.insertSession(club, plan.id, original) }
        val edited =
            Session
                .create(id = original.id, day = LocalDate.of(1999, 1, 1), type = SessionType.SERIES, notes = "editada")
                .shouldBeRight()

        enTransaccion { plans.updateSession(club, plan.id, edited) }

        val loaded = enTransaccion { plans.findById(club, plan.id) }.shouldNotBeNull().sessions.single()
        loaded.day shouldBe original.day
        loaded.type shouldBe SessionType.SERIES
        loaded.notes shouldBe "editada"
    }

    @Test
    fun `updateSession en un plan de otro club no cambia nada`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val planAjeno = sembrarPlanCrudo(otroClub)
        val original = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        sembrarSesionCruda(planAjeno, original)
        val edited = Session.create(id = original.id, day = original.day, type = SessionType.SERIES).shouldBeRight()

        enTransaccion { plans.updateSession(club, planAjeno, edited) }

        tipoGuardado(original.id) shouldBe SessionType.RODAJE.name
    }

    @Test
    fun `deleteSession elimina la fila`() {
        val plan = crearPlan()
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        enTransaccion { plans.insertSession(club, plan.id, session) }

        enTransaccion { plans.deleteSession(club, plan.id, session.id) }

        enTransaccion { plans.findById(club, plan.id) }.shouldNotBeNull().sessions shouldBe emptyList()
    }

    @Test
    fun `deleteSession en un plan de otro club no borra nada`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val planAjeno = sembrarPlanCrudo(otroClub)
        val session = Session.create(day = monday.plusDays(1), type = SessionType.RODAJE).shouldBeRight()
        sembrarSesionCruda(planAjeno, session)

        enTransaccion { plans.deleteSession(club, planAjeno, session.id) }

        contarSesiones(planAjeno) shouldBe 1
    }

    @Test
    fun `un plan de otro club no se encuentra`() {
        val otroClub = ClubId.of(UuidCreator.getTimeOrderedEpoch())
        val planAjeno = sembrarPlanCrudo(otroClub)

        // Sin pasar por `enTransaccion`: su `!!` confundiría el `null` esperado (no es de este club) con un
        // fallo del test, mismo motivo que `GroupRepositoryIntegrationTest.detalle`.
        transactions.execute { plans.findById(club, planAjeno) }.shouldBeNull()
    }

    private fun crearPlan(): WeeklyPlan {
        val plan =
            WeeklyPlan
                .createDraft(club, GroupId.of(UuidCreator.getTimeOrderedEpoch()), PersonId.of(admin.userId), monday)
                .shouldBeRight()
        enTransaccion { plans.save(club, plan) }
        return plan
    }

    /** Siembra un plan con SQL crudo bajo [otroClub], sin pasar por el repositorio (evita el aspecto `@AuthScope`). */
    private fun sembrarPlanCrudo(otroClub: ClubId): PlanId {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO planificacion.plan_semanal (id, club_id, grupo_id, entrenador_id, semana, estado)
            VALUES (?, ?, ?, ?, ?, 'BORRADOR')
            """.trimIndent(),
            id,
            otroClub.value,
            UuidCreator.getTimeOrderedEpoch(),
            UuidCreator.getTimeOrderedEpoch(),
            monday,
        )
        return PlanId.of(id)
    }

    /** Igual que `INSERT_SESSION_SQL` de `WeeklyPlanRepositoryJdbc`, con SQL crudo para no pasar por el aspecto. */
    private fun sembrarSesionCruda(
        planId: PlanId,
        session: Session,
    ) {
        jdbc.update(
            "INSERT INTO planificacion.sesion (id, plan_id, dia, tipo) VALUES (?, ?, ?, ?)",
            session.id.value,
            planId.value,
            session.day,
            session.type.name,
        )
    }

    private fun contarSesiones(planId: PlanId): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM planificacion.sesion WHERE plan_id = ?",
            Int::class.java,
            planId.value,
        ) ?: 0

    private fun tipoGuardado(sessionId: SessionId): String =
        jdbc.queryForObject(
            "SELECT tipo FROM planificacion.sesion WHERE id = ?",
            String::class.java,
            sessionId.value,
        )!!

    private fun <T> enTransaccion(action: () -> T): T = transactions.execute { action() }!!

    private fun autenticar(principal: Principal) {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken(
                principal,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}")),
            )
        SecurityContextHolder.setContext(context)
    }
}
