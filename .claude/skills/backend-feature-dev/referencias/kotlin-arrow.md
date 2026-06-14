# Kotlin y Arrow.kt — idioms del proyecto

Fuente: ADR-0008 D11, `estructura-de-un-modulo.md` §3. El stack es **Spring MVC bloqueante + JPA**: las corrutinas no son el modelo por defecto (ver última sección).

## Either y la Raise DSL

Todo error de negocio fluye como `Either<{Modulo}Error, T>`:

```kotlin
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

fun publicar(): Either<PlanificacionError, PlanPublicado> = either {
    ensure(estado == EstadoPlan.BORRADOR) { PlanificacionError.PlanYaPublicado(id) }
    ensure(sesiones.isNotEmpty()) { PlanificacionError.SinSesiones }
    estado = EstadoPlan.PUBLICADO
    PlanPublicado.from(id, clubId, sesiones.toList())
}
```

Herramientas del builder `either { }`:

- `ensure(condicion) { error }` — valida o corta con Left.
- `ensureNotNull(valor) { error }` — smart-cast a no-nulo o Left.
- `raise(error)` — corta incondicionalmente.
- `otroEither.bind()` — desempaqueta o propaga el Left. Es la composición monádica: una cadena de `bind()` reemplaza el railway de `flatMap`.

```kotlin
fun ejecutar(planId: PlanId): Either<PlanificacionError, PlanPublicado> = either {
    autorizacionService.puedePublicarPlan(principal, planId).bind()
    val plan = repositorio.buscar(planId)
        ?: raise(PlanificacionError.NotFound("PlanSemanal", planId.value.toString()))
    plan.publicar().bind()
}
```

## La regla `require`/`check` vs `Either`

| Situación | Mecanismo | Ejemplo |
|-----------|-----------|---------|
| Validación **esperable** de negocio | `Either` | Plan ya publicado, sin sesiones, alumno fuera del snapshot |
| Precondición **imposible** (bug del caller) | `require` / `check` | `sesionId` que no pertenece al plan, ID nulo donde no puede serlo |

`require` lanza `IllegalArgumentException` y eso es correcto: un bug no es un flujo de negocio. Se testea con `shouldThrow<IllegalArgumentException>`.

## Jerarquía de errores: sealed class por módulo

**No hay `DomainError` compartido.** Cada módulo define la suya con variantes comunes de shape estandarizado + variantes propias:

```kotlin
sealed class PlanificacionError {
    // shape estandarizado entre módulos
    data class Forbidden(val razon: String) : PlanificacionError()
    data class NotFound(val recurso: String, val id: String) : PlanificacionError()
    data class InvalidInput(val campo: String, val motivo: String) : PlanificacionError()
    data object Conflict : PlanificacionError()
    data class ProjectionStale(val modulo: String, val lagSeconds: Long) : PlanificacionError()
    // específicas del módulo
    data class PlanYaPublicado(val planId: PlanId) : PlanificacionError()
    data object SinSesiones : PlanificacionError()
}
```

Esta sealed class **es** la "exception hierarchy" del módulo: exhaustiva en `when`, sin herencia abierta, sin stack traces innecesarios.

## Política de excepciones

- Excepciones solo **técnicas** y solo en **infrastructure**. Se capturan en el adaptador y se traducen:

```kotlin
override fun enviarInvitacion(destinatario: Email, magicLink: String): Either<EmailError, Unit> = either {
    runCatching { postmarkClient.send(destinatario, "Invitación", html) }
        .onFailure { raise(EmailError.EnvioFallido(it.message ?: "desconocido")) }
}
```

- El `@RestControllerAdvice` captura excepciones de framework → 500 neutro.
- Nunca `try/catch` de "excepciones de dominio": no existen.

## Typed IDs: value class + UUID v7

```kotlin
@JvmInline
value class PlanId(val value: UUID) {
    companion object {
        fun nuevo(): PlanId = PlanId(UuidCreator.getTimeOrderedEpoch())
    }
}
```

- Cero coste en runtime (inline), imposible pasar el ID equivocado.
- UUID v7 **generado en aplicación** (ordenable temporalmente → mejor locality de índice).
- MockK los maneja sin fricción; en mappers Konvert requieren custom converter.

## Smart constructors

Cuando la construcción puede fallar de forma esperable, constructor privado + factory que devuelve Either:

```kotlin
class Email private constructor(val valor: String) {
    companion object {
        fun crear(valor: String): Either<IdentidadError, Email> = either {
            ensure(REGEX.matches(valor)) { IdentidadError.InvalidInput("email", "formato inválido") }
            Email(valor)
        }
    }
}
```

Si la invariante es universal e incondicional (no depende de input del usuario), basta `init { require(...) }`.

## Modelado con sealed + data

- **Estados**: sealed class con variantes que portan solo los datos válidos en ese estado (hace irrepresentable lo inválido).
- **Value objects**: `data class` inmutables, o sealed cuando tienen formas alternativas (`Ritmo.Absoluto | Ritmo.Relativo`).
- `when` sobre sealed **sin rama `else`**: el compilador avisa al añadir una variante.
- Colecciones: exponer `List`, no `MutableList`; copiar en defensa (`sesiones.toList()`).

## Null safety como diseño

- `?` en el tipo significa "la ausencia es un caso de negocio" — trátalo, no lo silencies.
- `?:` con `raise(...)` es el patrón estándar para "no encontrado".
- `!!` prohibido salvo justificación en comentario. En la práctica: nunca en domain/application.

## Scope functions con criterio

| Función | Cuándo |
|---------|--------|
| `let` | Transformar un nullable: `evento.traceparent?.let { restore(it) }` |
| `apply` | Configurar un objeto que se devuelve (builders, entidades JPA en tests) |
| `also` | Side-effect sin romper la cadena (logging puntual) |
| `run`/`with` | Agrupación de operaciones sobre un receptor |

Antipatrón: anidar más de dos scope functions — extrae una función con nombre.

## inline / reified

Útiles en helpers de test y en extensiones genéricas (`shouldBeLeft<PlanificacionError.Forbidden>()` funciona gracias a reified). En código de producción del dominio, raramente necesarios — no optimices por reflejo.

## Corrutinas: uso acotado

El stack es **MVC bloqueante**: los casos de uso son funciones síncronas y JPA bloquea el hilo. No introducir `suspend` en puertos ni casos de uso.

Uso legítimo y acotado:
- Jobs programados o trabajos paralelos puntuales (fan-out de emails): `runBlocking` + `coroutineScope` con structured concurrency, en infrastructure.
- Nunca `GlobalScope`. Nunca corrutina que toque la transacción JPA del request.

Si algún día el proyecto migra a WebFlux/corrutinas end-to-end, eso es un ADR nuevo — no una decisión de feature.
