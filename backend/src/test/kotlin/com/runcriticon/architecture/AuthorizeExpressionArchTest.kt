package com.runcriticon.architecture

import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.AuthorizeExpression
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods

/**
 * Las expresiones de `@Authorize` son strings que el compilador no verifica (ADR-0009 D2), y desde que
 * `AuthorizeInterceptor` las evalúa, una expresión equivocada deniega a un rol legítimo. Estas reglas lo detectan en la
 * build, no en producción.
 */
@AnalyzeClasses(
    packages = ["com.runcriticon"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class AuthorizeExpressionArchTest {
    /** La expresión nombra un recurso y una acción que existen, y al menos un rol tiene ese permiso en la matriz. */
    @ArchTest
    val `toda expresion de @Authorize es un permiso de la matriz` =
        methods()
            .that()
            .areAnnotatedWith(Authorize::class.java)
            .should(declararUnPermisoDeLaMatriz())

    /**
     * El handler llama a un caso de uso que consulta en la matriz el mismo recurso y la misma acción que declara. Si
     * declara otro par, el motor y el caso de uso deciden por reglas distintas: o el motor deniega a un rol que el
     * caso de uso aceptaría, o la anotación no dice lo que de verdad se comprueba.
     */
    @ArchTest
    val `todo handler con @Authorize llama a un caso de uso que consulta ese mismo permiso` =
        methods()
            .that()
            .areAnnotatedWith(Authorize::class.java)
            .should(llamarAUnCasoDeUsoQueConsultaElMismoPermiso())

    private fun declararUnPermisoDeLaMatriz(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("declarar un permiso RECURSO:ACCION con alguna regla en la matriz") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                val expresion = method.getAnnotationOfType(Authorize::class.java).expresion
                val permission = AuthorizeExpression.parse(expresion)
                val algunRol =
                    permission != null &&
                        Role.entries.any { AuthorizationMatrix.can(it, permission.resource, permission.action) }
                if (!algunRol) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} declara @Authorize(\"$expresion\"), que no es un permiso de la matriz",
                        ),
                    )
                }
            }
        }

    private fun llamarAUnCasoDeUsoQueConsultaElMismoPermiso(): ArchCondition<JavaMethod> =
        object : ArchCondition<JavaMethod>("llamar a un caso de uso que consulta el mismo permiso que declara") {
            override fun check(
                method: JavaMethod,
                events: ConditionEvents,
            ) {
                val expresion = method.getAnnotationOfType(Authorize::class.java).expresion
                val permission = AuthorizeExpression.parse(expresion) ?: return
                val casosDeUso = casosDeUsoLlamadosPor(method)
                val coincide =
                    casosDeUso.any { caso ->
                        permission.resource.name in constantesLeidas(caso, Resource::class.java) &&
                            permission.action.name in constantesLeidas(caso, Action::class.java)
                    }
                if (!coincide) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "${method.fullName} declara @Authorize(\"$expresion\"), pero ninguno de los casos de " +
                                "uso a los que llama (${casosDeUso.map { it.simpleName }}) consulta ese permiso",
                        ),
                    )
                }
            }
        }

    /** Los `@ApplicationService` a los que llama el handler, directamente o desde un método propio del controlador. */
    private fun casosDeUsoLlamadosPor(handler: JavaMethod): Set<JavaClass> {
        val controller = handler.owner
        val llamadas = handler.methodCallsFromSelf
        val desdeAuxiliares =
            llamadas
                .filter { it.targetOwner == controller }
                .flatMap { llamada -> controller.methods.filter { it.name == llamada.name } }
                .flatMap { it.methodCallsFromSelf }
        return (llamadas + desdeAuxiliares)
            .map { it.targetOwner }
            .filter { it.isAnnotatedWith(ApplicationService::class.java) }
            .toSet()
    }

    /** Constantes del enum [enumType] que lee la clase o alguna de sus clases anidadas (lambdas no inline incluidas). */
    private fun constantesLeidas(
        clazz: JavaClass,
        enumType: Class<*>,
    ): Set<String> {
        val anidadas = clazz.getPackage().classes.filter { it.name.startsWith("${clazz.name}$") }
        return (anidadas + clazz)
            .flatMap { it.fieldAccessesFromSelf }
            .filter { it.targetOwner.isEquivalentTo(enumType) }
            .map { it.target.name }
            .toSet()
    }
}
