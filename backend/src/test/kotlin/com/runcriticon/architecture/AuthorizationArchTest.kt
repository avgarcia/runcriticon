package com.runcriticon.architecture

import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Guards de la malla de autorización (ADR-0009 D6, D10, D11, D13). Ya muerden: hay repositorios,
 * casos de uso y handlers reales desde H0/H1 (LAL-59 cerró la divergencia doc↔código detectada en la
 * revisión 2026-07-03).
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class AuthorizationArchTest {
    @ArchTest
    val `cada metodo publico de un @Repository declara @AuthScope o @NoAuthScope` =
        methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Repository::class.java)
            .and()
            .arePublic()
            .should()
            .beAnnotatedWith(AuthScope::class.java)
            .orShould()
            .beAnnotatedWith(NoAuthScope::class.java)

    @ArchTest
    val `no se accede a SecurityContextHolder fuera del nucleo de autorizacion` =
        noClasses()
            .that()
            .resideOutsideOfPackage("..shared.autorizacion..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("SecurityContextHolder")

    @ArchTest
    val `nadie usa HttpSession directa (la app es stateless)` =
        noClasses()
            .should()
            .dependOnClassesThat()
            .haveSimpleName("HttpSession")

    /**
     * Todo `@ApplicationService` consulta [AuthorizationMatrix] (en la propia clase o en una clase
     * anidada/anónima — una lambda no-inline compilaría ahí) o se declara exento a nivel de clase con
     * [NoAuthRequired] o [AuthenticatedOnly] (ADR-0009 D13).
     */
    @ArchTest
    val `todo @ApplicationService consulta la matriz de autorizacion o se declara exento` =
        classes()
            .that()
            .areAnnotatedWith(ApplicationService::class.java)
            .should(consultaLaMatrizOSeDeclaraExento())

    /** Todo handler público de un `@RestController` declara su decisión de autorización (ADR-0009 D13). */
    @ArchTest
    val `todo handler publico de un @RestController declara @Authorize, @NoAuthRequired o @AuthenticatedOnly` =
        methods()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(RestController::class.java)
            .and()
            .arePublic()
            .and()
            .areMetaAnnotatedWith(RequestMapping::class.java)
            .should()
            .beAnnotatedWith(Authorize::class.java)
            .orShould()
            .beAnnotatedWith(NoAuthRequired::class.java)
            .orShould()
            .beAnnotatedWith(AuthenticatedOnly::class.java)

    /** Todo método `@AuthScope(CLUB)` declara el parámetro que [AuthScopeEnforcementAspect] necesita verificar. */
    @ArchTest
    val `todo metodo @AuthScope(CLUB) declara un parametro clubId de tipo UUID` =
        methods()
            .that()
            .areAnnotatedWith(AuthScope::class.java)
            .should(declaraParametroClubIdSiEsScopeClub())

    private fun consultaLaMatrizOSeDeclaraExento(): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>(
            "consultar AuthorizationMatrix o declararse exento (@NoAuthRequired/@AuthenticatedOnly de clase)",
        ) {
            override fun check(
                clazz: JavaClass,
                events: ConditionEvents,
            ) {
                if (clazz.isAnnotatedWith(NoAuthRequired::class.java) ||
                    clazz.isAnnotatedWith(AuthenticatedOnly::class.java)
                ) {
                    return
                }

                val nested = clazz.getPackage().classes.filter { it.name.startsWith("${clazz.name}$") }
                val consulta =
                    (nested + clazz).any { candidate ->
                        candidate.accessesFromSelf.any {
                            it.targetOwner.isEquivalentTo(AuthorizationMatrix::class.java)
                        }
                    }
                if (!consulta) {
                    events.add(
                        SimpleConditionEvent.violated(
                            clazz,
                            "${clazz.name} no consulta AuthorizationMatrix ni se declara exento",
                        ),
                    )
                }
            }
        }

    private fun declaraParametroClubIdSiEsScopeClub(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("declarar un parámetro clubId: UUID cuando el scope incluye CLUB") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                val scopes = method.getAnnotationOfType(AuthScope::class.java).scopes
                if (Scope.CLUB !in scopes) return

                val declaraClubId =
                    method.reflect().parameters.any {
                        it.isNamePresent && it.name == "clubId" && it.type == UUID::class.java
                    }
                if (!declaraClubId) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} no declara un parámetro clubId: UUID",
                        ),
                    )
                }
            }
        }
}
