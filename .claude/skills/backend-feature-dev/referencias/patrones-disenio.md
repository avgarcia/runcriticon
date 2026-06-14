# Patrones de diseño y SOLID en la hexagonal del proyecto

Fuente: ADR-0008 (hexagonal + DDD táctico), `estructura-de-un-modulo.md`. Regla general: **el patrón se gana, no se planta** — se introduce cuando el dolor existe, no por anticipación.

## SOLID mapeado a las capas

| Principio | Cómo se materializa aquí |
|-----------|--------------------------|
| **S** — Single Responsibility | Un `@ApplicationService` = un caso de uso. Un `@Konverter` = un par tipo-tipo. Un listener = un evento. |
| **O** — Open/Closed | Sealed classes + `when` exhaustivo: añadir variante obliga al compilador a señalar todos los puntos de extensión. |
| **L** — Liskov | Implementaciones de puertos honran el contrato completo del interface (incluido el shape del Either que devuelven). |
| **I** — Interface Segregation | Puertos pequeños por colaborador: `EnviadorDeEmail`, `PublicadorDeEventos` — no un `InfraestructuraService` gordo. |
| **D** — Dependency Inversion | Es la regla estructural del módulo: `infrastructure → application → domain`. El dominio define los puertos; la infraestructura los implementa. ArchUnit la verifica. |

## Clean Architecture ≈ la hexagonal del proyecto

La "Clean Architecture" no añade nada nuevo aquí: las capas del proyecto ya son sus círculos.

- **Entities** → `domain/` (agregados, value objects).
- **Use cases** → `application/` (`@ApplicationService`).
- **Interface adapters** → `infrastructure/` (REST, JPA, email).
- **La regla de dependencia** (solo hacia dentro) → verificada por ArchUnit (`CapasArchTest`).

No inventes capas extra (`usecases/`, `gateways/`, `presenters/`): la estructura canónica de `estructura-de-un-modulo.md` §1 es la única válida.

## DDD táctico

- **Agregado**: raíz que protege invariantes. Constructor privado + factory; operaciones de negocio como métodos que devuelven `Either` o evento. Mantenerlos **pequeños**: si una operación necesita dos agregados, casi siempre falta un evento de dominio entre ellos.
- **Value object**: inmutable, sin identidad, igualdad por valor (`data class` o sealed). Validación en construcción.
- **Domain event interno** (`domain/events/`): hecho en pasado, orquestación dentro del módulo.
- **Integration event** (`api/events/`): contrato público con los 6 campos + traceparent. Nunca expone tipos del dominio.
- **Repositorio**: puerto por agregado raíz (no por entidad hija). `Sesion` se persiste a través de `PlanSemanalRepository`, no tiene repo propio.
- **Servicio de dominio**: solo cuando la lógica no pertenece naturalmente a ningún agregado. Si tiene dependencias de infraestructura, no es de dominio — es un caso de uso.

## Strategy — con sealed + when, no con jerarquías

En Kotlin el Strategy clásico (interface + N clases + inyección) suele ser ceremonia. La forma idiomática:

```kotlin
// Las estrategias SON datos: sealed class
sealed class Ritmo {
    data class Absoluto(val segPorKm: Int) : Ritmo()
    data class Relativo(val referencia: Distancia, val deltaSegPorKm: Int) : Ritmo()
}

// El comportamiento se resuelve con when exhaustivo donde se necesita
fun Ritmo.resolverSegPorKm(marcas: MarcasAlumno): Either<SaludError, Int> = when (this) {
    is Ritmo.Absoluto -> segPorKm.right()
    is Ritmo.Relativo -> marcas.de(referencia)
        .map { it.segPorKm + deltaSegPorKm }
}
```

Strategy con interface + inyección Spring **solo** cuando: (a) las estrategias tienen dependencias propias de infraestructura, o (b) se seleccionan por configuración en runtime. Ejemplo legítimo: `EnviadorDeEmail` (Postmark hoy, SES mañana) — y eso ya es un puerto, no hace falta llamarlo Strategy.

## Factory — companion object, no FactoryFactory

```kotlin
// Named constructor en companion: la factory idiomática
class PlanSemanal private constructor(...) {
    companion object {
        fun crear(clubId: ClubId, entrenadorId: EntrenadorId, semana: Semana): Either<PlanificacionError, PlanSemanal> =
            either { /* invariantes de creación */ }

        fun reconstruir(id: PlanId, /* ... */): PlanSemanal =
            PlanSemanal(/* sin validar: viene de la BD, ya fue válido */)
    }
}
```

- `crear(...)` valida y devuelve Either — para flujos de negocio.
- `reconstruir(...)` no valida — exclusivo del mapper de persistencia (estado ya validado en su día).
- Una clase Factory separada solo si la creación exige colaboradores inyectados (raro). Abstract Factory: no ha hecho falta y probablemente no hará.

## Otros patrones que el proyecto ya usa sin llamarlos por su nombre

| Patrón | Dónde vive ya |
|--------|---------------|
| Ports & Adapters | `domain/ports/` + `infrastructure/` |
| Outbox | `public.event_publication` (Spring Modulith, ADR-0007 D6) |
| Idempotent consumer | `EventoProcesadoTracker` + tabla `evento_procesado` |
| CQRS-lite | Proyecciones locales de lectura (`miembros_grupo`) separadas del modelo de escritura |
| Builder | Fixtures de test (`PlanSemanalBuilder`) — no en producción, donde manda el smart constructor |
| Anti-corruption layer | El payload plano de los integration events: ningún tipo interno cruza módulos |

## Cuándo NO aplicar un patrón

- Si la solución directa cabe en una función pura del dominio, no la envuelvas en clases.
- Una abstracción con **una sola implementación y sin segunda prevista** no se abstrae (excepción: puertos hacia infraestructura, que se abstraen siempre por la regla de dependencias).
- No introducir genéricos "para reutilizar luego": el segundo caso de uso decide la forma de la abstracción, no el primero.
