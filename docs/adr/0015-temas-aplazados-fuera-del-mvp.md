# ADR-0015 — Temas de arquitectura aplazados fuera del MVP

- **Estado**: Aceptado
- **Fecha**: 2026-05-22 · revisado 2026-05-30 (reorganización Nivel 1: premisas heredadas, índice de aplazamientos, numeración A1-A3 con anchors; **reorientación a índice maestro consolidado**: incorporación de tabla maestra con todos los aplazamientos con disparadores documentados en otros ADRs aceptados (~25 entradas); **eliminación de entradas obsoletas**: i18n y objetivo WCAG salen porque ADR-0012 D9 y D6-D8 los decidieron activamente; incorporación de cifras concretas en disparadores donde tiene sentido) · **aceptado 2026-05-30**
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: todos los ADRs aceptados que contienen aplazamientos con disparador (ADR-0001, ADR-0003, ADR-0005, ADR-0006, ADR-0009, ADR-0010, ADR-0011, ADR-0012, ADR-0013, ADR-0014); ADR-0008 (lenguaje ubicuo); `docs/formacion/tipos-de-base-de-datos.md`

## Índice de aplazamientos

Este ADR cumple dos funciones:

1. **Aplazamientos consolidados aquí (A1-A3)** — temas que **no tienen sub-decisión propia** en otro ADR pero que se aplazan conscientemente con disparador concreto.
2. **Tabla maestra** — vista consolidada de **todos los aplazamientos** documentados en los demás ADRs aceptados, con su disparador y su cruce al ADR origen. Convierte este ADR en el **mapa único** para responder a *"¿qué queda fuera del MVP y cuándo se reabre?"*.

| #   | Aplazamiento consolidado aquí                                                      | Origen       |
|-----|------------------------------------------------------------------------------------|--------------|
| A1  | [Versionado de la API](#a1)                                                        | Propio       |
| A2  | [Estrategia de caché de aplicación](#a2)                                           | Propio + cruce ADR-0003 D10, ADR-0006 D4 |
| A3  | [Soporte de zonas horarias](#a3)                                                   | Propio       |

Tabla maestra de los aplazamientos cubiertos en otros ADRs en la sección [Aplazamientos documentados en otros ADRs](#aplazamientos-documentados-en-otros-adrs).

## Contexto y problema

La auditoría de coherencia de los ADRs identificó varios temas de arquitectura que **se han dejado deliberadamente fuera del MVP**. Mantenerlos sin registrar tiene dos riesgos:

- Que se reabran como si fueran **olvidos** — o, peor, que alguien los "resuelva" por su cuenta sin ver que la omisión era intencionada.
- Que el equipo no tenga un **mapa único** de qué queda fuera del MVP y cuándo se reabre — la información está dispersa en 14 ADRs.

Este ADR cubre ambas: registra los aplazamientos propios como **no-decisiones conscientes** (A1-A3) y consolida los disparadores documentados en otros ADRs como tabla maestra.

## Premisas heredadas (no se revisan en este ADR)

- **Alcance MVP**: mono-club, ~550 usuarios, equipo de 4 personas (ADR-0001).
- **Lenguaje ubicuo en castellano** (ADR-0008 D2).
- **Stack cerrado**: Spring Boot + Angular + PostgreSQL + AWS gestionado (ADR-0001, ADR-0004, ADR-0006).
- **Patrón "no-decisión consciente"**: cada aplazamiento documenta por qué se aplaza + situación por defecto + disparador concreto de reapertura.

## Decisión

Se documentan los siguientes temas como **aplazados de forma consciente**. No son huecos: son alcance recortado a propósito para un MVP mono-club con un equipo de 4. Tres se desarrollan aquí (A1-A3); el resto vive en sus ADRs respectivos y se referencia en la tabla maestra.

<a id="a1"></a>
### A1 — Versionado de la API

- **Por qué se aplaza**: en el MVP hay **un único cliente** (la SPA Angular), en un monorepo, con el contrato OpenAPI compartido (ADR-0001) y el cliente HTTP generado desde ese contrato (ADR-0012 D12). No hay consumidores externos a los que versionar.
- **Situación por defecto**: el contrato OpenAPI evoluciona junto al código, en el mismo PR. Cambios *breaking* del contrato son PR coordinada backend+frontend (ADR-0012 D12). No hay rutas `/v1/`, `/v2/`; los endpoints son la versión vigente.
- **Disparador para reabrir**: cualquiera de los siguientes:
  - Aparece un **cliente externo** (app nativa, SDK público, integración de terceros).
  - Se expone la API a **partners** con contrato versionado.
  - Entra una **app móvil nativa** que se libera con cadencia distinta a la SPA.

<a id="a2"></a>
### A2 — Estrategia de caché de aplicación

- **Por qué se aplaza**: la carga del MVP es baja (~550 usuarios, ADR-0001); PostgreSQL bien indexado va sobrado para los NFR de latencia (p95 < 400 ms, ADR-0001). Una caché de aplicación añade complejidad sin valor proporcional.
- **Situación por defecto**: **sin caché de aplicación**. PostgreSQL absorbe la carga. La caché de páginas/estáticos vive en App Runner por defecto. Redis ya está anticipado como adición futura para sesión compartida (ADR-0003 D10 + ADR-0006 D4) y como caché potencial (ver nota en `docs/formacion/tipos-de-base-de-datos.md`).
- **Disparador para reabrir**:
  - **Latencia p95 de un endpoint > 800 ms sostenida durante 1 semana** sin causa identificada en BD.
  - **Autoescalado de App Runner llega a `max=3`** sostenido (cruce ADR-0006 D4) — implica activar Redis ya para sesión, y aprovechar para caché.
  - **Costes de RDS suben** desproporcionadamente y la caché aliviaría el patrón de acceso.

Cuando se active, la caché entra como ElastiCache Redis (ADR-0006 D4) — coherente con el ecosistema y compartida con Spring Session.

<a id="a3"></a>
### A3 — Soporte de zonas horarias

- **Por qué se aplaza**: el MVP es un club en una única zona horaria (España, Europe/Madrid).
- **Situación por defecto** (**invariante de diseño desde el día 1**):
  - **Fechas y horas se almacenan en UTC** en la base de datos (PostgreSQL `TIMESTAMPTZ`).
  - **Se presentan en la zona del club** (Europe/Madrid en MVP) en la UI.
  - **No se construye soporte multi-zona**: ningún campo de "zona horaria del club" en la BD del MVP.
  - **Respeto de la invariante**: la guía de estructura de módulo (`docs/arquitectura/estructura-de-un-modulo.md`) lo recoge; revisión de código vigila que ningún campo de fecha entre como `TIMESTAMP` (sin zona).
- **Disparador para reabrir**:
  - **Multi-club con clubes en husos horarios distintos** (España + Latinoamérica, por ejemplo).
  - **Entrenadores o alumnos que viajan** y quieren ver horarios en su zona local — fuera del MVP por ser caso minoritario.

## Aplazamientos documentados en otros ADRs

Tabla maestra consolidada de **todos** los aplazamientos del proyecto con disparador concreto en otro ADR. **No se duplican las decisiones**: el ADR origen tiene la autoridad; esta tabla es solo índice navegable.

### Identidad y autorización

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Multi-rol por usuario | Un solo rol por usuario; "admin que también entrena" se resuelve con dos cuentas | Aparece el primer caso real que rompa el modelo de dos cuentas | ADR-0003 D2 + ADR-0009 notas |
| Login con Google (OAuth2) | Solo contraseña + magic link en MVP | Demanda real o cliente que lo exige | ADR-0003 D5 |
| MFA | Sin MFA en MVP | Cliente con datos más sensibles o exigencia regulatoria | ADR-0003 D5 |
| Solicitud de acceso + aprobación | Invite-only puro (alguien con autoridad crea, usuario activa) | Crecimiento del producto donde la delegación a entrenadores ya no escala | ADR-0003 D3 |
| Logout de todos los dispositivos sin admin | Pendiente de análisis; cambio de contraseña (D7) cubre el caso "robo de contraseña" | Demanda real o equipo con tiempo para diseñarlo | ADR-0003 D11 |
| Comprobación HaveIBeenPwned (k-anonymity) al fijar contraseña | Sin verificación contra filtraciones; solo longitud (12-128), datos personales y no-reutilización (histórico de 5) | Cliente con datos más sensibles o exigencia regulatoria, **o** primer incidente real de credential stuffing contra una cuenta del club | ADR-0003 D6 |
| Matriz de autorización configurable | Matriz fija en código (núcleo compartido) | Primer cliente que pide un rol propio que no encaja en `admin/entrenador/alumno`, **o** segundo club piloto con organización distinta | ADR-0009 D6 + notas |
| Rol de soporte interno | Sin rol propio; operación fuera de la aplicación | Segundo club piloto **o** primera incidencia donde el soporte necesite vista de aplicación sin admin disponible | ADR-0009 D19 |

### RGPD

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Tratamiento de menores | MVP no trata menores; club piloto declara solo adultos | Entra un club con menores **o** primera solicitud de alta de menor | ADR-0014 D17 |
| DPO formal | Sin DPO formal; análisis documentado de no aplicación | > 1 000 usuarios totales **o** "observación sistemática a gran escala" **o** recomendación legal | ADR-0014 D21 |
| Self-service de export RGPD | Runbook manual del responsable | Segundo club **o** > 5 solicitudes/mes durante 2 meses consecutivos | ADR-0014 D12 |

### Infraestructura

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Spring Session compartida en Redis | Sesión en memoria con `min=1` en App Runner | Al aumentar `min` a 2 o más en App Runner | ADR-0003 D10 + ADR-0006 D4 |
| ElastiCache para caché de aplicación | Sin caché de aplicación (ver A2) | Ver A2 | ADR-0006 D4 + A2 |
| Multi-AZ RDS | Single-AZ en MVP | Segundo club **o** ~500 usuarios activos sostenidos durante un mes | ADR-0006 D10 |
| ECS Fargate (en lugar de App Runner) | App Runner | Necesidad de control de red avanzado, **o** coste sostenido > 200 €/mes en App Runner, **o** límites de App Runner que aprieten | ADR-0006 D5 |
| CloudFront delante de App Runner | Sin CDN; App Runner sirve directamente | Latencia p95 Madrid > 500 ms sostenida 2 semanas, **o** DDoS detectado, **o** coste de salida > 30 €/mes | ADR-0006 D17 |
| Backups cross-region | Sin cross-region; backups locales 30 días | Cliente con SLA contractual > 99,5 % | ADR-0006 D9, D29 |

### Email

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Migración a Amazon SES | Postmark como proveedor | Volumen sostenido > 50 000 emails/mes durante 2 meses consecutivos **o** coste mensual de Postmark > 100 € | ADR-0005 D15 |
| Templates server-side en Postmark | Plantillas en código, versionadas en repo | Rechazado por D3/D7 (aislar tras puerto, sin lock-in). Reapertura requiere nuevo ADR | ADR-0005 D7 |

### Configuración y secretos

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| AWS Secrets Manager para rotación automática | SSM Parameter Store con rotación manual | Equipo > 4 personas **o** incidente con secretos **o** cliente con regulación específica | ADR-0013 D16 |
| KMS Customer Managed Key (CMK) | KMS managed `aws/ssm` | Auditoría externa **o** cliente con regulación específica | ADR-0013 D17 |
| Vault / Doppler para multi-cloud | SSM Parameter Store en AWS | Multi-cloud real **o** > 50 secretos **o** necesidad de secretos dinámicos | ADR-0013 D18 |

### Observabilidad

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Loki / Tempo dedicados (en lugar de CloudWatch Logs / X-Ray) | CloudWatch Logs + X-Ray gestionados | Coste mensual de CloudWatch Logs > 30 €/mes sostenido 2 meses **o** volumen > 50 GB/mes | ADR-0011 D22 |
| Error tracking dedicado (Sentry / GlitchTip) | Errores en logs estructurados + métrica de tasa | > 10 excepciones únicas/semana durante 4 semanas consecutivas | ADR-0011 D23 |
| SaaS completo (Datadog) | Stack gestionado AWS (AMP + AMG + X-Ray) | Cliente con SLA contractual > 99,5 % **o** equipo > 8 personas | ADR-0011 D24 |
| Slack / PagerDuty para alertas | Email a lista de distribución | Equipo > 4 personas **o** > 5 alarmas/semana | ADR-0011 D17 |
| Catálogo completo de métricas de negocio de identidad (magic links, invitaciones, time-to-activation, DAU, users_per_club) | Solo `identidad.accounts.activated` implementada | Necesidad real de esas métricas en el dashboard del piloto | ADR-0011 D11, `observabilidad-por-modulo.md` §7 |

### Frontend

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| NgRx por feature | Angular Signals + servicios | Feature crece en complejidad y Signals + servicios se vuelve ilegible (decisión por feature, no global) | ADR-0012 D16 |
| `@ngx-translate` (carga dinámica de traducciones) | `$localize` estándar Angular con extracción | Necesidad de idiomas distintos por club en multi-tenant | ADR-0012 D9 |
| WCAG 2.2 | WCAG 2.1 AA en pantallas críticas | Adopción generalizada del estándar 2.2 | ADR-0012 nota |
| Tailwind u otro utility-first | Solo Material 3 + SCSS con ámbito | Rechazado explícitamente (D5 — un solo paradigma). Reapertura requiere nuevo ADR | ADR-0012 D5 |
| App móvil nativa | Web responsive única plataforma | Demanda real validada en discovery | `vision.md` + ADR-0001 |
| `features/{club,salud,planificacion}/` y `layouts/` | Solo existe `features/identidad/`; sin shell/navegación propia (rutas planas en `app.routes.ts`) | Arranca la construcción de pantallas de club, salud o planificación; o aparece una segunda pantalla que necesite shell/navegación compartida | ADR-0012 D10 |

### CI/CD

| Tema | Situación por defecto | Disparador | Origen |
|------|------------------------|------------|--------|
| Mutation testing en cada PR | Nocturno, no por PR | Capacidad de runners suficiente para no penalizar la cadencia de PRs | ADR-0010 D9 |
| CODEOWNERS | Sin CODEOWNERS — equipo muy pequeño | Equipo > 4 personas con responsabilidades diferenciadas | ADR-0010 D20 |
| Umbrales de cobertura por capa (Kover/Istanbul, 90/80/60 % bloqueante) | Sin plugin ni umbral — `collectCoverage` activo en frontend sin `coverageThreshold` (decisión ya documentada en `jest.config.js`); backend sin Kover | Cierre de H1, o una regresión de cobertura real que un umbral hubiera cazado — medir la cobertura real por capa antes de fijar el umbral inicial | ADR-0010 D13 |

## Aplazamientos retirados

Las siguientes entradas existían en el ADR-0015 original y **se retiran** porque ADRs aceptados después tomaron una decisión activa sobre ellas:

| Entrada retirada | Por qué ya no aplica | Donde vive ahora |
|------|----------------------|------------------|
| **Internacionalización (i18n)** | No es aplazamiento ambiguo: ADR-0012 D9 decidió "castellano único en MVP, **preparado con `$localize`**" | ADR-0012 D9 |
| **Objetivo formal de accesibilidad (WCAG)** | Hay objetivo formal: ADR-0012 D6 fija **WCAG 2.1 AA en pantallas críticas**, ADR-0012 D7 lo verifica automáticamente con axe-core en CI, ADR-0012 D8 obliga a teclado | ADR-0012 D6-D8 |

Quien busque información sobre i18n o WCAG va directamente a ADR-0012.

## Consecuencias

### Positivas

- Los aplazamientos dejan de ser ambigüedades: queda escrito que están fuera del MVP **a propósito**, con disparador concreto y cifras donde se puede.
- **Vista consolidada**: el equipo tiene un solo lugar donde mirar para responder *"¿qué queda fuera y cuándo se reabre?"*.
- **Coherencia con ADRs aceptados**: las entradas retiradas (i18n, WCAG) ya no generan confusión.
- **Cifras concretas en disparadores** (50 000 emails/mes, > 500 ms latencia, > 30 €/mes coste): evitan el "lo veremos cuando duela" sin medida.
- **Cada aplazamiento tiene un ADR autoridad** (origen): la tabla maestra es índice navegable, no fuente de verdad duplicada.

### Negativas / coste asumido

- **Mantenimiento del índice**: cada ADR nuevo o revisado que añada un aplazamiento requiere actualizar la tabla maestra de este ADR. Mitigado: el patrón es estable y la tabla cabe en una PR.
- **Respeto de la situación por defecto** (A1-A3): p. ej. almacenar siempre las fechas en UTC desde el día 1, aunque no haya multi-zona, para no tener que retrofitear (A3).

### Riesgos y mitigaciones

- **Que un tema aplazado se reabra como urgencia tardía** → cada entrada tiene un disparador de reapertura explícito; revisarlos al planificar cada evolución.
- **Que la "situación por defecto" se incumpla** (p. ej. fechas en hora local en lugar de UTC en A3) → revisión de código + guía de estructura de módulo + ArchUnit cuando sea aplicable.
- **Que la tabla maestra se desactualice** respecto a los ADRs origen → revisión periódica (Notas) y cada PR que toca un aplazamiento existente añade revisar el cruce aquí.
- **Que un aplazamiento de la tabla maestra cambie su disparador en su ADR origen y no se sincronice aquí** → la verdad vive en el ADR origen; este es índice. Cruce a versión del ADR/commit puede mitigarlo si se hace crítico.

## Notas

- **Este ADR es una lista viva**: se amplía cuando aparezcan nuevos aplazamientos en ADRs futuros o cuando se decida aplazar algo que no estaba en el radar.
- Las **decisiones de alcance** ya recogidas en otros ADRs no se desarrollan aquí: este ADR las **indexa**, no las re-decide.
- **Aplazamientos cubiertos por `vision.md`** (sin ADR específico): app móvil nativa (referenciada en la tabla por completitud); GraphQL en el backend (ADR-0001 lo descartó).
- **`docs/formacion/tipos-de-base-de-datos.md`** documenta cómo evolucionará la elección de almacenes (Redis para caché/sesión, ClickHouse para analítica si llega, etc.) — son insumos para los disparadores de A2 y otros.
- **Revisión periódica**: este ADR se revisa cada **3 meses** o cuando un ADR aceptado añade/retira aplazamientos que afecten a la tabla maestra. Es revisión de **mantenimiento del índice**, no de las decisiones origen.
- **Reorganización del 2026-05-30 (Nivel 1)**: el ADR se reestructura como **índice maestro consolidado**. Cambios: índice de aplazamientos con tabla, premisas heredadas, numeración A1-A3 con anchors para los aplazamientos consolidados aquí; **tabla maestra con ~25 aplazamientos documentados en otros ADRs** y su disparador; **retirada de las entradas obsoletas** i18n y WCAG (resueltas en ADR-0012 D9 y D6-D8); **cifras concretas en disparadores** (volumen email, latencia, coste, usuarios, equipo).
- **Revisión del 2026-07-11**: añadida la entrada "Comprobación HaveIBeenPwned" (ADR-0003 D6) a la tabla maestra de Identidad y autorización — el código (`PasswordPolicy.kt`) ya declaraba la omisión en su propio comentario ("sin HIBP en MVP") sin que el aplazamiento constara aquí ni en ADR-0003 D6. Detectado por auditoría de drift documentación-código (23 docs, 61 hallazgos).
