# JPA / Hibernate — optimización y patrones del proyecto

Fuente: ADR-0004, ADR-0008 D6, `persistencia.md`. Regla estructural: **la entidad JPA está separada del agregado** y un `@Konverter` convierte entre ambos. Nunca `@Entity` en clases de dominio.

## Configuración base (no negociable)

```yaml
spring:
  jpa:
    open-in-view: false        # OSIV abierto = conexión retenida durante el render + lazy loading accidental
    properties:
      hibernate:
        jdbc.batch_size: 50    # agrupa INSERT/UPDATE
        order_inserts: true
        order_updates: true
```

- `open-in-view: false` convierte cualquier lazy loading fuera de transacción en `LazyInitializationException` — **bien**: el error ruidoso te obliga a cargar lo que necesitas dentro del caso de uso, en vez de disparar queries silenciosas desde el adaptador.

## N+1: el bug de persistencia más caro

**Síntoma**: cargar N planes y que Hibernate dispare N queries extra para las sesiones de cada uno.

**Detección**:
- En tests de integración: contar queries con un `StatementInspector` o revisar el log SQL del Testcontainer en el caso de uso de listado.
- En producción: picos de `hikaricp` + p95 de un endpoint que crece linealmente con el tamaño de la lista.

**Soluciones, por orden de preferencia**:

1. **Proyección de lectura** (la mejor para listados — ver sección siguiente): no cargues el agregado.
2. **Fetch join explícito** cuando sí necesitas el agregado completo:

```kotlin
@Query("SELECT p FROM PlanSemanalEntity p JOIN FETCH p.sesiones WHERE p.id = :id")
fun findByIdConSesiones(id: UUID): PlanSemanalEntity?
```

3. `@EntityGraph` como alternativa declarativa al fetch join.

**Nunca**: `FetchType.EAGER` como "solución" — convierte el N+1 puntual en sobrecarga universal. Todas las asociaciones `LAZY` (default en `@OneToMany`; forzarlo en `@ManyToOne`, que es EAGER por defecto).

## Proyecciones de lectura: no cargues agregados para listar

El agregado existe para **escribir con invariantes**. Para listados y vistas, query directa a DTO:

```kotlin
// DTO de lectura — ni entidad ni agregado
data class PlanResumen(val id: UUID, val estado: String, val semanaInicio: LocalDate)

// Spring Data: constructor expression o interface projection
@Query("""
    SELECT new com.runcriticon.planificacion.infrastructure.persistencia.PlanResumen(
        p.id, p.estado, p.semanaInicio)
    FROM PlanSemanalEntity p WHERE p.grupoId = :grupoId
""")
fun listarResumenPorGrupo(grupoId: UUID): List<PlanResumen>
```

- Sin persistence context, sin dirty checking, sin mapeo a dominio: una query, datos planos.
- El método del repositorio sigue llevando su `@AuthScope`.
- Para consultas complejas (la vista previa del constructor de grupos), JdbcTemplate con SQL explícito es legítimo y a menudo más claro que JPQL.

## Escrituras

- **`@Version` (optimistic locking)** en toda entidad de agregado — ya es columna estándar (`version BIGINT`). Un `OptimisticLockException` se traduce a `{Modulo}Error.Conflict` en el adaptador.
- **Batch**: con `batch_size` configurado, los inserts en bucle (p. ej. sesiones de un plan) se agrupan. Funciona porque los IDs son **UUID v7 generados en aplicación** — sin `IDENTITY`, que desactiva el batching.
- **`saveAll()` sobre `save()` en bucle** para colecciones.
- Persistence context en operaciones masivas (jobs de borrado RGPD, reproyecciones): `flush()` + `clear()` cada ~50 entidades, o JdbcTemplate directamente.

## JSONB

- Value objects con shape variable → columna `jsonb` con `AttributeConverter` + Jackson (sealed class con `@JsonTypeInfo`). Patrón completo en `persistencia.md` §11.
- El `CHECK` de estructura en la migración es defensa en profundidad, no sustituye la validación del dominio.
- Consulta sobre JSONB → índice de expresión (`(ritmo->>'tipo')`) o GIN; verifica con `EXPLAIN` que se usa.

## Mapeo con Konvert (no MapStruct, no reflection)

- Un `@Konverter` por par tipo-tipo: `PlanSemanalMapper` (entidad↔dominio) y `PlanRestMapper` (dominio↔DTO) son interfaces separadas.
- Typed IDs y enums via custom converters reutilizables en `infrastructure/persistencia/converters.kt`.
- La reconstrucción del agregado usa `reconstruir(...)` (sin validación) — el estado ya fue válido al persistirse.
- Konvert genera en compilación: si el mapping no cuadra, falla el build, no el runtime. Eso es exactamente lo que queremos.

## Paginación y ordenación

- Listados siempre paginados (`Pageable` o LIMIT/OFFSET explícito). `findAll()` sin límite es un incidente esperando volumen.
- Orden estable: UUID v7 es ordenable temporalmente — `ORDER BY id` da orden de creación sin columna extra.
- Para paginación profunda (no aplica en MVP con este volumen), keyset pagination antes que OFFSET grande.

## Checklist JPA por feature

- [ ] Asociaciones nuevas `LAZY` (incluido `@ManyToOne`)
- [ ] Listado → proyección de lectura, no agregado mapeado
- [ ] Carga de agregado con hijos → fetch join, no N+1
- [ ] Entidad nueva con `@Version`, `created_at`/`updated_at`, esquema del módulo en `@Table`
- [ ] Índice para cada filtro de query nueva (más el de `club_id`)
- [ ] Operación masiva → batch + clear del persistence context, o JdbcTemplate
- [ ] Test de integración del repositorio contra Testcontainers (JSONB/índices no funcionan igual en H2 — por eso H2 está prohibido)
