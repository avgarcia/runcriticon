# JVM y performance — criterios para Runcriticon

Contexto real (no genérico): mono-club, ~550 usuarios, < 100 concurrentes, p95 API < 400 ms, single-instance (ADR-0001 NFRs, ADR-0006). **La mayoría de problemas de performance de este proyecto serán SQL, no JVM.** Mira primero el plan de la query; toca la JVM después.

## Runtime: GraalVM CE 25 en modo JIT (ADR-0016)

- Runtime **GraalVM CE 25** (Java 25 LTS) con el compilador Graal como JIT — mejor rendimiento en lambdas/streams (código Kotlin idiomático) y arranque algo más rápido.
- La compilación apunta a **target Java 21** (Kotlin 2.1.0 / detekt no soportan 25). Build stage y CI en 21; runtime stage del Docker en 25.
- **No** native-image en MVP: dejaría fuera partes de Spring Boot y el payoff no existe a esta escala.

## Selección de GC

| Escenario | GC | Cuándo |
|-----------|----|----|
| **Default del proyecto** | **G1** | Heap < 4 GB, pausas objetivo ~200 ms — sobra para p95 400 ms. No tocar sin datos. |
| Latencia estricta + heap grande | ZGC | Solo si algún día las pausas de G1 aparecen en los percentiles. Hoy no. |
| Batch puro sin latencia | Parallel | No aplica (servicio interactivo). |

Disparador para revisar el GC: pausas > 100 ms visibles en `jvm_gc_pause` (Micrometer) correlacionadas con picos de p95. Sin esa evidencia, cualquier flag de GC es cargo cult.

## Heap y contenedor

- En contenedor, dimensionar con `-XX:MaxRAMPercentage=75.0` en vez de `-Xmx` fijo — sobrevive a cambios de tamaño de task.
- `-Xms` = `-Xmx` efectivo (o `InitialRAMPercentage` igual) para evitar resize del heap en caliente.
- Dejar ~25 % para off-heap: metaspace, threads (1 MB/stack), buffers de red, código JIT.

## Detección de memory leaks

1. **Síntoma**: `jvm_memory_used` (old gen) creciendo monótonamente entre GCs completos; OOM eventual.
2. **Diagnóstico barato**: JFR continuo (`-XX:StartFlightRecording=...`), overhead < 2 % — se puede llevar siempre activo.
3. **Diagnóstico profundo**: heap dump (`-XX:+HeapDumpOnOutOfMemoryError`) + Eclipse MAT, buscar dominator tree.
4. **Sospechosos típicos en este stack**:
   - Caches sin bound (`mutableMapOf` como "cache" en un singleton — usar Caffeine con `maximumSize` si hace falta cache).
   - Listeners/callbacks registrados y nunca des-registrados.
   - `ThreadLocal` sin `remove()` en pools de hilos (¡el MDC lo gestiona logback, no lo toques a mano!).
   - Sesiones Hibernate de larga vida acumulando entidades en el persistence context (ver jpa-hibernate.md).

## Thread pools

- **Tomcat** (`server.tomcat.threads.max`, default 200): para < 100 concurrentes el default sobra. Reducirlo (p. ej. 50) ahorra memoria de stacks sin coste real.
- **Regla de dimensionado**: pool ≈ núcleos × (1 + tiempo_espera/tiempo_cpu). Con cargas JPA (mucho I/O de BD), pools moderados; más hilos que conexiones de BD disponibles solo genera cola en Hikari.
- **`@Async`/executors propios**: siempre executor explícito con nombre, bounded queue y `ThreadPoolTaskExecutor` — nunca el `SimpleAsyncTaskExecutor` default (crea un hilo por tarea).
- Virtual threads (Java 21+): tentadores con este stack bloqueante, pero **pinning** con `synchronized` en drivers JDBC antiguos es un riesgo real. Es decisión de plataforma (ADR), no de feature.

## HikariCP

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10        # núcleos*2 + margen; más NO es mejor (contención en Postgres)
      minimum-idle: 10             # = max para evitar churn de conexiones
      connection-timeout: 3000     # falla rápido: mejor 503 que requests colgados
      max-lifetime: 1800000        # 30 min, por debajo de timeouts de red intermedios
      leak-detection-threshold: 60000  # loguea conexiones retenidas > 60 s (síntoma de tx larga)
```

- Pool pequeño y estable > pool grande: PostgreSQL rinde mejor con pocas conexiones activas.
- `leak-detection-threshold` activado en staging siempre: detecta transacciones que se quedan abiertas (p. ej. `@Transactional` envolviendo una llamada HTTP externa — antipatrón).
- Métrica a vigilar: `hikaricp_connections_pending` > 0 sostenido = pool agotado o transacciones lentas.

## JMH — guía (sin setup en el build hoy)

**Cuándo merece la pena**: casi nunca en este proyecto. Solo si un algoritmo puro del dominio (p. ej. resolución de pertenencia a grupo sobre miles de tags) aparece como hotspot en JFR **y** la alternativa de optimización no es obvia. El 95 % de los "lento" serán queries — eso se mide con `EXPLAIN ANALYZE`, no con JMH.

**Cómo montarlo llegado el momento** (decisión consciente, PR propio):
- Plugin `me.champeau.jmh` en un source set separado (`src/jmh/kotlin`) — no contamina main ni test.
- Benchmarks sobre funciones **puras del dominio** (sin Spring, sin BD): es donde JMH da señal limpia.

**Antipatrones que invalidan el benchmark**:
- Ignorar warmup: el JIT necesita iteraciones para compilar — usar `@Warmup` siempre.
- Dead code elimination: si no consumes el resultado, Graal lo borra y mides nada — devolver el valor o usar `Blackhole`.
- Constant folding: inputs hardcodeados se precalculan — usar `@State` con datos variables.
- Medir con el portátil en battery-saver o con otras cargas: resultados basura.
- Microbenchmark de algo dominado por I/O: el ruido de la BD aplasta cualquier diferencia de CPU.

## Checklist de performance al desarrollar una feature

- [ ] ¿La query nueva tiene índice para su filtro principal (además del de `club_id`)?
- [ ] ¿La transacción abarca solo BD (sin llamadas HTTP/email dentro)?
- [ ] ¿Listados con paginación, no `findAll()`?
- [ ] ¿Algún bucle que llame al repositorio por elemento? (N+1 aplicativo)
- [ ] ¿Cache propuesto? → justifica con datos; el NFR se sostiene sin caché (ADR-0001)
