# Estructura de un módulo — guía de referencia

Esta guía baja a tierra las decisiones de los ADR de arquitectura — **ADR-0007** (monolito modular, *events-first*), **ADR-0008** (hexagonal + DDD táctico) y **ADR-0009** (autorización) — mostrando **cómo se estructura un módulo por dentro**. Es el documento que el equipo lee el día 1.

> Los fragmentos de código son **ilustrativos** (Kotlin, el lenguaje de ADR-0001). El proyecto está en fase de diseño y aún no tiene código; esto es un patrón de referencia, no un módulo real. Los nombres están en castellano: el **lenguaje ubicuo** del discovery es el del código (ADR-0008).

Como ejemplo recurrente se usa el módulo **Planificación** y su agregado `PlanSemanal`.

## 1. Las tres capas

Cada módulo (un *bounded context* de ADR-0007) se organiza en tres capas:

```
infrastructure   →   application   →   domain
(adaptadores)        (casos de uso)     (modelo + puertos + eventos)
```

La **regla de dependencias apunta siempre hacia el dominio**: `infrastructure` depende de `application`, `application` depende de `domain`, y el `domain` **no depende de nadie**. La regla la verifica **ArchUnit** en los tests (ADR-0010), incluida la ausencia de imports de framework en `domain`.

## 2. La capa `domain`

Clases **puras**: sin Spring, sin JPA, sin ninguna anotación de framework. Es el corazón testable.

**Agregado** — una raíz que protege sus invariantes:

```kotlin
// domain
class PlanSemanal private constructor(
    val id: PlanId,
    val clubId: ClubId,
    val entrenadorId: EntrenadorId,
    private val sesiones: MutableList<Sesion>,
    private var estado: EstadoPlan,
) {
    fun publicar(): PlanPublicado {
        require(estado == EstadoPlan.BORRADOR) { "El plan ya está publicado" }
        estado = EstadoPlan.PUBLICADO
        return PlanPublicado(id, clubId)   // evento de dominio
    }
}
```

**Value object** — concepto sin identidad propia, inmutable (ej. el `Ritmo` de ADR-0002):

```kotlin
// domain
data class Ritmo(val tipo: TipoRitmo, val valor: Int, val distancia: Distancia? = null)
```

**Evento de dominio** — un hecho que ya ha ocurrido, nombrado en pasado:

```kotlin
// domain
data class PlanPublicado(val planId: PlanId, val clubId: ClubId)
```

**Puerto** — la interfaz de un repositorio o de algo que cruza a infraestructura; vive en `domain`, se implementa en `infrastructure`:

```kotlin
// domain
interface PlanSemanalRepository {
    fun guardar(plan: PlanSemanal)
    fun buscar(id: PlanId): PlanSemanal?
}
```

## 3. La capa `application`

Casos de uso que **orquestan el dominio**. Dependen de `domain`. Publican los eventos de dominio y consumen los entrantes.

```kotlin
// application
class PublicarPlanService(
    private val repositorio: PlanSemanalRepository,
    private val publicador: PublicadorDeEventos,
) {
    fun ejecutar(planId: PlanId) {
        val plan = repositorio.buscar(planId) ?: throw PlanNoEncontrado(planId)
        val evento = plan.publicar()
        repositorio.guardar(plan)
        publicador.publicar(evento)   // se entrega vía el outbox de Spring Modulith
    }
}
```

## 4. La capa `infrastructure`

Los **adaptadores**. Implementan los puertos de `domain`.

**Adaptador de entrada** — controlador REST:

```kotlin
// infrastructure
@RestController
class PlanController(private val publicarPlan: PublicarPlanService) {
    @PostMapping("/api/planes/{id}/publicar")
    fun publicar(@PathVariable id: String) = publicarPlan.ejecutar(PlanId(id))
}
```

**Modelo de persistencia + mapeador** — por la decisión de ADR-0008 (dominio puro), la entidad JPA está **separada** del agregado, y un mapeador convierte entre ambos:

```kotlin
// infrastructure — entidad de persistencia, distinta del agregado de dominio
@Entity @Table(name = "plan_semanal")
class PlanSemanalEntity { /* campos con anotaciones JPA */ }

// infrastructure — mapeador dominio ⇄ persistencia
object PlanSemanalMapper {
    fun aDominio(e: PlanSemanalEntity): PlanSemanal { /* ... */ }
    fun aEntidad(p: PlanSemanal): PlanSemanalEntity { /* ... */ }
}
```

El **repositorio** implementa el puerto usando la entidad JPA y el mapeador. Un **adaptador de publicación de eventos** implementa `PublicadorDeEventos` sobre el registro de eventos de Spring Modulith.

## 5. Comunicación entre módulos — *events-first*

Un módulo **nunca llama de forma síncrona** a otro (ADR-0007). Cuando necesita datos de otro contexto, mantiene una **proyección local** alimentada por eventos.

Ejemplo: para resolver el *snapshot* de membresía al publicar un plan, Planificación **no pregunta** a Club y taxonomía — mantiene su propia proyección, actualizada al consumir los eventos de aquel módulo:

```kotlin
// application — escucha de un evento de otro módulo
@ApplicationModuleListener
fun cuando(evento: AlumnoAsignadoAGrupo) {
    proyeccionMiembros.añadir(evento.grupoId, evento.alumnoId)   // idempotente
}
```

Los consumidores deben ser **idempotentes**: un evento puede entregarse más de una vez.

## 6. Autorización

Cada módulo **autoriza el acceso a sus propios recursos** (ADR-0009), con un núcleo compartido para el *principal*:

- **RBAC** (por rol) → en el controlador, con `@PreAuthorize`.
- **Nivel de objeto** (¿puede este usuario tocar este objeto?) → en el caso de uso, contra una proyección local de relaciones.
- **`club_id`** → filtro sistemático en el repositorio.

## 7. Checklist al crear un módulo nuevo

- [ ] Paquete del módulo reconocido por Spring Modulith; dependencias verificadas por ArchUnit.
- [ ] `domain` sin imports de framework — clases puras.
- [ ] Agregados que protegen de verdad sus invariantes (no dominio anémico).
- [ ] Eventos de dominio para lo que otros módulos deben saber; consumidores idempotentes.
- [ ] Modelo de persistencia separado del agregado, con su mapeador y tests del mapeo en los dos sentidos.
- [ ] Esquema propio del módulo; ninguna FK ni consulta cruzando a otro esquema (ADR-0004).
- [ ] Autorización RBAC + nivel de objeto + filtro por `club_id`.
- [ ] Tests: unitarios del dominio, de integración con Testcontainers, de contrato, de arquitectura (ArchUnit) y de fronteras de Modulith (ADR-0010).

## Referencias

- **ADR-0002** — modelo de datos (tags, `Ritmo` como *value object*).
- **ADR-0004** — base de datos: un esquema por módulo.
- **ADR-0007** — monolito modular, comunicación *events-first*.
- **ADR-0008** — arquitectura hexagonal y DDD; dominio puro con modelo de persistencia aparte.
- **ADR-0009** — modelo de autorización.
- **ADR-0010** — estrategia de tests (ArchUnit, Testcontainers, fronteras de Modulith).
- Plan de formación: [`docs/formacion/arquitectura-dirigida-por-eventos.md`](../formacion/arquitectura-dirigida-por-eventos.md).
