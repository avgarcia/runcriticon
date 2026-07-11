# Observabilidad por módulo — guía de referencia

Subdocumento de [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md). Cubre los **detalles de observabilidad por módulo** que la guía principal resume: MDC, logs estructurados, métricas obligatorias con Micrometer, trazas con OpenTelemetry, health checks, alarmas mínimas.

> Espejo aplicado de **ADR-0011** (observabilidad runtime: AMP + AMG + X-Ray + CloudWatch Logs) y los puntos de contacto operativos con ADR-0007 (outbox), ADR-0009 (lag stale), ADR-0014 (PII en logs). Si hay conflicto, gana el ADR.

## 1. Propósito y alcance

Cada módulo es responsable de:

1. **Emitir métricas obligatorias** por capa (HTTP, eventos, listeners, BD) y de negocio (registros, activaciones, etc.).
2. **Rellenar el MDC** con `module`, `club_id`, `user_id_hash` y `trace_id` en cada operación.
3. **Restaurar el contexto de traza** en los listeners para no romper la correlación end-to-end (ADR-0011 D4).
4. **Exponer health checks** custom cuando dependa de servicios externos.
5. **Documentar las alarmas** que el módulo necesita en el dashboard de AMG.

El **backend de observabilidad** es **AMP + AMG + X-Ray + CloudWatch Logs** (ADR-0011 D6). El código del módulo es **neutral** (Actuator + Micrometer + OpenTelemetry), nunca llama al SDK de AWS directamente.

## 2. Stack del módulo

| Pieza | Tecnología | Por qué |
|---|---|---|
| **Métricas** | Spring Boot Actuator + Micrometer (Prometheus exporter) | API estándar, exportador a AMP via `remote-write` |
| **Logs** | Logback con `LogstashEncoder` (JSON estructurado) | stdout → CloudWatch Logs, parseable por AMG |
| **Trazas** | OpenTelemetry SDK + ADOT Collector → AWS X-Ray | Cero acoplamiento al backend, plugin nativo en AMG |
| **Propagación** | W3C Trace Context (`traceparent`) | Estándar; propagado en eventos del outbox (ADR-0011 D4) |
| **Health** | Actuator `HealthIndicator` | App Runner usa `/actuator/health` |

### Dependencias típicas (`build.gradle.kts`)

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    implementation("io.opentelemetry:opentelemetry-api")
    implementation("io.opentelemetry:opentelemetry-sdk")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")

    implementation("net.logstash.logback:logstash-logback-encoder")
}
```

## 3. MDC operativo

Cada línea de log incluye el MDC con (ADR-0011 D5):

| Campo | Origen | Cómo se rellena |
|---|---|---|
| `trace_id` | OpenTelemetry | Auto en peticiones HTTP; restaurado del `traceparent` en listeners |
| `span_id` | OpenTelemetry | Auto |
| `club_id` | Principal de la sesión | Filtro HTTP / restaurador del evento |
| `user_id_hash` | Principal de la sesión | Hash determinístico con salt rotado anualmente |
| `module` | Paquete del controller / caso de uso | Filtro HTTP / aspecto al entrar |
| `env` | Variable de entorno `SPRING_PROFILES_ACTIVE` | Una vez en arranque |

### Filtro HTTP que rellena el MDC al entrar

```kotlin
// shared/observability/HttpMdcFilter.kt (identificadores en inglés per ADR-0008 D4)
@Component
class HttpMdcFilter(
    private val mdcRestorer: MdcRestorerForEvents,
    private val principalProvider: PrincipalProvider,
    private val handlerMappings: List<HandlerMapping>,
    private val environment: Environment,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        try {
            val principal = runCatching { principalProvider.current() }.getOrNull()
            mdcRestorer.restore(
                module = moduleOf(request),
                traceparent = request.getHeader("traceparent"),
                clubId = principal?.clubId,
                actorId = principal?.userId,
            )
            MDC.put("env", environment.activeProfiles.firstOrNull() ?: "unknown")
            filterChain.doFilter(request, response)
        } finally {
            mdcRestorer.clear()
        }
    }

    // module: resuelve el controller vía HandlerMapping.getHandler (mismo mecanismo que
    // DispatcherServlet), no vía regex sobre el path — "unmatched" si no hay ruta (404).
    private fun moduleOf(request: HttpServletRequest): String {
        val handler = handlerMappings.firstNotNullOfOrNull { mapping ->
            runCatching { mapping.getHandler(request) }.getOrNull()?.handler
        }
        val controllerClass = (handler as? HandlerMethod)?.beanType ?: return "unmatched"
        val rootPackage = controllerClass.packageName.removePrefix("com.runcriticon.").substringBefore(".")
        return ModuleTagResolver.resolve(rootPackage)
    }
}
```

Reutiliza `MdcRestorerForEvents.restore(...)` (mismo `trace_id`/`club_id`/`user_id_hash`/`module` que en el lado de eventos, ver más abajo) en vez de duplicar el hasheo y el parseo de `traceparent`. En rutas anónimas (login, activación, health) no hay principal — `user_id_hash` cae a `"system"`, igual que en eventos. Se registra en `SecurityConfig` con `addFilterAfter(httpMdcFilter, SecurityContextHolderFilter::class.java)`, para que el contexto de autenticación ya esté cargado cuando se resuelve el principal.

### Restaurador del MDC en listeners

Cuando un listener procesa un evento, no hay petición HTTP. El MDC se restaura del propio evento con `MdcRestorerForEvents` (`shared.observability` — identificadores en inglés per ADR-0008 D4, `NamingConventionArchTest`). Es `@Component`, no `object`: necesita `UserIdHasher` inyectado para no emitir nunca el `userId` en claro.

```kotlin
// shared/observability/MdcRestorerForEvents.kt
@Component
class MdcRestorerForEvents(private val userIdHasher: UserIdHasher) {

    /** Para eventos que implementan IntegrationEvent: el módulo se deriva de su paquete. */
    fun restore(event: IntegrationEvent) =
        restore(module = moduleOf(event), traceparent = event.traceparent, clubId = event.clubId, actorId = event.actorId)

    /** Para eventos internos de aplicación que no implementan IntegrationEvent. */
    fun restore(module: String, traceparent: String?, clubId: UUID?, actorId: UUID?) {
        traceIdOf(traceparent)?.let { MDC.put("trace_id", it) }
        clubId?.let { MDC.put("club_id", it.toString()) }
        MDC.put("user_id_hash", actorId?.let(userIdHasher::hash) ?: "system")
        MDC.put("module", module)
    }

    fun clear() = MDC.clear()
}
```

### Uso en el listener

```kotlin
@ApplicationModuleListener
fun on(evento: AlumnoAsignadoAGrupo) {
    mdcRestorer.restore(evento)
    try {
        if (!tracker.marcarSiNuevo(...)) return
        proyeccion.añadir(...)
    } finally {
        mdcRestorer.clear()
    }
}
```

## 4. Logs estructurados JSON

Configuración Logback (`src/main/resources/logback-spring.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>false</includeContext>
            <includeMdc>true</includeMdc>
            <fieldNames>
                <timestamp>ts</timestamp>
                <level>level</level>
                <logger>logger</logger>
                <thread>thread</thread>
                <message>message</message>
            </fieldNames>
            <customFields>{"service":"runcriticon-app","version":"${VERSION:-unknown}"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

### Ejemplo de línea de log

```json
{
  "ts": "2026-06-15T10:23:14.512Z",
  "level": "INFO",
  "logger": "com.runcriticon.planificacion.application.PublicarPlanService",
  "thread": "http-nio-8080-exec-3",
  "message": "Plan publicado",
  "trace_id": "0af7651916cd43dd8448eb211c80319c",
  "span_id": "b7ad6b7169203331",
  "club_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id_hash": "8a4b3c2d1e5f6a7b",
  "module": "planificacion",
  "env": "production",
  "service": "runcriticon-app",
  "version": "1.2.3"
}
```

### Niveles de log

ADR-0011 D13:

- **INFO** por defecto en producción.
- **WARN** para situaciones recuperables (rate limit alcanzado, reintento, proyección stale temporal).
- **ERROR** para fallos reales (Postmark caído, evento en DLQ, excepción no capturada del framework).
- **DEBUG/TRACE** off por defecto, activable via `/actuator/loggers/{logger}` sin redeploy (ADR-0013 D9).

### PII en logs — política estricta

Cruce con ADR-0014 D9:

- **NO loguear**: passwords, tokens, magic links, datos de salud completos, emails en claro, IPs completas.
- **Sí permitido**: `user_id_hash`, IP truncada `/24`, `trace_id`, `club_id`, identificadores de agregado.
- **Cuerpos HTTP**: NO se loguean salvo `DEBUG`/`TRACE` activado por incidente con tiempo limitado.

## 5. Bean de métricas por módulo

Cada módulo tiene una clase `{Modulo}Metricas` (`@Component`) que registra **explícitamente** sus métricas en el constructor o vía `@PostConstruct`. Patrón **declaración + uso**.

### Patrón canónico

```kotlin
// planificacion/infrastructure/observabilidad/PlanificacionMetricas.kt
@Component
class PlanificacionMetricas(registry: MeterRegistry) {

    val planesPublicados: Counter = Counter
        .builder("planificacion.planes_publicados_total")
        .description("Total de planes semanales publicados a un grupo")
        .tag("module", "planificacion")
        .register(registry)

    val tiempoPublicacion: Timer = Timer
        .builder("planificacion.publicacion_seconds")
        .description("Latencia del caso de uso publicar plan")
        .tag("module", "planificacion")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)

    val sesionesPersonalizadas: Counter = Counter
        .builder("planificacion.sesiones_personalizadas_total")
        .description("Total de sesiones personalizadas para un alumno concreto")
        .tag("module", "planificacion")
        .register(registry)

    val proyeccionLagSegundos: Gauge = Gauge
        .builder("planificacion.projection_lag_seconds") { calcularLag() }
        .description("Lag de la proyección miembros_grupo en segundos")
        .tag("module", "planificacion")
        .tag("projection", "miembros_grupo")
        .register(registry)

    private fun calcularLag(): Double = miembrosGrupoProjection.lagSegundos().toDouble()
}
```

### Uso desde el caso de uso

```kotlin
@ApplicationService
class PublicarPlanService(
    private val repositorio: PlanSemanalRepository,
    private val publicador: PublicadorDeEventos,
    private val metricas: PlanificacionMetricas,
) {
    fun ejecutar(planId: PlanId): Either<PlanificacionError, PlanPublicado> = either {
        metricas.tiempoPublicacion.record<Either<PlanificacionError, PlanPublicado>> {
            val plan = repositorio.buscar(planId) ?: raise(...)
            val evento = plan.publicar().bind()
            repositorio.guardar(plan)
            publicador.publicar(evento)
            metricas.planesPublicados.increment()
            evento
        }
    }
}
```

### Por qué declaración explícita

- **Catálogo legible**: el bean es la **lista visible** de métricas que el módulo emite.
- **Tests unitarios**: cada test puede verificar `metricas.planesPublicados.count() shouldBe 1`.
- **Control de cardinalidad**: el equipo decide qué tags pone (cruce sección 9).

## 6. Catálogo de métricas obligatorias por capa

| Capa | Métrica | Tipo | Tags | Umbral de alarma |
|---|---|---|---|---|
| **HTTP** | `http_server_requests_seconds` (p95) | Timer | `endpoint`, `status` | > 400 ms p95 (NFR ADR-0001) |
| **HTTP** | `http_server_requests_total{status=~"5.."}` | Counter | `endpoint`, `status` | > 1 % sostenido 5 min |
| **HTTP** | `http_server_requests_total{status=~"4.."}` | Counter | `endpoint`, `status` | Pico sostenido = posible escaneo |
| **Eventos** | `outbox_pending_events` | Gauge | — | > 100 sostenido 5 min |
| **Eventos** | `outbox_dlq_events` | Gauge | — | > 0 (cualquiera) |
| **Eventos** | `outbox_delivery_seconds` (p95) | Timer | `event_type` | > 10 s sostenido 5 min |
| **Listeners** | `listener_failures_total` | Counter | `listener` | > 0,1 % sostenido 10 min |
| **Listeners** | `listener_duration_seconds` (p95) | Timer | `listener` | > 1 s |
| **Proyecciones** | `projection_lag_seconds` | Gauge | `module`, `projection` | > 60 s (ADR-0009 D9) |
| **Postmark** | `postmark_send_failures_total` | Counter | `tipo_email` | > 5 % sostenido 5 min |
| **BD** | `hikari_connections_active`, `hikari_connections_pending` | Gauge | — | `pending > 0` sostenido 1 min |
| **JVM** | `jvm_gc_pause_seconds` (max) | Timer | `cause` | > 1 s |

### Política frente a errores de dominio (`Result.failure`)

Cruce con ADR-0008 D11 y ADR-0011 D10: los `XxxError` que viajan como `Either.Left` **no son excepciones** y **no inflan `http_server_requests_total{status=~"5.."}`**. Devuelven códigos 4xx (`400`, `403`, `409`) según la traducción del `@RestControllerAdvice`.

Eso significa que:

- La métrica de 5xx mide fallos del framework / infrastructure.
- La métrica de 4xx mide validaciones de negocio + autorización + conflictos.
- Picos sostenidos de 4xx son señal a investigar (escaneo, bug, regresión de UX) — pero no son "errores del backend".

## 7. Métricas de negocio del MVP

Cruce con ADR-0011 D11. Cada métrica vive en el bean `{Modulo}Metricas` del módulo correspondiente.

### Catálogo

| Módulo | Métrica | Definición | Cruce ADR |
|---|---|---|---|
| Identidad | `identidad.magic_links_issued_total` | Magic links emitidos | ADR-0003 D5 |
| Identidad | `identidad.magic_links_activated_total` | Magic links consumidos con éxito | ADR-0003 D5 |
| Identidad | `identidad.invitations_issued_total` | Invitaciones emitidas | ADR-0003 D4 |
| Identidad | `identidad.invitations_accepted_total` | Invitaciones aceptadas | ADR-0003 D4 |
| Identidad | `identidad.accounts_activated_total` | Cuentas activadas | ADR-0003 D4 |
| Identidad | `identidad.time_to_activation_seconds` (histograma) | Tiempo entre invitación → activación | derivada |
| Seguimiento | `seguimiento.session_reports_created_total` | Reportes de sesión creados | módulo Seguimiento |
| Seguimiento | `seguimiento.marcas_actualizadas_total` | Marcas actualizadas por alumnos | módulo Seguimiento |
| Planificación | `planificacion.planes_publicados_total` | Planes publicados a un grupo | módulo Planificación |
| Planificación | `planificacion.sesiones_personalizadas_total` | Personalizaciones de sesión por alumno | módulo Planificación |
| Cross-módulo | `dau` (gauge calculada) | Usuarios con al menos una petición HTTP autenticada en el día | derivada |
| Cross-módulo | `users_per_club` (gauge) | Usuarios activos por club | etiqueta `club_id` |

### Ejemplo: emitir métrica al activar cuenta

```kotlin
// identidad/application/ActivarCuentaService.kt
@ApplicationService
class ActivarCuentaService(
    private val invitacionRepo: InvitacionRepository,
    private val usuarioRepo: UsuarioRepository,
    private val metricas: IdentidadMetricas,
) {
    fun ejecutar(token: TokenInvitacion, password: Password?): Either<IdentidadError, Usuario> = either {
        val invitacion = invitacionRepo.buscarPorToken(token) ?: raise(...)
        val usuario = Usuario.desdeInvitacion(invitacion, password)
        usuarioRepo.guardar(usuario)
        invitacionRepo.marcarConsumida(invitacion)

        // Métricas de negocio
        metricas.cuentasActivadas.increment()
        metricas.tiempoActivacion.record(
            Duration.between(invitacion.emitidaEn, Instant.now())
        )

        usuario
    }
}
```

## 8. Trazas distribuidas con OpenTelemetry

Auto-instrumentación de Spring Boot cubre lo básico (HTTP, JDBC). Para los **flujos events-first**, hay que propagar y restaurar el contexto manualmente. Cruce con la guía principal sección 5 y con ADR-0011 D4.

### Propagación al publicar el evento

```kotlin
// shared/eventos/IntegrationEventFactory.kt
object IntegrationEventFactory {
    /** Rellena los 6 campos obligatorios + traceparent del contexto actual. */
    fun camposBase(aggregateId: UUID, clubId: UUID, actorId: UUID?): EventoCamposBase =
        EventoCamposBase(
            eventId       = UUID.randomUUID(),
            aggregateId   = aggregateId,
            occurredAt    = Instant.now(),
            version       = 1,
            clubId        = clubId,
            actorId       = actorId,
            traceparent   = OpenTelemetryHelper.serializarContextoActual(),
        )
}
```

### Restauración en el listener

Ver sección 3 (`MdcRestorerForEvents`). Equivalente para el contexto OTel:

```kotlin
object TraceContextRestorer {
    fun restore(traceparent: String) {
        val ctx = W3CTraceContextPropagator.getInstance()
            .extract(Context.current(), mapOf("traceparent" to traceparent)) { c, k -> c[k] }
        Context.current().with(ctx).makeCurrent()
    }
}
```

### Spans personalizados

Para operaciones largas dentro de un caso de uso:

```kotlin
fun publicarPlanGrande(planId: PlanId) {
    val tracer = OpenTelemetry.getTracer("planificacion")
    tracer.spanBuilder("publicar_plan_grande")
        .setAttribute("plan_id", planId.value.toString())
        .startSpan()
        .use { span ->
            // operación
        }
}
```

### Muestreo

ADR-0011 D12: 100 % en MVP → 10 % cuando supere 100 req/s + tail sampling para errores y latencia alta. Configuración en el ADOT Collector, no en el código.

## 9. Convención de tagging y cardinalidad

Los **tags** (labels) son la dimensión que permite filtrar en AMG. Mal usados explotan la cardinalidad y disparan el coste de AMP.

### Tags obligatorios

| Tag | Valor | Razón |
|---|---|---|
| `service` | `runcriticon-app` | Identificación del servicio |
| `env` | `staging` \| `production` | Separar entornos |
| `module` | `identidad` \| `planificacion` \| ... | Atribución por módulo |

### Tags permitidos (controlados)

| Tag | Valores | Cardinalidad | Notas |
|---|---|---|---|
| `endpoint` | URL template (`/api/planes/{id}/publicar`) | Baja (< 100) | Spring auto-tagea |
| `status` | `2xx`, `3xx`, `4xx`, `5xx` o código exacto | Baja | Auto |
| `event_type` | Nombre del integration event | Baja (< 50) | Manual |
| `listener` | FQCN del listener | Baja | Manual |
| `tipo_email` | `invitacion`, `magic_link`, etc. | Baja | Manual |
| `projection` | Nombre de la proyección | Baja | Manual |

### Tags PROHIBIDOS sin justificación

- **`user_id`** — cardinalidad altísima. Usar `user_id_hash` con muestreo si hace falta.
- **`club_id`** — en MVP mono-club es trivial. **A revisar** al llegar a > 50 clubes (cruce ADR-0011 D9): evaluar bucketización (`club_id_bucket = hash(club_id) % 10`) o agregaciones a nivel servicio.
- **Path completos** con IDs (`/api/planes/abc-123/publicar`) — explotan la cardinalidad. Usar siempre el path template.
- **Mensajes de error como tag** — no, van en logs.

## 10. Health checks

### Composite de Actuator

App Runner consulta `/actuator/health` cada N segundos para decidir si la instancia está sana (ADR-0006 D3). El composite incluye:

- **`db`** — conexión a RDS.
- **`diskSpace`** — espacio disponible.
- **`ping`** — la JVM responde.

### Health checks custom por módulo

Cuando un módulo depende de un servicio externo, expone su propio `HealthIndicator`:

```kotlin
// email/infrastructure/health/PostmarkHealthIndicator.kt
@Component
class PostmarkHealthIndicator(private val postmarkClient: PostmarkClient) : HealthIndicator {
    override fun health(): Health {
        return runCatching { postmarkClient.ping() }
            .fold(
                onSuccess = { Health.up().withDetail("provider", "postmark").build() },
                onFailure = { Health.down(it).build() },
            )
    }
}
```

El composite lo incluye automáticamente. Si `postmark` está abajo, `/actuator/health` reporta `DOWN` y App Runner puede decidir reciclar la instancia.

### Readiness vs Liveness

```yaml
# application.yml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: ping, diskSpace
        readiness:
          include: db, postmark
```

- **Liveness** = la JVM responde. App Runner mata la instancia si falla persistentemente.
- **Readiness** = la app está lista para servir peticiones. Si BD o Postmark están abajo, no se enrutan peticiones nuevas.

## 11. Alarmas mínimas por módulo

ADR-0011 D16 fija las alarmas mínimas iniciales. Cada módulo documenta en su `OBSERVABILIDAD.md` las alarmas que el equipo debería ver en AMG:

```markdown
# Observabilidad — módulo Identidad

## Alarmas mínimas (severidad → email a alertas@runcriticon)

| Alarma | Métrica | Umbral | Severidad |
|---|---|---|---|
| Postmark falla > 5% | `postmark_send_failures_total` rate | > 5 % sostenido 5 min | HIGH |
| Magic links sin entregar atascados | `outbox_pending_events{event_type='MagicLinkSolicitado'}` | > 100 sostenido 5 min | CRITICAL |
| Tasa de errores 5xx en /api/identidad/* | http_server_requests_total{status=~"5.."} | > 1 % sostenido 5 min | HIGH |

## Métricas de negocio en el dashboard del piloto

- magic_links_issued_total (24h)
- magic_links_success_rate (24h)
- accounts_activated_total (acumulado)
- time_to_activation_seconds.p50 / p95
```

## 12. Tests obligatorios de observabilidad

### Test: el caso de uso incrementa la métrica

```kotlin
class PublicarPlanServiceMetricasTest : IntegrationTestBase() {

    @Autowired lateinit var publicarPlan: PublicarPlanService
    @Autowired lateinit var registry: MeterRegistry

    @Test
    fun `publicar plan incrementa planes_publicados_total`() {
        val plan = PlanSemanalBuilder().enBorrador().conTresSesiones().build()
        repositorio.guardar(plan)

        val antes = registry.counter("planificacion.planes_publicados_total").count()

        publicarPlan.ejecutar(plan.id).shouldBeRight()

        val despues = registry.counter("planificacion.planes_publicados_total").count()
        despues shouldBe (antes + 1.0)
    }
}
```

### Test: el log incluye MDC esperado

```kotlin
class MdcEnLogsTest : IntegrationTestBase() {

    @Test
    fun `peticion HTTP rellena MDC con module, club_id y user_id_hash`() {
        val logs = capturarLogs {
            mockMvc.perform(get("/api/planes").principal(testPrincipal()))
        }

        logs.first { it.formattedMessage.contains("Plan listado") }
            .mdcPropertyMap shouldContainAll mapOf(
                "module" to "planificacion",
                "club_id" to testPrincipal().clubId.toString(),
            )
        logs.first().mdcPropertyMap.keys shouldContain "user_id_hash"
    }
}
```

### Test: el listener restaura el trace_id

```kotlin
class ListenerRestauracionTraceTest : IntegrationTestBase() {

    @Test
    fun `listener restaura el trace_id del traceparent del evento`() {
        val traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
        val evento = AlumnoAsignadoAGrupoBuilder()
            .conTraceparent(traceparent)
            .build()

        val capturado = capturarMDC { listener.on(evento) }

        capturado["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
    }
}
```

## 13. Checklist observabilidad al crear un módulo

- [ ] Bean `{Modulo}Metricas` creado con MeterRegistry inyectado, listando explícitamente las métricas del módulo
- [ ] Métricas obligatorias por capa cubiertas: HTTP (auto), outbox (Spring Modulith), listeners, proyecciones (`projection_lag_seconds`), BD (HikariCP), JVM (Micrometer) `(ADR-0011 D10)`
- [ ] Métricas de negocio del módulo emitidas en los casos de uso correspondientes `(ADR-0011 D11)`
- [ ] Todos los counters / timers tienen `tag("module", "{modulo}")` para atribución
- [ ] `HttpMdcFilter` (`shared/observability`) activo en la cadena de filtros HTTP del módulo
- [ ] Listeners llaman a `MdcRestorerForEvents.restaurar(evento)` al inicio y `.limpiar()` en `finally`
- [ ] Logs en INFO con `LogstashEncoder` (JSON estructurado) `(ADR-0011 D3)`
- [ ] Logs respetan PII: ningún password, token, magic link, datos de salud en claro; `user_id_hash` no `user_id`; IPs truncadas `/24` `(ADR-0011 D15, ADR-0014 D9)`
- [ ] Si el módulo depende de un servicio externo: `HealthIndicator` custom registrado en `readiness` group `(ADR-0011 D19)`
- [ ] Trazas: si hay listeners de eventos, restauración de `traceparent` con `TraceContextRestorer` antes de la lógica del listener `(ADR-0011 D4)`
- [ ] Spans personalizados en operaciones largas del caso de uso (cuando aporten)
- [ ] Tags: solo los autorizados (`module`, `endpoint`, `status`, `event_type`, `listener`); cardinalidad controlada (sin `user_id`, sin `club_id` salvo plan documentado para multi-club)
- [ ] `OBSERVABILIDAD.md` del módulo creado con alarmas y métricas de negocio del dashboard
- [ ] Tests verifican que el caso de uso incrementa la métrica esperada, que el MDC tiene los campos esperados, y que el listener restaura el trace_id

## Referencias

- **ADR-0011 D1-D24** — observabilidad runtime: Actuator + Micrometer + OpenTelemetry, AMP + AMG + X-Ray + CloudWatch Logs, MDC, métricas por capa, métricas de negocio, severidades, política anti-ruido, canary externo.
- **ADR-0006 D3, D24** — App Runner consume `/actuator/health`, coexistencia CloudWatch + AMP/AMG.
- **ADR-0007 D6, D13** — outbox y política de fallos como fuente de las métricas de eventos.
- **ADR-0009 D9** — política frente a proyección stale → `projection_lag_seconds`.
- **ADR-0008 D11** — `Either.Left` no infla 5xx en la métrica HTTP.
- **ADR-0014 D9** — anonimización de IP truncada y `userId` hasheado en logs.
- **ADR-0001** — NFR de latencia p95 < 400 ms (base de la alarma HTTP).
- [`estructura-de-un-modulo.md`](estructura-de-un-modulo.md) — guía principal.
- [`persistencia.md`](persistencia.md) §8 — `last_processed_event_ts` para el cálculo de `projection_lag_seconds`.
- [`testing-de-modulos.md`](testing-de-modulos.md) §4 — patrones de tests para verificar emisión de métricas.
