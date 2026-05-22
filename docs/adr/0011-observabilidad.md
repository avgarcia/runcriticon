# ADR-0011 — Observabilidad

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (NFR de latencia), ADR-0006 (infraestructura, portabilidad), ADR-0007 (monolito modular, events-first), ADR-0010 (CI/CD)

## Contexto y problema

Una beta con un club piloto necesita **enterarse cuando algo va mal y poder diagnosticarlo**. Ningún ADR cubría hasta ahora la observabilidad: logging, métricas, trazas, alertas. Además, la comunicación *events-first* (ADR-0007) introduce flujos **asíncronos** entre módulos que conviene poder seguir, y ADR-0001 fija objetivos de latencia (p95 de la API < 400 ms) que hay que **medir** para saber si se cumplen.

Hay que decidir **cómo se observa el sistema en producción**.

## Drivers de la decisión

- Operar una beta: **detectar y diagnosticar** incidencias rápido.
- **Medir** los requisitos no funcionales de ADR-0001 (latencia, errores).
- *Events-first* (ADR-0007) → flujos asíncronos y proyecciones que conviene trazar y vigilar.
- **Portabilidad** (ADR-0006): no atar la observabilidad a una nube concreta.
- Equipo de 4 → herramientas que el equipo pueda manejar.

## Opciones consideradas — backend de observabilidad

- **Opción A** — CloudWatch (nativo de AWS).
- **Opción B** — Stack autoalojado basado en Grafana.
- **Opción C** — SaaS de observabilidad (Grafana Cloud, Datadog…).

### Opción A — CloudWatch

- 👍 Mínima operación: App Runner y RDS ya emiten a CloudWatch; cero infraestructura propia; alarmas integradas.
- 👎 UX de logs y *dashboards* mediocre; acoplado a AWS.

### Opción B — Stack autoalojado basado en Grafana

Grafana + Loki (logs) + Prometheus (métricas) + Tempo (trazas).

- 👍 Potente, **portable**, estándar abierto, sin coste de licencia, excelente UX.
- 👍 Encaja con el principio de portabilidad de ADR-0006.
- 👎 Es un stack que **desplegar, parchear, asegurar y respaldar** — operación a cargo del equipo.

### Opción C — SaaS de observabilidad

- 👍 Gran UX, cero infraestructura, muy potente.
- 👎 Coste recurrente que crece con el uso; otro proveedor externo.

## Decisión

### Instrumentación — neutral de proveedor

La aplicación se instrumenta con **Spring Boot Actuator + Micrometer** (métricas y *health*) y **OpenTelemetry** (trazas). Es instrumentación **neutral**: no depende del backend. Cambiar de backend o de nube afecta solo al *exporter*, nunca al código de la aplicación.

### Backend — Opción B, stack autoalojado basado en Grafana

**Grafana + Loki + Prometheus + Tempo**, autoalojado por el equipo. Se valoró una variante gestionada (Amazon Managed Grafana + Managed Prometheus) y se descartó: se prioriza la **portabilidad total** y el control del stack. CloudWatch (A) acopla a AWS; el SaaS (C) es coste sin necesidad a esta escala.

Los tres pilares:

- **Logs → Loki**. Logging **estructurado** (JSON), con un **identificador de correlación** por petición que permite seguir una operación de punta a punta, incluidos los flujos asíncronos por eventos.
- **Métricas → Prometheus**. Métricas de la aplicación (latencia, tasa de errores, uso de recursos) y de negocio básicas.
- **Trazas → Tempo**. Trazado de peticiones y de los flujos *events-first* entre módulos.

### Seguimiento de errores

**Sin herramienta dedicada** en el MVP. Los errores son **logs de error estructurados en Loki**, con una **métrica de tasa de errores** en Prometheus y **alertas en Grafana**. Una herramienta dedicada de *error tracking* (Sentry/GlitchTip) queda como incorporación posterior si el triage de excepciones se vuelve incómodo.

### Alertas

Alertas con **Grafana Alerting**, dirigidas al equipo (email; otros canales más adelante). Política de alertas mínima:

- La aplicación no responde o el *health check* falla.
- Tasa de errores 5xx por encima de un umbral.
- Latencia p95 por encima del objetivo de ADR-0001.
- Recursos (CPU/memoria/disco) o la base de datos en apuros.
- **Eventos sin procesar atascados** en el *outbox* de Spring Modulith — señal de un consumidor caído (relevante por events-first).

### Health checks

Los *endpoints* de salud de Spring Boot Actuator; App Runner los usa para decidir si una instancia está sana (ADR-0006).

## Consecuencias

### Positivas

- El equipo ve qué pasa en producción: logs, métricas, trazas y alertas.
- Instrumentación neutral → cambiar de backend o de nube no toca la app.
- Stack portable y de estándar abierto, sin coste de licencia.
- Se pueden **verificar** los NFR de ADR-0001 con datos reales.

### Negativas / coste asumido

- El stack de observabilidad es **infraestructura que el equipo opera**: desplegar, parchear, asegurar, respaldar.
- Debe ser **más fiable que la propia app** — es lo que avisa cuando algo se rompe.

### Riesgos y mitigaciones

- **El propio stack de observabilidad se cae** → desplegarlo aparte de la app; un *health check* externo mínimo que vigile que el stack vive.
- **Operación que sobrecarga a un equipo de 4** → empezar con una configuración simple; si la carga pesa, se reconsidera la variante gestionada o un SaaS.
- **Coste de almacenamiento de logs/métricas** → políticas de retención ajustadas desde el principio.

## Notas

- La variante **gestionada** del ecosistema Grafana (Amazon Managed Grafana + Managed Prometheus) queda documentada como alternativa si la operación del stack autoalojado resulta demasiado carga.
- Una herramienta dedicada de *error tracking* (Sentry/GlitchTip) es una incorporación posterior, no del MVP.
