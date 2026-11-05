package com.runcriticon.architecture

import com.runcriticon.identidad.infrastructure.persistence.repositories.ClubEntityRepository
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import jakarta.persistence.EntityManager
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import org.springframework.data.repository.Repository as SpringDataRepository

/**
 * Guards de la malla de autorización (ADR-0009 D6, D10, D11, D13). Ya muerden: hay repositorios,
 * casos de uso y handlers reales desde H0/H1 (se cerró la divergencia doc↔código detectada en la
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

    /**
     * Solo un `@Repository` accede a la base de datos (ADR-0009 D11, D13). La regla de arriba solo ve los métodos
     * declarados en una clase `@Repository`. Un bean que inyectara un repositorio de Spring Data o un `JdbcTemplate`
     * consultaría sin pasar por la malla de autorización, y ni ArchUnit ni el aspecto lo verían. Con esta regla, toda
     * consulta pasa por un método que declara `@AuthScope` o `@NoAuthScope`, también los métodos que los
     * repositorios de Spring Data heredan de `JpaRepository`: solo se pueden llamar desde dentro del adaptador.
     *
     * Dos excepciones, y ninguna atiende peticiones:
     * - `..infrastructure.scheduling..`: los jobs de retención (ADR-0017) purgan por antigüedad, sin principal.
     * - `..infrastructure.bootstrap..`: validan o siembran datos al arrancar.
     *
     * Solo mira beans (`@Component` y sus derivados). Una clase que no es bean, como el helper de un adaptador, solo
     * recibe el acceso a datos de quien la llama, y quien la llama ya está sujeto a esta regla.
     */
    @ArchTest
    val `solo un @Repository accede a la base de datos` =
        noClasses()
            .that()
            .areMetaAnnotatedWith(Component::class.java)
            .and()
            .areNotAnnotatedWith(Repository::class.java)
            .and()
            .resideOutsideOfPackages("..infrastructure.scheduling..", "..infrastructure.bootstrap..")
            .should()
            .dependOnClassesThat(accesoABaseDeDatos())

    /**
     * Los métodos que un repositorio de Spring Data hereda para leer o borrar por id, o la tabla entera, no reciben el
     * `club_id` (ADR-0009 D4, D10). `findById(id)` devuelve la fila aunque sea de otro club, y el aspecto no lo nota:
     * el `clubId` que recibe el método del adaptador sí coincide con el del principal. Las consultas van por métodos
     * derivados o `@Query` que reciben el `club_id`. Guardar (`save`) y borrar entidades ya cargadas
     * (`delete(entity)`, `deleteAll(entities)`) siguen permitidos: actúan sobre filas que trajo una consulta acotada.
     *
     * Excepción: `ClubEntityRepository`, porque la clave primaria de `identidad.club` es el propio club.
     */
    @ArchTest
    val `ningun adaptador lee ni borra con los metodos heredados de Spring Data que no filtran por club` =
        noClasses()
            .should()
            .callMethodWhere(metodoHeredadoSinClub())

    @ArchTest
    val `no se accede a SecurityContextHolder fuera del nucleo de autorizacion` =
        noClasses()
            .that()
            .resideOutsideOfPackage("..shared.autorizacion..")
            .should()
            .dependOnClassesThat()
            .haveSimpleName("SecurityContextHolder")

    /**
     * [ListenerPrincipalScope] pone un principal arbitrario en el contexto de seguridad: desde una petición
     * serviría para suplantar al usuario autenticado. Solo lo usan los listeners de eventos, que no tienen
     * otro (y su implementación).
     */
    @ArchTest
    val `solo los listeners de eventos usan ListenerPrincipalScope` =
        noClasses()
            .that()
            .resideOutsideOfPackages("..application.listeners..", "..shared.autorizacion..")
            .should()
            .dependOnClassesThat()
            .areAssignableTo(ListenerPrincipalScope::class.java)

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

    /** Todo `@ApplicationService` vive en `application.*` (ADR-0008 D15): es la capa de casos de uso. */
    @ArchTest
    val `todo @ApplicationService reside en el paquete application` =
        classes()
            .that()
            .areAnnotatedWith(ApplicationService::class.java)
            .should()
            .resideInAPackage("..application..")

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

    /**
     * Todo método `@AuthScope(CLUB)` declara el parámetro que [AuthScopeEnforcementAspect] necesita
     * verificar. Vale tanto `clubId: UUID` como el typed ID `ClubId` (typed ID que elimina el UUID desnudo del
     * dominio): la value class se borra
     * a `UUID` en bytecode, que es lo que ven esta regla y el aspecto.
     */
    @ArchTest
    val `todo metodo @AuthScope(CLUB) declara un parametro clubId de tipo UUID` =
        methods()
            .that()
            .areAnnotatedWith(AuthScope::class.java)
            .should(declaraParametroClubIdSiEsScopeClub())

    private fun accesoABaseDeDatos(): DescribedPredicate<JavaClass> =
        assignableTo(SpringDataRepository::class.java)
            .or(assignableTo(JdbcOperations::class.java))
            .or(assignableTo(NamedParameterJdbcOperations::class.java))
            .or(assignableTo(JdbcClient::class.java))
            .or(assignableTo(EntityManager::class.java))
            .`as`("un repositorio de Spring Data, JdbcTemplate, JdbcClient o EntityManager")

    private fun metodoHeredadoSinClub(): DescribedPredicate<JavaMethodCall> =
        object : DescribedPredicate<JavaMethodCall>("un método heredado de Spring Data que no filtra por club") {
            override fun test(call: JavaMethodCall): Boolean {
                val target = call.target
                val owner = target.owner
                if (!owner.isAssignableTo(SpringDataRepository::class.java)) return false
                if (owner.isEquivalentTo(ClubEntityRepository::class.java)) return false
                return when (target.name) {
                    // Con argumentos borran las entidades recibidas; sin argumentos, la tabla entera.
                    "deleteAll", "deleteAllInBatch" -> target.rawParameterTypes.isEmpty()
                    else -> target.name in METODOS_HEREDADOS_SIN_CLUB
                }
            }
        }

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

    private companion object {
        val METODOS_HEREDADOS_SIN_CLUB =
            setOf(
                "findById",
                "findAllById",
                "existsById",
                "getReferenceById",
                "getById",
                "getOne",
                "findAll",
                "count",
                "deleteById",
                "deleteAllById",
                "deleteAllByIdInBatch",
            )
    }
}
