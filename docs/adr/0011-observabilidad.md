# ADR-0011 — Observabilidad

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: premisas heredadas, NFRs propios, sub-decisiones numeradas D1-D24 con anchors; **cambio del backend**: del stack Grafana+Loki+Prometheus+Tempo autoalojado original al **stack gestionado AWS** — Amazon Managed Prometheus + Amazon Managed Grafana + AWS X-Ray + CloudWatch Logs — por proporción al equipo de 4 personas; **resolución de la contradicción con ADR-0006 D24**: coexistencia explícita CloudWatch (infra-AWS) + AMP/AMG (app); incorporación de: identificador de correlación W3C Trace Context, MDC operativo, métricas obligatorias por capa con umbrales, métricas de negocio del MVP, política de muestreo de trazas, retención por tipo, PII en logs, severidades de alertas, política anti-ruido, canary externo, distinción observabilidad vs auditoría, disparadores para evolución) · **aceptado 2026-05-29** · revisado 2026-06-12 (corrección de drift de nombres de módulo/esquema: "módulo Salud" → módulo Seguimiento en D5 y métricas de negocio, valores del tag `module` en D9 alineados con los esquemas canónicos `identidad`/`club_taxonomia`/`planificacion`/`seguimiento`/`auditoria` — ADR-0004 D4; sin cambio de decisión) · **revisado 2026-07-11** (D5 — el filtro Servlet que rellena el MDC en cada petición HTTP no existía; solo el lado de eventos (`MdcRestorerForEvents`). Se implementa `HttpMdcFilter` (`shared.observability`, registrado en `SecurityConfig`) con el mismo mecanismo de `module` que D9 (`ModuleTagResolver`, extraído y compartido con `MdcRestorerForEvents`). D5 documenta además que `span_id` no está implementado en ninguno de los dos lados — sin SDK de tracing real todavía — y que D4 da por hecho un `traceparent` auto-generado que tampoco existe; ambos quedan fuera de alcance)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (NFR de latencia), ADR-0003 (auditoría de identidad — distinta a este ADR), ADR-0005 (Postmark, fallos de envío como alarma), ADR-0006 (infraestructura, CloudWatch como almacén de infra-AWS, AWS gestionado), ADR-0007 (monolito modular, events-first, outbox y DLQ), ADR-0008 (`Result<T, DomainError>` — los errores de dominio no inflan tasa 5xx), ADR-0009 (proyección stale, auditoría de autorización — distinta a este ADR), ADR-0010 (CI/CD, dashboard CI/CD distinto al runtime), ADR-0014 (RGPD: IP truncada, userId hasheado, retención 90 días)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre observabilidad runtime. Las veinticuatro sub-decisiones se agrupan en ocho áreas:

- **Instrumentación neutral (D1-D5)** — código de la app: Actuator + Micrometer + OpenTelemetry + Logback estructurado + MDC.
- **Backend de observabilidad gestionado (D6-D9)** — AMP + AMG + X-Ray + CloudWatch Logs, coexistencia con CloudWatch para infra-AWS, sin operación del propio stack, tagging obligatorio.
- **Política de los tres pilares (D10-D13)** — métricas por capa, métricas de negocio, muestreo de trazas, niveles de log.
- **Retención y privacidad (D14-D15)** — retención por tipo y PII en logs.
- **Alertas (D16-D18)** — alertas mínimas, canal con severidades, política anti-ruido.
- **Health y meta-monitoring (D19-D20)** — health checks de la app y canary externo del propio stack.
- **Distinción explícita (D21)** — observabilidad ≠ auditoría.
- **Evolución (D22-D24)** — disparadores para Loki/Tempo dedicados, error tracking, SaaS completo.

| #   | Sub-decisión                                                                       | Capa         |
|-----|------------------------------------------------------------------------------------|--------------|
| D1  | [Spring Boot Actuator + Micrometer](#d1)                                           | Estratégica  |
| D2  | [OpenTelemetry para trazas](#d2)                                                   | Estratégica  |
| D3  | [Logging estructurado JSON con Logback + encoder](#d3)                             | Operativa    |
| D4  | [Identificador de correlación: W3C Trace Context](#d4)                             | Operativa    |
| D5  | [MDC con contexto operativo (`trace_id`, `club_id`, `user_id_hash`, `module`)](#d5)| Operativa    |
| D6  | [Backend gestionado AWS: AMP + AMG + X-Ray + CloudWatch Logs](#d6)                 | Estratégica  |
| D7  | [Coexistencia con CloudWatch para infra-AWS (ADR-0006 D24)](#d7)                   | Estratégica  |
| D8  | [Sin operación del propio stack (todo gestionado)](#d8)                            | Operativa    |
| D9  | [Tagging de telemetría obligatorio](#d9)                                           | Operativa    |
| D10 | [Métricas obligatorias por capa con umbrales](#d10)                                | Operativa    |
| D11 | [Métricas de negocio del MVP](#d11)                                                | Estratégica  |
| D12 | [Política de muestreo de trazas: 100 % MVP → 10 % al escalar + tail errores](#d12) | Operativa    |
| D13 | [Niveles de log y activación de DEBUG/TRACE en incidente](#d13)                    | Operativa    |
| D14 | [Retención: logs 90 d, métricas 13 m, trazas 7 d](#d14)                            | Operativa    |
| D15 | [PII en logs: IP truncada, userId hasheado (cruce ADR-0014 D9)](#d15)              | Operativa    |
| D16 | [Alertas mínimas con umbrales concretos](#d16)                                     | Operativa    |
| D17 | [Canal con severidades (CRITICAL / HIGH / INFO) por email](#d17)                   | Operativa    |
| D18 | [Política anti-ruido: grouping + reenvío 4 h hasta resolver](#d18)                 | Operativa    |
| D19 | [Health checks de Actuator + readiness probes](#d19)                               | Operativa    |
| D20 | [Canary externo mínimo (AWS Synthetics)](#d20)                                     | Operativa    |
| D21 | [Distinción explícita: observabilidad ≠ auditoría](#d21)                           | Estratégica  |
| D22 | [Disparador: Loki/Tempo dedicados si CloudWatch Logs > 30 €/mes](#d22)             | Operativa    |
| D23 | [Disparador: error tracking dedicado > 10 excepciones únicas/semana](#d23)         | Operativa    |
| D24 | [Disparador: SaaS completo o autoalojado pleno](#d24)                              | Operativa    |

## Contexto y problema

Una beta con un club piloto necesita **enterarse cuando algo va mal y poder diagnosticarlo**. Ningún ADR cubría hasta ahora la observabilidad runtime: logging, métricas, trazas, alertas. La comunicación *events-first* (ADR-0007) introduce flujos **asíncronos** entre módulos que conviene poder seguir, y ADR-0001 fija objetivos de latencia que hay que **medir** para saber si se cumplen.

Además, **el ADR-0006 D24 (aceptado) fijó CloudWatch como almacén de logs y métricas en MVP** dejando "la evolución hacia observabilidad más sofisticada" para este ADR. Este ADR debe resolver la coexistencia: qué se queda en CloudWatch y qué pasa a AMP/AMG.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR**. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Stack Spring Boot** (ADR-0001) con Actuator de serie.
- **NFR de latencia p95 < 400 ms** (ADR-0001) — el "qué medir" sale de aquí.
- **Mono-tenant con `club_id`** (ADR-0006 D22) — toda telemetría lleva `club_id`.
- **AWS `eu-west-1` + App Runner + RDS** (ADR-0006 D1/D3/D7) — los pilares del cómputo a observar.
- **CloudWatch como almacén de logs y métricas de plataforma** (ADR-0006 D24) — premisa con la que este ADR debe convivir, no contradecir.
- **Spring Modulith + outbox + retención 30 días** (ADR-0007 D6/D15) — outbox vigilado por métricas.
- **Política de fallos del outbox: 5 reintentos + DLQ + republicación admin** (ADR-0007 D13) — disparador concreto de alarma.
- **Hexagonal con `Result<T, DomainError>`** (ADR-0008 D11) — los errores de dominio **no son excepciones** y **no inflan tasa 5xx**.
- **Política de proyección stale: fail-closed con timeout 60 s** (ADR-0009 D9) — métrica obligatoria de lag por proyección.
- **Auditoría de identidad** (ADR-0003 D15) **y de autorización** (ADR-0009 D15-D17) son distintas a logging operativo — no se mezclan, no van a Loki/CloudWatch (D21).
- **CI/CD GitHub Actions con dashboard mínimo del pipeline** (ADR-0010 D22) — distinto al runtime que cubre este ADR; ambos coexisten.
- **Postmark como email** (ADR-0005) — fallos de envío disparan alarma.
- **RGPD: logs operativos con IP truncada /24 y userId hasheado** (ADR-0014 D9).
- **Retención de logs operativos 90 días** (ADR-0014 D10).
- **Equipo de 4 personas** — premisa de coste de tiempo, no sólo de dinero.

## Requisitos no funcionales

| Dimensión | Valor objetivo |
|---|---|
| **Cobertura de instrumentación** | 100 % de endpoints públicos + 100 % de adaptadores externos (Postmark, RDS) + 100 % de listeners de eventos |
| **Latencia desde evento operativo → alarma disparada** (p95) | < **1 min** |
| **Latencia desde alarma → notificación al canal** (p95) | < **30 s** |
| **Tasa de alertas falsas positivas** | < **5 %** en régimen estable |
| **Disponibilidad del stack de observabilidad** | **mejor que la app**: objetivo 99,9 % (cubierto por AMP/AMG gestionados de AWS) |
| **Retención de logs** | **90 días** (cruce ADR-0014 D10) |
| **Retención de métricas** | **13 meses** (permite comparación interanual) |
| **Retención de trazas** | **7 días** (volumen mayor por petición, valor decreciente con el tiempo) |
| **Coste objetivo del stack de observabilidad** | **< 30 €/mes** a volumen del piloto (AMP + AMG + X-Ray + CloudWatch a este volumen) |

## Drivers de la decisión

- Operar una beta: **detectar y diagnosticar** incidencias rápido.
- **Medir** los requisitos no funcionales de ADR-0001 (latencia, errores).
- *Events-first* (ADR-0007) → flujos asíncronos y proyecciones que conviene trazar y vigilar.
- Equipo de 4 → herramientas que el equipo **pueda manejar sin convertirse en SRE a tiempo parcial**.
- **Proporcionalidad** al volumen del piloto: el coste de operar el stack no puede superar el valor que aporta.
- Coherencia con ADR-0006 D24 (CloudWatch ya elegido para infra-AWS): este ADR debe **coexistir**, no contradecir.

## Opciones consideradas — backend de observabilidad

- **Opción A** — CloudWatch puro (nativo de AWS).
- **Opción B** — Stack autoalojado Grafana + Loki + Prometheus + Tempo.
- **Opción C** — SaaS completo (Grafana Cloud, Datadog).
- **Opción D** — **Stack gestionado AWS**: AMP + AMG + X-Ray + CloudWatch Logs.

### Opción A — CloudWatch puro

- 👍 Mínima operación: App Runner y RDS ya emiten a CloudWatch; cero infraestructura propia; alarmas integradas.
- 👍 Ya elegido por ADR-0006 D24 para plataforma.
- 👎 UX de logs y *dashboards* mediocre; sin trazas distribuidas nativas para apps Java; acoplado a AWS.

### Opción B — Stack autoalojado Grafana

Grafana + Loki + Prometheus + Tempo desplegado en EC2 o Fargate.

- 👍 Potente, **portable**, estándar abierto, sin coste de licencia, excelente UX.
- 👍 Encaja con el principio de portabilidad de ADR-0006 D23.
- 👎 Es un stack que **desplegar, parchear, asegurar y respaldar** — operación significativa a cargo de un equipo de 4.
- 👎 Tres bases de datos distintas (Loki chunks, Prometheus TSDB, Tempo blocks) que respaldar y escalar.

### Opción C — SaaS completo

- 👍 Gran UX, cero infraestructura, muy potente.
- 👎 Coste recurrente que crece con el uso; otro proveedor externo y otro DPA (cruce ADR-0014 D22).
- 👎 Datadog es caro al escalar; Grafana Cloud Free tiene cuotas que el piloto consumirá rápido.

### Opción D — Stack gestionado AWS

- **Amazon Managed Prometheus (AMP)** — Prometheus gestionado, sin operación, escalado automático.
- **Amazon Managed Grafana (AMG)** — Grafana gestionado, SSO + dashboards.
- **AWS X-Ray** — trazas distribuidas con plugin OpenTelemetry, plugin nativo en AMG.
- **CloudWatch Logs** — logs estructurados, plugin nativo en AMG para consultas en CloudWatch Logs Insights.

- 👍 **Cero operación del stack**: ni parches, ni backups, ni escalado manual.
- 👍 **Proporcional al equipo** de 4 personas.
- 👍 Coste muy bajo a este volumen (~15-25 €/mes).
- 👍 La instrumentación sigue **neutral** (OpenTelemetry + Micrometer): si en el futuro la elección cambia, se sustituye el backend sin tocar código.
- 👍 **Coexiste de forma natural** con CloudWatch del ADR-0006 D24: AMG puede consultar CloudWatch Logs directamente; CloudWatch Logs sigue siendo el destino para logs.
- 👎 Lock-in al ecosistema AWS para esta capa. Aceptado: la observabilidad se **reemplaza**, no se migra; cuando cambien las necesidades, el siguiente backend (Grafana Cloud, Datadog, Loki/Tempo autoalojados) consume la misma instrumentación.

## Decisión

**Instrumentación neutral (Opción A heredada del ADR original)** + **Backend gestionado AWS (Opción D)**. Cambia el backend respecto al ADR original (Opción B): operar Loki + Prometheus + Tempo con 4 personas no es proporcional al volumen del piloto. La **instrumentación de código sigue siendo neutral**, así que si en el futuro hace falta cambiar de backend, se cambia el destino (config) sin tocar la app.

Las veinticuatro sub-decisiones desarrolladas a continuación. Siete son **estratégicas** (D1, D2, D6, D7, D11, D21 — los pilares de instrumentación, la elección del backend, la coexistencia con CloudWatch, el set de métricas de negocio y la distinción frente a auditoría); el resto son **operativas** y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — Spring Boot Actuator + Micrometer

La aplicación expone métricas con **Spring Boot Actuator + Micrometer**. Es la integración nativa del stack JVM (ADR-0001) y agnóstica de proveedor:

- Endpoints `/actuator/prometheus`, `/actuator/health`, `/actuator/info`.
- Micrometer publica al **Prometheus exporter**, que AMP consume vía remote write.
- Métricas de JVM (heap, GC, threads), HTTP (`http_server_requests_seconds`), conexiones BD (`hikari_*`) están **on by default**.
- Métricas personalizadas (negocio, dominio) se registran con `MeterRegistry`.

<a id="d2"></a>
### D2 — OpenTelemetry para trazas

Trazas distribuidas con **OpenTelemetry**:

- SDK Java + autoinstrumentación de Spring Boot, JDBC, HTTP client, Kafka (futuro).
- **Exporter OTLP** dirigido a **AWS X-Ray** vía el AWS Distro for OpenTelemetry (ADOT) Collector.
- **Propagación**: W3C Trace Context (D4) entre servicios y entre eventos del outbox (cruce ADR-0007).

Spring Modulith no propaga trace context automáticamente entre eventos; se añade en el listener para no perder trazas en flujos asíncronos.

<a id="d3"></a>
### D3 — Logging estructurado JSON con Logback + encoder

- **Logback** (incluido en Spring Boot) con encoder **JSON estructurado** (`net.logstash.logback.encoder.LogstashEncoder` o equivalente).
- Cada línea de log es un objeto JSON con: `timestamp`, `level`, `logger`, `message`, `thread`, `mdc.*` (D5), `exception` (si aplica).
- Salida a stdout/stderr — App Runner la captura y envía a CloudWatch Logs (D7).
- Sin logs en archivo: la infraestructura efímera de App Runner los perdería.

<a id="d4"></a>
### D4 — Identificador de correlación: W3C Trace Context

- **`traceparent`** header como portador del identificador de traza (estándar W3C Trace Context).
- Spring Boot + OpenTelemetry generan el `traceparent` en el adaptador de entrada y lo propagan a logs y trazas.
- En logs estructurados aparece como `trace_id` y `span_id` (D5).
- **Propagación en eventos**: el listener `@ApplicationModuleListener` (ADR-0007) restaura el contexto de traza del evento publicado para no perder correlación en flujos asíncronos.

<a id="d5"></a>
### D5 — MDC con contexto operativo

Cada línea de log incluye en el MDC (Mapped Diagnostic Context):

| Campo | Origen | Notas |
|-------|--------|-------|
| `trace_id` | OpenTelemetry | Correlación end-to-end (D4) |
| `span_id` | OpenTelemetry | Span actual |
| `club_id` | Resolución del principal (ADR-0009 D6) | Multi-tenant en logs desde el día 1 |
| `user_id_hash` | Hash determinístico con salt anual | NUNCA userId en claro (cruce ADR-0014 D9 / D15) |
| `module` | Anotación del bean o package | Identidad / Seguimiento / Auditoría / etc. |
| `env` | Configuración de entorno | `staging` / `production` |

El MDC se rellena en un filtro Servlet en cada petición HTTP (`HttpMdcFilter`, `shared.observability`) y se propaga en listeners de eventos (`MdcRestorerForEvents`, cruce ADR-0007). Ambos comparten la traducción paquete→esquema de `module` (`ModuleTagResolver`).

`HttpMdcFilter` va registrado tras `SecurityContextHolderFilter` en la cadena de seguridad (`SecurityConfig`), antes de cualquier filtro que pueda rechazar la petición (tope de sesión, gate-check de estado) para que esos rechazos también queden correlados:

- `trace_id`: del `traceparent` W3C entrante si lo hay. No se genera uno propio — no hay SDK de tracing real todavía (D4); si el cliente no manda `traceparent`, `trace_id` queda vacío.
- `club_id` / `user_id_hash`: del `PrincipalProvider` si la petición está autenticada; en rutas anónimas (login, activación, health) `user_id_hash` cae a `"system"`, igual que en el lado de eventos.
- `module`: del controller que Spring MVC va a atender la petición, resuelto vía `HandlerMapping.getHandler(request)` (el mismo mecanismo que usa `DispatcherServlet` internamente, sin invocar el handler) — `"unmatched"` si no hay ruta (404).
- `env`: primer perfil Spring activo.

`span_id` (fila de la tabla de arriba) **no está implementado en ninguno de los dos lados** — ni HTTP ni eventos: `micrometer-tracing-bridge-otel` está en el classpath (dependencia del backend) pero sin exporter configurado, y ni `HttpMdcFilter` ni `MdcRestorerForEvents` lo rellenan hoy. D4 (arriba) también da por hecho un `traceparent` auto-generado por "Spring Boot + OpenTelemetry" que tampoco existe — `HttpMdcFilter` solo **propaga** un `traceparent` entrante, no genera uno nuevo. Ambos quedan fuera de alcance de esta corrección — requieren integrar de verdad un SDK de tracing, no solo el MDC.

<a id="d6"></a>
### D6 — Backend gestionado AWS: AMP + AMG + X-Ray + CloudWatch Logs

| Pilar | Backend | Notas |
|-------|---------|-------|
| Métricas | **Amazon Managed Prometheus (AMP)** | Recibe vía remote write desde Micrometer Prometheus endpoint |
| Logs | **CloudWatch Logs** | Destino nativo de App Runner; consultas vía CloudWatch Logs Insights desde AMG |
| Trazas | **AWS X-Ray** | OpenTelemetry exporta vía ADOT Collector |
| UI / Dashboards | **Amazon Managed Grafana (AMG)** | Plugin nativo de AMP, CloudWatch y X-Ray |

- AMG con SSO via IAM Identity Center (ADR-0006 D27) para el equipo.
- Sin operación del propio stack (D8).
- Coste estimado a volumen del piloto: AMP ~5 €/mes, AMG ~9 €/usuario/mes (~9 € hoy), X-Ray ~3 €/mes, CloudWatch Logs ~5-10 €/mes. Total < **30 €/mes**.

<a id="d7"></a>
### D7 — Coexistencia con CloudWatch para infra-AWS (ADR-0006 D24)

**Resolución de la contradicción aparente con ADR-0006 D24**: ambos ADRs aceptados y **coexisten**.

| Origen de datos | Destino | Visualizado en |
|-----------------|---------|----------------|
| Logs de la app | CloudWatch Logs | AMG (plugin CloudWatch) |
| Logs de App Runner (build/deploy) | CloudWatch Logs | AMG y CloudWatch console |
| Métricas de App Runner | CloudWatch Metrics | AMG (plugin CloudWatch) |
| Métricas internas de RDS | CloudWatch Metrics | AMG (plugin CloudWatch) |
| Slow query log de RDS | CloudWatch Logs | AMG (plugin CloudWatch) |
| Facturación / billing | CloudWatch + AWS Budgets | Email directo (ADR-0006 D26) |
| Métricas de la **aplicación** (Micrometer) | AMP | AMG |
| Trazas | X-Ray | AMG |

AMG es el **panel único** de cara al equipo. CloudWatch sigue siendo el almacén para infra-AWS (cumpliendo ADR-0006 D24); AMP+X-Ray son el almacén para telemetría de aplicación.

<a id="d8"></a>
### D8 — Sin operación del propio stack (todo gestionado)

- **AMP, AMG, X-Ray, CloudWatch Logs**: todos servicios gestionados de AWS. **Cero parches, cero backups, cero escalado manual**.
- El equipo se ocupa de: configurar dashboards y alarmas, mantener la instrumentación.
- No se ocupa de: mantener Loki, Prometheus, Tempo, Grafana ni sus dependencias.

Comparación frente a la decisión original del ADR (autoalojado): el ahorro es N horas/mes que el equipo de 4 personas no puede permitirse perder operando observabilidad.

<a id="d9"></a>
### D9 — Tagging de telemetría obligatorio

Toda métrica, log y traza lleva las dimensiones / labels:

| Dimensión | Valor |
|-----------|-------|
| `env` | `staging` \| `production` |
| `service` | `runcriticon-app` |
| `module` | `identidad` \| `club_taxonomia` \| `seguimiento` \| etc. |
| `club_id` | UUID del club (visible solo para roles autorizados; cuidado con cardinalidad alta — ver más abajo) |
| `version` | tag del commit (ADR-0010 D18) |

**Atención a cardinalidad**: `club_id` puede ser alta cardinalidad si el producto escala a cientos de clubes. En MVP mono-club es trivialmente seguro. **Disparador para revisar**: cuando entren > 50 clubes, evaluar agregaciones para evitar la explosión de series temporales en AMP.

<a id="d10"></a>
### D10 — Métricas obligatorias por capa con umbrales

| Capa | Métrica | Disparador de alarma |
|------|---------|----------------------|
| HTTP | `http_server_requests_seconds` (p95 por endpoint) | > NFR ADR-0001 (**400 ms p95** sostenido 5 min) |
| HTTP | `http_server_requests_total{status=~"5.."}` | > **1 %** sostenido 5 min |
| Eventos | `outbox_pending_events` (size en `event_publication`) | > **100** sostenido 5 min |
| Eventos | `outbox_dlq_events` (reintentos agotados, ADR-0007 D13) | **> 0** (cualquiera) |
| Eventos | `outbox_delivery_seconds` (p95) | > **10 s** sostenido 5 min |
| Listeners | `listener_failures_total` por listener | > **0,1 %** sostenido 10 min |
| Auditoría stale | `projection_lag_seconds` por proyección (ADR-0009 D9) | > **60 s** (fail-closed) |
| Email Postmark | `postmark_send_failures_total` (ADR-0005) | > **5 %** sostenido 5 min |
| BD | `hikari_connections_active`, `hikari_connections_pending` | `pending > 0` sostenido 1 min |
| BD | Slow query log (RDS → CloudWatch) | queries > **1 s** |
| JVM | heap, GC pauses | pauses > **1 s** |

Cada métrica viene con su dashboard en AMG y su alarma (D16-D18).

**Importante** (cruce ADR-0008 D11): los **errores de dominio** que viajan como `Result.Failure(...)` **no son excepciones** y **no inflan la tasa de 5xx**. Devuelven códigos HTTP 4xx (`400`, `403`, `409`) según el caso. La métrica de 4xx vigila exfiltraciones (picos sospechosos) pero no es señal de fallo de la app.

<a id="d11"></a>
### D11 — Métricas de negocio del MVP

Set amplio desde el día 1, para medir adopción del club piloto:

| Métrica | Definición | Cruce |
|---------|------------|-------|
| `magic_links_issued_total` | Magic links emitidos | ADR-0003 D5 |
| `magic_links_activated_total` | Magic links consumidos con éxito | ADR-0003 D5 |
| `magic_links_success_rate` | Ratio activados / emitidos | derivada |
| `invitations_issued_total` | Invitaciones emitidas | ADR-0003 D4 |
| `invitations_accepted_total` | Invitaciones aceptadas | ADR-0003 D4 |
| `invitations_acceptance_rate` | Ratio aceptadas / emitidas | derivada |
| `accounts_activated_total` | Cuentas activadas | ADR-0003 D4 |
| `time_to_activation_seconds` (histograma) | Tiempo entre invitación enviada → cuenta activada | derivada |
| `session_reports_created_total` | Reportes de sesión creados | módulo Seguimiento |
| `dau` (Daily Active Users) | Usuarios con al menos una petición HTTP autenticada/día | derivada |
| `users_per_club` | Usuarios activos por club (preparado para multi-club) | etiqueta `club_id` |

Dashboard dedicado en AMG: *"Adopción del piloto"* con visualización de funnel.

Añadir métricas adicionales requiere PR y revisión (no se añaden por capricho; cada nueva dimensión cuesta cardinalidad y mantenimiento).

<a id="d12"></a>
### D12 — Política de muestreo de trazas: 100 % MVP → 10 % al escalar + tail errores

- **MVP**: **100 % de muestreo** (head sampling). Volumen del piloto (< 100 req/s) lo soporta sin coste apreciable.
- **Cuando se superen 100 req/s sostenidos**: **head sampling al 10 %** + **tail sampling para errores y latencia alta** (cualquier traza con span error o con latencia > p99 se conserva al 100 %).
- ADOT Collector configura el muestreo; cambiar la política es un cambio de configuración, no de código.

<a id="d13"></a>
### D13 — Niveles de log y activación de DEBUG/TRACE en incidente

- **Producción**: nivel **INFO** por defecto.
- **WARN** para situaciones recuperables que merecen atención (rate limit alcanzado, reintento, proyección stale temporal).
- **ERROR** para fallos reales (Postmark caído, evento en DLQ, excepción del framework).
- **DEBUG / TRACE**: **off** por defecto. Activable por configuración (`logging.level.*` en SSM, ADR-0013) en incidente, sin redespliegue.
- `staging`: **DEBUG** activable libremente para el desarrollo.

<a id="d14"></a>
### D14 — Retención: logs 90 d, métricas 13 m, trazas 7 d

| Tipo | Retención | Almacén | Justificación |
|------|-----------|---------|---------------|
| Logs operativos | **90 días** | CloudWatch Logs | Cruce ADR-0014 D10 |
| Métricas | **13 meses** | AMP | Comparación trimestre a trimestre y año a año |
| Trazas | **7 días** | X-Ray | Volumen mayor, valor decreciente con el tiempo |

Configurado en IaC (Terraform). Cambios requieren PR.

<a id="d15"></a>
### D15 — PII en logs: IP truncada, userId hasheado

Cruce explícito con ADR-0014 D9:

- **IP**: truncada a **/24 IPv4** (último octeto → 0) o **/48 IPv6** en logs operativos.
- **userId**: hash determinístico con salt (rotado anualmente). Permite agrupar peticiones del mismo usuario sin reidentificar.
- **Cuerpos de peticiones HTTP** con PII: **no se loguean** salvo en `DEBUG`/`TRACE` activado por incidente (D13), y con tiempo limitado de logging extendido.
- **NUNCA**: contraseñas, tokens, magic links, datos de salud completos.

El patrón aplica a TODOS los logs que se exportan a CloudWatch Logs. La auditoría de identidad (ADR-0003 D15) y de autorización (ADR-0009 D17) son separadas y siguen sus propias reglas (D21).

<a id="d16"></a>
### D16 — Alertas mínimas con umbrales concretos

Las alertas mínimas iniciales con sus umbrales viven en D10 (tabla de métricas obligatorias). Resumen:

- **App caída / health check fallido**
- **Tasa 5xx > 1 % sostenido 5 min**
- **Latencia p95 > 400 ms sostenido 5 min** (NFR ADR-0001)
- **CPU > 80 % sostenido 10 min** | memoria > 80 % | conexiones BD pending > 0
- **Outbox DLQ con eventos** (> 0)
- **Outbox pending > 100 sostenido 5 min**
- **Proyección stale > 60 s** (ADR-0009 D9)
- **Postmark fallos > 5 % sostenido 5 min**

Configuradas en AMG (Grafana Alerting). Cada alarma documenta su runbook (`docs/runbooks/alarmas/*.md`).

<a id="d17"></a>
### D17 — Canal con severidades (CRITICAL / HIGH / INFO) por email

Severidades y canal:

| Severidad | Disparadores típicos | Canal MVP | Evolución futura |
|-----------|----------------------|-----------|------------------|
| **CRITICAL** | App caída, BD inaccesible, outbox DLQ con eventos | Email inmediato a `alertas-runcriticon@runcriticon.com` | + SMS / PagerDuty cuando entre SLA o equipo > 4 |
| **HIGH** | Latencia p95 > NFR, tasa 5xx > 1 %, proyección stale, Postmark fallos | Email con grouping (D18) | + Slack cuando equipo > 4 |
| **INFO** | Métricas de negocio fuera de banda, recordatorios | Solo dashboard, no notifica | — |

En MVP: **solo email**. **Disparador para añadir Slack o PagerDuty**: equipo > 4 personas o > 5 alarmas/semana.

<a id="d18"></a>
### D18 — Política anti-ruido: grouping + reenvío 4 h hasta resolver

Grafana Alerting agrupa alertas por `alertname`. Política:

- **Grouping** por `alertname` (todas las instancias de la misma alarma viajan juntas).
- **Tras la primera notificación**, no se reenvía más de **1 vez cada 4 horas** hasta que la alarma se resuelva.
- **Notificación de resolución** (estado OK) activada — útil para saber que ya no hay que mirar.
- **Mantenimiento programado**: ventana de silenciado configurable desde AMG para no recibir alertas durante despliegues planeados.

Sin esta política, una alerta ruidosa silenciaría emocionalmente al equipo en días.

<a id="d19"></a>
### D19 — Health checks de Actuator + readiness probes

- **`/actuator/health`** — composite check de Spring Boot Actuator: BD accesible, disk space, custom checks (Postmark reachable, AMP reachable).
- **App Runner** usa `/actuator/health` para decidir si la instancia está sana (ADR-0006 D3) y para reciclarla en caso de fallo persistente.
- **Readiness check**: separa `liveness` (la JVM responde) de `readiness` (los dependientes están listos). Útil durante el arranque de Spring Boot (10-20 s).
- Endpoints expuestos con **seguridad mínima** (no se exponen detalles si no está autenticado el caller).

<a id="d20"></a>
### D20 — Canary externo mínimo (AWS Synthetics)

El propio stack de observabilidad necesita un vigilante externo: si todo cae a la vez (incluida la app y AMG), nadie avisa.

- **AWS Synthetics**: un canary mínimo que cada **5 minutos** hace `GET /actuator/health` desde fuera de la VPC.
- Si falla 2 ejecuciones consecutivas, alarma directa por email (sin pasar por AMG, que podría estar caído).
- Coste mínimo (~2 €/mes).

Cubre el "more reliable than the app" del ADR original: el canary externo es independiente del stack que vigila el resto.

<a id="d21"></a>
### D21 — Distinción explícita: observabilidad ≠ auditoría

Tres registros distintos que **no se mezclan**:

| Registro | Propósito | Almacén | Retención | ADR |
|----------|-----------|---------|-----------|-----|
| **Observabilidad** (este ADR) | Detectar y diagnosticar incidentes operativos | CloudWatch Logs / AMP / X-Ray | logs 90 d, métricas 13 m, trazas 7 d | 0011 |
| **Auditoría de identidad** | Investigar incidentes de cuenta | `identidad.evento_auditoria` (Postgres) | 12 meses | 0003 D15 |
| **Auditoría de autorización** | Investigar acceso a datos sensibles y denegaciones | `auditoria.evento` (Postgres, módulo `auditoria`) | 24 meses | 0009 D15-D17 |

**No se envían eventos de auditoría a CloudWatch Logs ni a AMP**. La auditoría vive en su propia tabla, con su propia consulta, su propia retención y su propio borrado mixto al ejercer el olvido (ADR-0014 D6).

<a id="d22"></a>
### D22 — Disparador: Loki/Tempo dedicados si CloudWatch Logs > 30 €/mes

CloudWatch Logs cuesta más que Loki autoalojado a volumen alto. Disparador para mover a un Loki / Tempo gestionado (Grafana Cloud) o autoalojado:

- **Coste mensual de CloudWatch Logs > 30 €/mes** sostenido 2 meses.
- **O** volumen de logs > 50 GB/mes.

Cuando se activa, la migración es de **destino** (cambio de configuración del log appender), no de **código** — la instrumentación es neutral.

<a id="d23"></a>
### D23 — Disparador: error tracking dedicado > 10 excepciones únicas/semana

**Sentry o GlitchTip** entra en el stack cuando:

- El triage de excepciones por logs sea claramente incómodo.
- O se detecten **> 10 excepciones únicas / semana** durante 4 semanas consecutivas.

Hasta entonces, las excepciones se rastrean por logs estructurados (D3) + métrica de tasa de errores (D10).

<a id="d24"></a>
### D24 — Disparador: SaaS completo o autoalojado pleno

- **SaaS completo (Datadog o equivalente)**: cliente con SLA contractual > 99,5 % **o** equipo > 8 personas. Datadog tiene gran UX pero el coste crece rápido.
- **Autoalojado pleno (Grafana + Loki + Prometheus + Tempo en EC2/EKS)**: si necesidades de portabilidad o personalización avanzada lo justifican Y hay capacidad operativa (> 1 SRE dedicado).

Hasta entonces, AMP + AMG + X-Ray + CloudWatch Logs es la combinación correcta para el volumen y el equipo del piloto.

## Consecuencias

### Positivas

- El equipo ve qué pasa en producción: logs estructurados, métricas, trazas y alertas en AMG.
- **Cero operación del stack**: AMP + AMG + X-Ray + CloudWatch Logs son todos gestionados.
- Instrumentación neutral (Actuator + Micrometer + OpenTelemetry) → cambiar de backend o de nube no toca la app.
- **Coexistencia limpia con ADR-0006 D24**: CloudWatch para infra-AWS, AMP/AMG/X-Ray para aplicación.
- Se **verifican** los NFR de ADR-0001 (latencia, errores) con datos reales.
- Métricas de negocio del MVP miden adopción real del piloto desde el día 1.
- Distinción clara entre observabilidad y auditoría (D21) — no se mezclan en almacén ni en retención.
- PII protegida en logs (D15) sin penalizar capacidad forense de incidentes.
- Disparadores explícitos para evolución (D22-D24) — el ADR no se queda en "lo mejoramos después" sin condiciones.

### Negativas / coste asumido

- **Lock-in** al ecosistema AWS para esta capa. Aceptado: la observabilidad se reemplaza, no se migra; cuando cambien las necesidades, la instrumentación neutral permite cambiar el backend sin tocar la app.
- Coste recurrente del stack (~15-30 €/mes a volumen del piloto). Mucho menor que el coste de tiempo de operar el stack autoalojado.
- AMG con coste por usuario (~9 €/usuario/mes). Para un equipo de 4: ~36 €/mes en AMG. Aceptable.
- CloudWatch Logs es más caro que Loki autoalojado a alto volumen — disparador para revisar en D22.
- **Cambio respecto al ADR original**: el equipo no aprende a operar Loki/Prometheus/Tempo en MVP. La curva de aprendizaje se retrasa hasta el disparador de D22/D24.

### Riesgos y mitigaciones

- **Pérdida de visibilidad por instrumentación incompleta** → cobertura NFR del 100 % verificada por convención de código + revisión PR.
- **Coste descontrolado de CloudWatch Logs** → disparador concreto en D22 + alarmas de facturación del ADR-0006 D26.
- **Cardinalidad alta de `club_id`** cuando entren más clubes → revisión a 50 clubes (D9).
- **Falsa sensación de seguridad por alarmas mal calibradas** → política anti-ruido (D18) + tasa de falsos positivos como NFR.
- **Caída del propio AMG** → canary externo (D20) garantiza notificación incluso si AMG está caído.
- **Mezcla observabilidad / auditoría** → distinción explícita (D21) + revisión arquitectónica en cada PR que loguea.
- **Picos de excepciones únicos no triagedos** → disparador D23 para introducir error tracking.

## Notas

- Las premisas heredadas son **invariantes de este ADR**: si cambian (especialmente ADR-0006 D24, ADR-0014 D9/D10, ADR-0007 D13), este ADR se revisita.
- **Variante autoalojada Grafana + Loki + Prometheus + Tempo** queda documentada como evolución futura (D24) si las necesidades de control o coste lo justifican.
- **Variante SaaS completo (Datadog, New Relic)** queda como evolución (D24) si entran clientes con SLA o el equipo crece.
- **Error tracking dedicado (Sentry / GlitchTip)** entra cuando se cumpla el disparador de D23.
- **Slack / PagerDuty para alertas** entra cuando se cumpla el disparador de D17.
- **Revisión periódica**: este ADR se revisa al **primer mes** con el club piloto (ajuste de umbrales reales) y luego cada **6 meses** o cuando un disparador específico se active.
- **Reorganización del 2026-05-29 (Nivel 1)**: el ADR se reestructura con índice de sub-decisiones (párrafo introductorio + tabla), premisas heredadas, NFRs explícitos, numeración D1-D24 con anchors. **Cambio sustantivo**: del stack autoalojado Grafana+Loki+Prometheus+Tempo al **stack gestionado AWS** (AMP + AMG + X-Ray + CloudWatch Logs) por proporcionalidad al equipo de 4 personas. Resolución de la contradicción aparente con ADR-0006 D24 mediante coexistencia explícita. Decisiones nuevas o explicitadas: identificador de correlación W3C Trace Context (D4), MDC operativo (D5), métricas obligatorias por capa con umbrales (D10), métricas de negocio del MVP (D11), política de muestreo de trazas (D12), niveles de log con activación en incidente (D13), retención por tipo (D14), PII en logs (D15), severidades de alertas (D17), política anti-ruido (D18), canary externo (D20), distinción observabilidad vs auditoría (D21), disparadores para evolución (D22-D24).
