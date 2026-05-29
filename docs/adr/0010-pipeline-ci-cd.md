# ADR-0010 — Pipeline de CI/CD

- **Estado**: Propuesto
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: índice + premisas heredadas + NFRs + numeración de sub-decisiones D1-D12 con anchors estables; sin cambios en el contenido técnico)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack, monorepo, contract-first, mismo origen), ADR-0002 (modelo de datos — tests críticos), ADR-0003 (autenticación — tests críticos), ADR-0004 (PostgreSQL, migraciones Flyway, UUID v7), ADR-0006 (infraestructura, App Runner, Terraform, portabilidad), ADR-0007 (Spring Modulith, events-first — tests críticos), ADR-0008 (arquitectura hexagonal — ArchUnit, mapper roundtrip, criterios de éxito del proceso)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre el pipeline de CI/CD del proyecto. Las veintitrés sub-decisiones se agrupan en seis áreas:

- **Plataforma y forma del pipeline (D1-D3)** — qué herramienta, cómo se reparte el pipeline y qué cruza la frontera entre etapas.
- **Modelo de despliegue y workflow (D4-D6, D17, D20)** — cómo se entrega el código a `staging` y a `producción`, política de PRs y branch protection.
- **Quality gates y tests (D7-D9, D13-D14, D21)** — qué se verifica en cada PR, umbrales de cobertura, catálogo unificado y política de tests flaky.
- **Rendimiento del pipeline (D15-D16)** — caché de dependencias y filtrado por path en monorepo.
- **Artefactos y reproducibilidad (D18-D19)** — versionado de imágenes Docker y reproducibilidad de builds.
- **Operación y seguridad (D10-D12, D22-D23)** — autenticación contra la nube, compatibilidad de migraciones, rollback, observabilidad del pipeline y procedimiento operativo de rollback.

| #   | Sub-decisión                                                              | Capa         |
|-----|---------------------------------------------------------------------------|--------------|
| D1  | [GitHub Actions como plataforma de CI/CD](#d1)                            | Estratégica  |
| D2  | [Pipeline en dos etapas: CI agnóstica + CD específica de nube](#d2)       | Estratégica  |
| D3  | [Imagen Docker en GHCR como artefacto frontera entre etapas](#d3)         | Estratégica  |
| D4  | [Trunk-based con PR de vida corta](#d4)                                   | Estratégica  |
| D5  | [Entrega continua a `staging` desde merge a `main`](#d5)                  | Operativa    |
| D6  | [Aprobación manual a `producción` con `environment` + revisor](#d6)       | Operativa    |
| D7  | [Quality gates como bloqueantes en cada PR](#d7)                          | Operativa    |
| D8  | [Pirámide de tests con Testcontainers para Postgres real](#d8)            | Estratégica  |
| D9  | [Mutation testing programado nocturno, no por PR](#d9)                    | Operativa    |
| D10 | [OIDC para autenticación contra AWS, sin claves de larga vida](#d10)      | Operativa    |
| D11 | [Migraciones Flyway compatibles hacia atrás para preservar el rollback](#d11) | Operativa |
| D12 | [Rollback por redespliegue de imagen anterior](#d12)                      | Operativa    |
| D13 | [Umbrales de cobertura por capa (domain ≥ 90 %, application ≥ 80 %, infrastructure ≥ 60 %)](#d13) | Operativa |
| D14 | [Catálogo unificado de tests críticos con cruce a los ADRs del modelo](#d14) | Estratégica |
| D15 | [Caché de dependencias estratificada (Gradle, npm, Docker layers)](#d15) | Operativa |
| D16 | [Triggers por path en monorepo](#d16) | Operativa |
| D17 | [Concurrencia por PR con `cancel-in-progress`](#d17) | Operativa |
| D18 | [Versionado de imágenes Docker: `main-<sha>` / `v<semver>` / `pr-<num>`](#d18) | Operativa |
| D19 | [Reproducibilidad de builds: lockfiles + toolchain fijada + imágenes base con SHA](#d19) | Operativa |
| D20 | [Política global de PRs: branch protection en `main`, merge commit, sin CODEOWNERS por ahora](#d20) | Operativa |
| D21 | [Política de tests flaky: 1 retry automático, cuarentena tras 3 rojos en `main`, SLA de 1 semana](#d21) | Operativa |
| D22 | [Observabilidad del pipeline: dashboard básico de GitHub Actions + alertas mínimas](#d22) | Operativa |
| D23 | [Procedimiento operativo de rollback documentado](#d23) | Operativa |

## Contexto y problema

El despliegue y las validaciones de calidad deben estar **automatizados desde el principio** — es un requisito del proyecto, no algo opcional. Hay que decidir la plataforma de CI/CD, la estructura del pipeline, qué validaciones de calidad se ejecutan y el modelo de despliegue a los entornos.

Una restricción la fija ADR-0006: el **objetivo de portabilidad** — poder cambiar de nube tocando solo el despliegue, no el código.

Además, este ADR es **el sitio donde se materializan** las promesas de tests críticos que los ADRs del modelo (0002, 0003, 0004, 0007, 0008) han ido dejando: ArchUnit para fronteras, Testcontainers para Postgres real con `JSONB` y `unaccent`, mapper roundtrip de Konvert, retro-compatibilidad de JSON Schema de eventos, contratos de API. Sin que el pipeline las ejecute en CI, esas promesas se quedan en papel.

## Premisas heredadas (no se revisan en este ADR)

Estas premisas vienen como **input cerrado** del contexto del proyecto. **No se revisan en este ADR** — se asumen y condicionan toda la decisión que sigue. Si alguna cambia, este ADR deja de ser válido y hay que abrir uno nuevo.

- **Monorepo con backend Kotlin/Spring Boot 3.x y frontend Angular** (ADR-0001 D9). El pipeline construye los dos artefactos desde el mismo checkout y los publica como dos imágenes Docker distintas.
- **Repositorio ya alojado en GitHub** (input externo del proyecto, no decisión del equipo técnico). Es lo que permite elegir GitHub Actions (D1) y GHCR (D3) sin coste de migración de repo.
- **Mono-tenant en AWS con App Runner en `eu-west-1`** (ADR-0006). La etapa de CD (D2) materializa el despliegue concreto a AWS; si se cambia de nube, solo cambia esa etapa.
- **PostgreSQL con `JSONB`, `unaccent`, índices parciales y de expresión** (ADR-0004). Los tests **no funcionan con H2 o HSQLDB**: hace falta Postgres real vía Testcontainers (D8).
- **Spring Modulith con outbox local sobre Postgres** (ADR-0007 D6). Los tests de fronteras de Modulith son parte del paquete de tests críticos.
- **Hexagonal + DDD con ArchUnit como enforcer** (ADR-0008 D3, D14, D15). Las reglas de arquitectura se verifican en CI; sin esto, la pureza del dominio (ADR-0008 D6) se erosiona silenciosamente.
- **Equipo interno de 4 personas** (ADR-0001). Cadencia de commits frecuente, intolerancia a un pipeline lento; cualquier minuto de pipeline se paga × N ingenieros × commits/día.

## Requisitos no funcionales del pipeline

Estas cifras son **restricciones** y justifican las decisiones de paralelización, caché y filtrado por path (que se materializan como sub-decisiones en una segunda tanda, ver *Notas*).

| Dimensión | Valor objetivo |
|---|---|
| **Tiempo total del pipeline en PR (p95)** | **< 15 min**. Más allá, dolor para el equipo y caída de la cadencia. |
| **Tiempo de la etapa de CI agnóstica (D2)** | **< 10 min**. Es la parte que corre en cada commit. |
| **Tiempo de la etapa de CD a `staging`** desde merge a `main` | **< 5 min**. Coherente con el principio de entrega continua (D5). |
| **Time-to-rollback** (decisión humana → producción con versión anterior) | **< 10 min** (D12 lo materializa). |
| **Cadencia objetivo de deploys a `producción`** en MVP | **≥ 5 / semana**. Cadencia menor es señal de PR demasiado grandes o de pipeline lento. |
| **Frecuencia de fallos del pipeline en `main`** | **< 5 %**. Más es señal de tests inestables que erosionan la confianza en CI. |
| **Consumo mensual de GitHub Actions minutes** | El proyecto opera en el **plan Free de GitHub Actions** (2.000 min/mes para repos privados). Monitorización vía D22; si el consumo supera el 60 % de la cuota sostenidamente, se activan las optimizaciones pendientes (caché agresiva ya en D15, *path filtering* ya en D16) o se evalúa pasar a *self-hosted runners* en una instancia EC2 pequeña con autoescala (decisión separada cuando llegue). |
| **Verde a verde** (todos los quality gates en verde antes de mergear) | **Obligatorio**, sin excepción. Skip de quality gates **prohibido**. |

Estos NFRs son del propio proceso de construcción; los NFRs de runtime los fijan ADR-0001 y ADR-0007.

## Drivers de la decisión

- Despliegues automatizados y validaciones de calidad: **requisito**, no opcional.
- Equipo interno de 4 personas → poca operación, poca infraestructura propia que mantener.
- Portabilidad (ADR-0006): la parte específica de nube debe quedar aislada.
- Repositorio ya en GitHub; monorepo (ADR-0001).
- Coste contenido.

## Opciones consideradas — plataforma

- **Opción A** — GitHub Actions.
- **Opción B** — Jenkins autoalojado.
- **Opción C** — GitLab CI / CircleCI / otros.

### Opción A — GitHub Actions

- 👍 Integrado con el repositorio, los PR y las *releases*; *workflows* versionados junto al código.
- 👍 Sin infraestructura de CI que mantener; capa gratuita amplia.
- 👍 Neutral respecto a la nube — despliega a donde sea (apoya el objetivo de portabilidad).
- 👎 Atado a GitHub como *forge* — pero el repositorio ya está ahí.

### Opción B — Jenkins autoalojado

- 👍 Control total y flexibilidad.
- 👎 Un servidor que desplegar, parchear y mantener — carga de operación que choca con un equipo de 4.

### Opción C — GitLab CI / CircleCI / otros

- 👍 Plataformas maduras.
- 👎 GitLab CI obligaría a mover el repositorio; CircleCI y similares añaden otro proveedor externo. Sin ventaja que lo justifique estando el repo en GitHub.

## Decisión

**Plataforma: GitHub Actions.** Cero infraestructura, integrado con el repositorio y neutral respecto a la nube.

Las veintitrés sub-decisiones desarrolladas a continuación. Seis son **estratégicas** (D1, D2, D3, D4, D8, D14 — plataforma, estructura, artefacto frontera, modelo trunk-based, estrategia de tests, catálogo unificado de tests críticos); el resto son **operativas** (D5, D6, D7, D9, D10, D11, D12, D13, D15, D16, D17, D18, D19, D20, D21, D22, D23) y derivan o implementan las anteriores.

<a id="d1"></a>
### D1 — GitHub Actions como plataforma de CI/CD

GitHub Actions es la plataforma sobre la que se construye todo el pipeline. Decisión sostenida por:

- **Integrado con el repositorio**: PR, releases, environments y secrets viven al lado de los workflows.
- **Cero infraestructura propia**: nada que parchear ni mantener (descartado Jenkins).
- **Neutral respecto a la nube**: la propia plataforma de CI no encadena al proyecto a ningún proveedor cloud (descartado pasar a GitLab CI con migración de repo).
- **Workflows versionados como código** en `.github/workflows/` — auditables, revisables en PR como cualquier otro fichero.

El acoplamiento al *forge* (GitHub) se asume conscientemente: el repositorio ya está allí como premisa, y la opcionalidad de mover el repo es de orden distinto y mucho menos probable que la de cambiar de nube.

<a id="d2"></a>
### D2 — Pipeline en dos etapas: CI agnóstica + CD específica de nube

El pipeline se parte en dos para aislar lo específico de nube:

- **Etapa 1 — CI (agnóstica de nube)**: *checkout* → compilar backend + frontend → tests y *quality gates* (D7) → construir la **imagen Docker** → publicarla en GHCR (D3). **Nada de esta etapa conoce la nube.**
- **Etapa 2 — CD (específica de nube)**: replicar la imagen de GHCR a Amazon ECR → aplicar Terraform → desplegar a App Runner (ADR-0006). Es **la única etapa que cambia** al cambiar de nube.

El acoplamiento a AWS queda contenido en una sola etapa. Materializa el objetivo de portabilidad de ADR-0006: cambiar de nube es reescribir el job de CD, no el código de aplicación ni el resto del pipeline.

<a id="d3"></a>
### D3 — Imagen Docker en GHCR como artefacto frontera entre etapas

El **artefacto frontera** entre la etapa de CI (D2) y la de CD (D2) es la **imagen Docker versionada en GHCR** (GitHub Container Registry).

Razones:

- **GHCR es neutral respecto a la nube** (a diferencia de ECR de AWS): si se cambia de nube, GHCR sigue siendo el origen de imágenes; solo cambia el destino de réplica.
- **Coste cero** para el uso del MVP (cuotas de GHCR son generosas para repos privados).
- **Inmutabilidad** del artefacto: una vez publicada una imagen con un tag, no se sobrescribe. El rollback (D12) consiste en volver a desplegar una imagen anterior por su tag.

La estrategia de etiquetado de imágenes (qué tags se usan para PR, `main`, releases) es una sub-decisión operativa que **queda abierta** para una segunda tanda — ver *Notas*.

<a id="d4"></a>
### D4 — Trunk-based con PR de vida corta

Se trabaja sobre `main` con PR de vida corta:

- **Una sola rama larga**: `main`. No hay `develop`, ni `release-*`, ni feature branches que vivan más de unos días.
- **PR pequeños y rápidos**: el ciclo abrir → revisar → mergear debe medirse en horas o pocos días, no en semanas.
- **Sin código long-lived fuera de `main`**: cualquier feature inacabada vive **detrás de un flag** o se mergea como código todavía no expuesto (controllers no registrados, módulos sin tráfico).

Trunk-based encaja con el principio 1 del plan de implementación (*esqueleto andante* — siempre desplegable) y con la cadencia objetivo de ≥ 5 deploys/semana (NFR).

<a id="d5"></a>
### D5 — Entrega continua a `staging` desde merge a `main`

Cada merge a `main` que pase la CI en verde dispara un **despliegue automático a `staging`**. Sin pasos manuales, sin gates intermedios.

Razones:

- **Detección temprana de problemas de integración** que no se ven en local ni en PR (configuración del entorno, secretos, migraciones).
- **Sostiene el principio 1 del plan** (esqueleto andante). Cualquier commit que entre debe poder vivir en `staging`.
- **Reduce el coste de cada deploy** al hacerlo trivial; reduce la tentación de acumular cambios.

`staging` debe ser **funcionalmente equivalente a `producción`** salvo en datos: misma versión de PostgreSQL, mismo `App Runner` (probablemente con tamaño de instancia menor), misma configuración (cifrado, OIDC, etc.).

<a id="d6"></a>
### D6 — Aprobación manual a `producción` con `environment` + revisor

El despliegue a `producción` **no es automático**: se materializa con un **environment de GitHub** que requiere **aprobación manual** de al menos un revisor autorizado.

Razones:

- **Datos de salud** (ADR-0014) y club piloto real: el coste de un deploy malo a producción es mayor que la fricción de pulsar "Approve".
- **Trazabilidad**: la aprobación queda registrada en el deploy log con `actorId` (cruce con audit log de ADR-0003 D15).
- **Punto de pausa pragmático** en MVP. Cuando el equipo y los smoke tests post-despliegue maduren, se evalúa si pasar a despliegue continuo también a producción (decisión separada, no MVP).

Cualquier ingeniero del equipo puede ser revisor; la lista exacta se materializa en GitHub environments.

<a id="d7"></a>
### D7 — Quality gates como bloqueantes en cada PR

Paquete de validaciones, **bloqueante en cada PR** — sin verde no se mergea, **sin excepción**:

- **Compilación** de backend (Gradle) y frontend (npm).
- **Tests** (ver D8 para la estrategia completa).
- **Lint / formato**: detekt + ktlint (Kotlin), ESLint + Prettier (Angular).
- **Dependencias vulnerables (SCA)**: Dependabot + *dependency review*.
- **Escaneo de secretos**: gitleaks.
- **Cobertura**: se publica siempre; umbrales por capa quedan abiertos para una segunda tanda — ver *Notas*.

El **análisis estático profundo** (SonarQube / SonarCloud) queda **fuera del MVP** por coste o por operación de servidor. Se trata como tarea pendiente del proyecto con disparador en *Notas*.

**Skip de quality gates**: prohibido. Cualquier mecanismo para saltarlos ("admin override", "force merge") está desactivado en GitHub branch protection. La única forma de mergear es con todos los gates en verde.

<a id="d8"></a>
### D8 — Pirámide de tests con Testcontainers para Postgres real

Se sigue la **pirámide de tests**: muchos unitarios rápidos, menos de integración, muy pocos E2E.

- **En cada PR** (bloqueante por D7):
  - **Unitarios** del dominio (testables sin BD ni Spring por ADR-0008 D6 — suite del dominio < 1 s por módulo).
  - **Integración con Testcontainers** (PostgreSQL real). Necesario porque `JSONB`, `unaccent` e índices de expresión de ADR-0004 **no funcionan en una BD de mentira** como H2 o HSQLDB.
  - **Contrato de la API** (cumplimiento de OpenAPI vs implementación, ADR-0001 D10).
  - **Arquitectura con ArchUnit** (regla de dependencias hexagonal de ADR-0008 D3, typed IDs de D11, repositorios estrictos de D14, fronteras de domain/integration events de ADR-0007 D12).
  - **Fronteras de Spring Modulith** (`ApplicationModules.verify()` de ADR-0007 D8 + D12).
  - **Mapper roundtrip de Konvert** (ADR-0008 D10): `mapper.toDomain(mapper.toEntity(plan)) == plan` con datos sintéticos representativos.
  - **Migraciones Flyway desde cero** sobre Testcontainers (ADR-0004 D9): aplicar todas en cada PR garantiza que no hay migraciones rotas.
  - **Retro-compatibilidad de JSON Schema de eventos** (ADR-0007 D11): payloads antiguos deserializan contra la clase actual.

- **E2E (Playwright / Cypress)**: suite pequeña de los *journeys* críticos (login, publicar plan, reportar sesión). Se ejecuta en cada PR pero con tolerancia: una flakiness aislada no bloquea (la política concreta de flaky tests queda abierta para una segunda tanda).

- **Smoke tests post-despliegue**: tras desplegar a `staging` o `producción`, *health check* y un par de rutas críticas. Si fallan, rollback automático (D12).

- **Tests de carga / rendimiento (k6 o Gatling)**: antes de la beta, para validar los NFRs de ADR-0001 (p95 API < 400 ms). Periódico, no por commit.

Tres detalles que el catálogo unificado de tests críticos (segunda tanda) detallará: el listado completo cruzando con ADR-0002, ADR-0003, ADR-0004, ADR-0007 y ADR-0008, los umbrales de cobertura por capa y la política de tests flaky.

<a id="d9"></a>
### D9 — Mutation testing programado nocturno, no por PR

**Mutation testing (PITest)** se ejecuta en una *schedule* nocturna sobre `main`, **no por PR**. Razones:

- Mide la **calidad de los tests**, no solo la cobertura: detecta tests que pasan pero no son útiles (cobertura "fantasma").
- Es **lento** — un orden de magnitud más que la suite normal. Bloquearía el PR sin beneficio.
- Como información de tendencia, sobra con verlo en diario o semanal.

Si la nocturna detecta una caída sostenida de la *mutation score*, se abre ticket. No bloquea merges.

<a id="d10"></a>
### D10 — OIDC para autenticación contra AWS, sin claves de larga vida

GitHub Actions se autentica contra AWS vía **OIDC (OpenID Connect)** — **tokens temporales**, **sin claves de larga vida** guardadas como secretos.

Razones:

- **Cero claves estáticas**: no hay `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY` que rotar, leakear o revocar.
- **Tokens efímeros** por workflow run con scope acotado a un rol IAM concreto.
- **Auditable**: cada uso del rol queda en CloudTrail con el workflow run id.

Implementación: configuración de un identity provider OIDC en IAM apuntando a GitHub Actions, un rol por entorno (`staging`, `producción`) con permisos mínimos, y la action oficial `aws-actions/configure-aws-credentials` con la sintaxis OIDC en cada job que despliegue.

Permisos del propio `GITHUB_TOKEN` también restringidos al mínimo necesario por workflow.

**Actions de terceros pineadas por SHA**, no por tag móvil (`@v4`): evita ataques de supply chain por compromiso de una action popular.

<a id="d11"></a>
### D11 — Migraciones Flyway compatibles hacia atrás para preservar el rollback

Las **migraciones Flyway** (ADR-0004 D9) **deben ser compatibles hacia atrás**: la versión anterior de la aplicación debe poder seguir funcionando contra el esquema migrado durante una **ventana de coexistencia** (típicamente una release).

Implicaciones operativas:

- **Añadir columnas nullable** → versión vieja las ignora; versión nueva las usa. OK.
- **Renombrar columnas** → **prohibido en un solo paso**. Patrón: añadir la nueva, escribir en ambas, migrar lectores, dejar de escribir en la vieja, eliminar la vieja en una migración posterior.
- **Eliminar columnas** → **prohibido en la misma release que las deja de usar**. Patrón equivalente al anterior.
- **Cambios de tipo de columna** → solo si Postgres permite la coerción sin reescribir tabla y sin perder datos; de lo contrario, mismo patrón en pasos.

Sin esta restricción, **el rollback (D12) se rompe**: si la migración no es compatible y se ha desplegado, volver a la imagen anterior la deja inservible porque la app vieja no entiende el esquema nuevo.

Esta es una **regla de equipo**, no técnica: la revisión de PR de migraciones (ADR-0004 D9) es donde se verifica.

<a id="d12"></a>
### D12 — Rollback por redespliegue de imagen anterior

El **rollback** ante un despliegue malo se materializa como **redespliegue de la imagen Docker anterior** desde GHCR (D3).

Procedimiento:

- Cada imagen es **inmutable** y queda en GHCR con su tag versionado.
- App Runner se reconfigura para apuntar a la imagen anterior; el cambio se aplica en minutos.
- La base de datos **no se revierte** — las migraciones son compatibles hacia atrás (D11), así que la app vieja convive con el esquema nuevo.
- Smoke tests post-despliegue validan que el rollback dejó la app sana.

Time-to-rollback objetivo: **< 10 min** desde la decisión humana hasta producción con versión anterior (NFR de este ADR).

La política operativa detallada (quién decide rollback, comunicación al equipo, manejo de incidentes en curso) queda abierta para una segunda tanda — ver *Notas*.

<a id="d13"></a>
### D13 — Umbrales de cobertura por capa

La cobertura se mide por capa de cada módulo (ADR-0008 D2). Los umbrales son **bloqueantes en PR** (D7) y derivan del **criterio de éxito del proceso de desarrollo** del ADR-0008.

| Capa | Umbral mínimo | Razón |
|---|---|---|
| **`domain`** | **≥ 90 %** | Es el corazón testable (ADR-0008 D6); coincide con el criterio de éxito de ese ADR. Sin BD ni framework, los tests son rápidos y exhaustivos. |
| **`application`** | **≥ 80 %** | Casos de uso que orquestan el dominio. Parte de la cobertura viene de tests de integración con Testcontainers (D8); el resto, unitario. |
| **`infrastructure`** | **≥ 60 %** | Mappers (Konvert genera código verificado por roundtrip, no necesita coverage manual extra), controladores REST, repositorios. Mucho boilerplate y código de framework; coverage estricto aquí no aporta. |

**Tendencia decreciente bloquea** aunque sigamos por encima del umbral: si en un PR `domain` baja del 92 % al 91 %, sigue en verde porque > 90 %; si baja del 92 % al 89 %, rojo aunque siga > 80 %. La regla previene la degradación silenciosa.

**Herramienta**: Kover para Kotlin (backend), Istanbul vía Angular CLI (frontend). Resultados publicados en cada PR.

<a id="d14"></a>
### D14 — Catálogo unificado de tests críticos con cruce a los ADRs del modelo

Cada ADR aceptado del modelo (0002, 0003, 0004, 0007, 0008) ha definido sus tests críticos en una tabla propia. **Este ADR es donde se materializa que esos tests se ejecutan en CI** — sin esta consolidación, las promesas se quedan en papel.

#### Mapa categoría de test → herramienta

| Categoría | Herramienta | Cuándo se ejecuta |
|---|---|---|
| Unitarios de dominio | JUnit 5 + kotest (assertions + property-based) | **Cada PR** (bloqueante). Suite < 1 s por módulo (NFR de ADR-0008). |
| Reglas de arquitectura | **ArchUnit** | **Cada PR** (bloqueante). |
| Fronteras entre módulos | Spring Modulith `ApplicationModules.verify()` | **Cada PR** (bloqueante). |
| Integración con BD real | **Testcontainers** + PostgreSQL | **Cada PR** (bloqueante). H2/HSQLDB **no se usan** (ADR-0004 D2 + premisa heredada). |
| Mapper roundtrip de Konvert | Property-based con kotest | **Cada PR** (bloqueante). |
| Contrato de la API REST | Spring Cloud Contract / test propio contra OpenAPI | **Cada PR** (bloqueante, ADR-0001 D10). |
| Retro-compatibilidad de JSON Schema de eventos | Validador JSON Schema + payloads de versiones anteriores en `tests/resources/events/` | **Cada PR** (bloqueante, ADR-0007 D11). |
| Migraciones desde cero | Flyway sobre Testcontainers | **Cada PR** (bloqueante, ADR-0004 D9). |
| Compactación de eventos + ordering | Testcontainers + datos sintéticos | **Cada PR** (bloqueante, ADR-0007 D14, D15). |
| Política de fallos del outbox | Testcontainers + simulación de consumidor que falla | **Cada PR** (bloqueante, ADR-0007 D13). |
| Borrado RGPD | Testcontainers + flujo completo | **Cada PR** (bloqueante, ADR-0004 D16). |
| E2E (journeys críticos) | Playwright | **Cada PR**, tolerancia a flakiness (política específica pendiente). |
| Smoke tests post-deploy | Suite pequeña de rutas críticas | **Tras cada deploy** a `staging` y `producción`. |
| Mutation testing | PITest | **Nocturno** sobre `main` (D9). |
| Carga / rendimiento | k6 o Gatling | **Antes de la beta H1** y periódico. |

#### Cruce con tests críticos por ADR (sin duplicar las tablas originales)

| ADR | Sección con la tabla detallada | Ámbitos críticos |
|---|---|---|
| **ADR-0002** | *Estrategia de tests críticos del modelo* | D1 metadata, D2 unicidad (`unaccent`), D3 SQL canónico pertenencia a grupo, D4 overrides, D5 snapshot, D6 cálculo de ritmo, D7 privacidad de `MarcaAlumno`, D8 read model, D9 personalización, Reglas de oro (`Distancia` compartida). |
| **ADR-0003** | *Estrategia de tests críticos* | D4 token un solo uso, D5 magic link, D6 política de contraseñas con HIBP, D7 caducidad invalida sesiones, D8 reseteo invalida sesiones, D9 cambio de email, D10 Spring Session (no `HttpSession`), D11 revocación admin, D12 rate limiting tres dimensiones, D13 Argon2id + SHA-256+HMAC para tokens, D14 CSRF, D15 audit log de identidad, D16 recuperación por admin. |
| **ADR-0004** | *Estrategia de tests críticos del modelo* | D4 fronteras de schema (sin FK ni query cruzando), D8 tipos (`TIMESTAMPTZ`, UUID v7), D9 migraciones desde cero, D11 compactación + outbox en schema del emisor, D13 cifrado `sslmode=require`, D16 borrado RGPD. |
| **ADR-0007** | *Estrategia de tests críticos* (D7 idempotencia, D10 contrato, D11 retro-compat, D12 distinción, D13 fallos, D14 ordering, D15 reprocesamiento) | Fronteras Modulith, idempotencia de consumidores, seis campos obligatorios + naming en pasado, retro-compatibilidad JSON Schema, distinción domain/integration events, política de reintentos + DLQ implícita + endpoint republish, orden FIFO por `aggregateId` + no-garantía cross-aggregate, reprocesamiento desde outbox compactado. |
| **ADR-0008** | *Estrategia de tests críticos* | D3 dependencias (sin Spring/JPA/Jackson en domain), D4 eventos por capa (`domain.events.*` vs `api.events.*`), D6 agregado rechaza estados inválidos, D10 mapper roundtrip de Konvert, D11 typed IDs (no `UUID` raw en domain), D12 sealed `DomainError` con tests por caso, D13 servicios de dominio con ≥ 2 agregados raíz, D14 repositorios estrictos, D15 `@ApplicationService` en `application.*`, D17 carga eager sin N+1. |

Esta tabla **no duplica** los detalles — cada ADR es responsable de su propia definición; este catálogo es el contrato de "qué pasa en CI". Si un ADR aceptado añade una fila nueva en su tabla de tests críticos, **el ADR-0010 se actualiza** para incluir el ámbito en este cruce.

#### Regla de cierre

El ADR-0010 actúa como **paraguas**: las tablas detalladas viven en cada ADR del modelo; este ADR garantiza que CI las ejecuta. Cualquier promesa de test crítico en un ADR aceptado que **no esté cubierta por una herramienta de este catálogo** es deuda explícita — bien se añade una herramienta aquí, bien se rebaja la promesa.

<a id="d15"></a>
### D15 — Caché de dependencias estratificada (Gradle, npm, Docker layers)

Sin caché, cada PR rehidrata todo desde cero y los NFRs de tiempo (< 15 min total, < 10 min CI) son inalcanzables. Tres niveles:

- **Gradle**: caché de `~/.gradle/caches` + `~/.gradle/wrapper`. Clave: hash de `gradle.lockfile` + `build.gradle.kts` + `settings.gradle.kts` + `gradle/wrapper/gradle-wrapper.properties`.
- **npm**: caché de `~/.npm`. Clave: hash de `frontend/package-lock.json`.
- **Docker buildx**: layer cache vía `type=gha` (cache nativa de GitHub Actions). Habilita reuso de layers entre builds del mismo Dockerfile.

Patrón de claves: `runner.os-tool-${{ hashFiles(...) }}`. Fallback con prefijo parcial (`runner.os-gradle-`) para hits cuando el lockfile cambia ligeramente.

**No se cachean artefactos de build** (`build/`, `dist/`) entre runs — esos son output del propio pipeline y deben regenerarse para garantizar reproducibilidad (D19).

<a id="d16"></a>
### D16 — Triggers por path en monorepo

ADR-0001 D9 fijó *triggers por path* en monorepo como principio. D16 lo materializa: no todos los jobs se ejecutan en todos los PRs.

| Cambio | Jobs que se ejecutan |
|---|---|
| Solo `backend/**` | Build + tests backend + ArchUnit + Modulith + Testcontainers + cobertura backend + imagen Docker backend |
| Solo `frontend/**` | Build + tests frontend + lint + cobertura frontend + imagen Docker frontend |
| `api/openapi.yaml` | **Ambos** — regeneración de stubs (backend) y de cliente TS (frontend); tests de contrato bloqueantes |
| `events/**/*.schema.json` | Tests de retro-compatibilidad JSON Schema bloqueantes |
| Solo `terraform/**` | `terraform plan` + revisión obligatoria; **sin app build** |
| Solo `docs/**` | Solo lint markdown y validación de enlaces; **sin app build** |
| `.github/workflows/**` | Validación de sintaxis YAML del workflow; tests pertinentes a lo que cambia |

Resultado esperado: **ahorro de 30-50 %** del tiempo medio del pipeline, especialmente en commits de docs y de un solo lado (back o front).

Excepción: el job de **smoke tests post-deploy** se ejecuta siempre que haya despliegue, independientemente de qué cambió.

<a id="d17"></a>
### D17 — Concurrencia por PR con `cancel-in-progress`

Cuando llega un nuevo push a una rama de PR, los workflows previos del mismo PR se **cancelan automáticamente**:

```yaml
concurrency:
  group: pr-${{ github.event.number }}
  cancel-in-progress: true
```

Razón: gastar minutos de Actions en commits que el desarrollador ya ha sobrescrito es coste sin beneficio. La cancelación libera el slot para el commit más reciente.

**Excepción crítica**: deploys a `producción` (workflow disparado por aprobación manual en environment, D6) **nunca se cancelan**. Si dos deploys a producción se disparan, ambos llegan a su fin — el segundo gana de forma natural. Cancelar a mitad un deploy a producción es la receta para un estado inconsistente.

<a id="d18"></a>
### D18 — Versionado de imágenes Docker

Patrón de tags en GHCR (D3) — claro, sin ambigüedad:

| Tag | Cuándo | Retención |
|---|---|---|
| `runcriticon-backend:main-<sha7>` y `runcriticon-frontend:main-<sha7>` | Cada merge a `main`. Es lo que se despliega a `staging` (D5). | **90 días** |
| `runcriticon-backend:v<semver>` y `runcriticon-frontend:v<semver>` | Releases formales etiquetadas con tag git (`v1.2.3`). Es lo que se promueve a `producción` (D6). | **Indefinido** |
| `runcriticon-backend:pr-<num>` y `runcriticon-frontend:pr-<num>` | Cada PR abierto (efímero). Útil para probar la imagen del PR. | **30 días** o **al cerrar el PR** (lo que ocurra antes) |

**`latest` está prohibido como tag de producción** — solo se permite como alias de la última `v<semver>` formal si se quiere mantener un puntero móvil para herramientas externas (raramente necesario).

Política de retención implementada con GitHub Container Registry retention policies + workflow nocturno de limpieza.

**El SHA7** del commit hace los tags `main-*` ordenables temporalmente sin colisión razonable. **El SemVer** se reserva para releases formales — en MVP, los deploys a producción son por commit en `main` (cadencia continua), no por releases discretos; por tanto `v<semver>` solo se usa para hitos manuales.

<a id="d19"></a>
### D19 — Reproducibilidad de builds: lockfiles + toolchain fijada + imágenes base con SHA

Un build desde el mismo commit debe producir **siempre la misma imagen Docker**. Tres anclajes:

- **Lockfiles commiteados y obligatorios**:
  - `gradle.lockfile` (Gradle dependency locking).
  - `frontend/package-lock.json` (npm).
  - `gradle/verification-metadata.xml` opcional para integridad de dependencias.
- **Toolchain bloqueada**:
  - Gradle Wrapper con versión fijada (`gradle/wrapper/gradle-wrapper.properties` + `gradle-wrapper.jar`).
  - Node con versión fijada en `.nvmrc` (leída por la action `actions/setup-node`).
  - Java con versión exacta en `actions/setup-java` (sin `lts` ni `latest`).
- **Imágenes Docker base pineadas por SHA**:
  - `FROM eclipse-temurin:21-jdk@sha256:<sha>` en lugar de `:21-jdk` (tag móvil).
  - Idem para la imagen base del frontend (`nginx:alpine@sha256:<sha>` o similar).
  - Renovate / Dependabot actualiza los SHA con PRs revisables.

Test del invariante (al menos como check manual periódico): construir la imagen dos veces desde el mismo commit y verificar que el digest resultante coincide. Si no coincide, hay no-determinismo escondido (timestamps, ordering de jars, etc.) y hay que arreglarlo.

<a id="d20"></a>
### D20 — Política global de PRs

- **Branch protection en `main`** — sin excepciones:
  - **Prohibido push directo** a `main`. Toda entrada es vía PR.
  - **Quality gates obligatorios en verde** (D7) antes de poder mergear.
  - **Al menos 1 aprobación** de cualquier miembro del equipo.
  - **Sin force push**, sin deletion de la rama.
  - **Branch up-to-date with main** antes de mergear (evita merges sobre base obsoleta).
- **Estrategia de merge**: **merge commit** (no squash, no rebase merge). Razón: preserva la granularidad histórica de los commits del PR, coherente con el patrón ya usado en este proyecto.
- **CODEOWNERS**: **no se configura por ahora**. El equipo es pequeño (4 personas, ADR-0001) y la sobrecarga de mantener CODEOWNERS no compensa. Se reabre cuando el equipo crezca o aparezcan áreas con propietario claro (típico disparador: > 6 personas o introducción de roles de dominio diferenciado).
- **Skip de quality gates**: **prohibido**. No se configura "admin override" ni mecanismos de fuerza. La única forma de mergear es con CI verde.

PRs de vida corta (D4) → flujo natural: cualquier ingeniero abre, otro aprueba en horas, se mergea. Sin política compleja añadida.

<a id="d21"></a>
### D21 — Política de tests flaky: 1 retry automático, cuarentena tras 3 rojos en `main`, SLA de 1 semana

Los tests inestables erosionan la confianza en CI: cuando un test falla, el primer instinto del equipo pasa a ser *"será flaky, re-ejecutar"* — y los tests legítimos rojos se ignoran. Política para que esto no ocurra:

#### Re-ejecución limitada

- **Backend (Gradle test retry plugin)**: cada test individual tiene **un retry automático** si falla. Si pasa en el segundo intento, el test se marca como **inestable** y la incidencia se registra en el informe del PR (sin bloquearlo). Si falla las dos veces, el job está rojo.
- **Frontend (Playwright)**: `retries: 1` configurado en CI (no en local — los desarrolladores deben ver los fallos sin red de seguridad). Mismo comportamiento.
- **Re-ejecución manual del workflow desde la UI de Actions**: permitida **una sola vez por PR**, con justificación en comentario del PR. La segunda re-ejecución manual sin commit nuevo está prohibida (la política se aplica por convención + revisión de PR; no hay enforcement técnico de GitHub para esto).

#### Detección automática de flakiness

Un workflow nocturno sobre `main`:

- Recoge los informes de los últimos N runs.
- **Marca como flaky** todo test que haya fallado **3 veces consecutivas** sin commits que toquen su fichero ni el del código que cubre.
- **Abre issue de GitHub** con etiqueta `flaky-test`, asignado a quien tocó por última vez el código relacionado.

#### Cuarentena automática

Cuando un test entra en estado *flaky* (3 rojos consecutivos):

- Se **mutea automáticamente** mediante anotación `@Disabled` (JUnit) o `test.skip` (Playwright), con comentario apuntando al issue.
- **Deja de bloquear PRs y `main`**.
- Queda registrado en un dashboard de tests muteados (cruce con D22).

#### SLA para corregir o eliminar

- El issue de un test flaky tiene **SLA de 1 semana laboral** desde su apertura.
- Acciones aceptables dentro del SLA: **corregir** la causa raíz (race condition, datos compartidos, timeout mal calibrado…) o **decidir conscientemente eliminar** el test si su valor no compensa el coste.
- Si el SLA expira sin acción: **el test se elimina** del repositorio en un PR automático (`bot/flaky-test-cleanup`) y el issue se cierra con motivo `expired-sla`. Razón: un test que no se mantiene es coste sin beneficio; mejor ausencia que falsa seguridad.

#### Prevenciones (regla de equipo, no técnica)

- **Tests de integración con Testcontainers**: contenedor por test class, no por test method. Datos sintéticos generados; nada de fixtures compartidos entre clases.
- **Tests E2E**: selectores estables (`data-testid`), no XPath frágiles. Esperas con `expect().toBeVisible()` de Playwright, **nunca `page.waitForTimeout()`** ni `Thread.sleep()`.
- **Tests unitarios del dominio**: prohibido cualquier dependencia de reloj real (`Instant.now()`); usar un `Clock` inyectado.

#### Métricas (cruce con D22)

- Número de tests en cuarentena en cada momento.
- Tiempo medio desde detección a corrección.
- % de runs de CI con al menos un retry automático.

<a id="d22"></a>
### D22 — Observabilidad del pipeline: dashboard básico de GitHub Actions + alertas mínimas

Sin observabilidad básica del propio pipeline no se sabe si los NFRs (tiempo total, frecuencia de fallos, consumo de Actions minutes) se están cumpliendo. Para MVP, lo mínimo viable:

#### Dashboard

**Dashboard en GitHub Insights / Actions** (nativo, sin herramientas extra). Métricas visibles:

- **Tiempo de cada workflow** (medio, p95) por rama (`main` vs PRs).
- **Tasa de éxito / fallo** del workflow principal en `main`.
- **Consumo de Actions minutes** del mes en curso vs cuota (importante por D-NFR de plan Free).
- **Tests en cuarentena** (D21) — workflow que mantiene un readme actualizado.

Periodicidad de revisión: **semanal** durante el desarrollo intensivo; **mensual** en estado estable.

#### Alertas mínimas

Tres alertas obligatorias, todas vía notificación en canal de Slack/email del equipo (cuando exista; en MVP, vía `gh workflow` o notificaciones de email de GitHub):

- **Pipeline en `main` rojo > 30 min** — algo se está degradando o un fix urgente está atascado.
- **Deploy a `producción` fallido** — el equipo debe estar al tanto para decidir rollback (cruce con D23).
- **Consumo mensual de Actions minutes > 80 % del plan** — semáforo para activar self-hosted runners o subir de plan.

#### Lo que NO entra en MVP

Las **métricas DORA completas** (Deployment Frequency, Lead Time, Change Failure Rate, MTTR) requieren herramientas adicionales (Sleuth, LinearB, etc.) que tienen coste recurrente. Se aplazan como evolución cuando el equipo crezca o el negocio lo exija. La cadencia objetivo de ≥ 5 deploys/semana (NFR) y la frecuencia de fallos < 5 % se aproximan con el dashboard básico sin necesidad de DORA formal.

#### Cruce con ADR-0011

Cuando ADR-0011 se cierre, decidirá la pila de observabilidad de la aplicación (Datadog, Grafana, CloudWatch). Las métricas de CI/CD **pueden migrar** a esa pila si conviene unificarlo todo; mientras tanto, GitHub Insights es suficiente.

<a id="d23"></a>
### D23 — Procedimiento operativo de rollback documentado

D12 fija el rollback como **redespliegue de imagen anterior**; D11 garantiza que las migraciones lo permiten. D23 define el **procedimiento concreto** que el equipo ejecuta cuando hay un incidente — sin esto, la primera vez se improvisa y se cometen errores caros bajo presión.

#### Quién decide rollback

- **`staging`**: cualquier ingeniero del equipo. Sin aprobación necesaria — es un entorno de validación.
- **`producción`**: cualquier ingeniero del equipo puede iniciar el procedimiento; **se notifica al admin** (Antonio en MVP) a posteriori si no se le pudo consultar antes. Bajo el principio de *prefer correcting fast over asking permission* cuando hay impacto en usuarios reales.

No se requiere "war room" formal en MVP — el equipo es pequeño y la comunicación directa es viable.

#### Disparadores de rollback

Se inicia rollback cuando ocurre **uno cualquiera** de:

- **Smoke tests post-deploy en rojo** (automático: el workflow de deploy ya está configurado para iniciar rollback si los smoke tests fallan).
- **Tasa de errores en producción > umbral** durante > 5 min (cuando ADR-0011 esté activo; en MVP, observación humana).
- **Datos corruptos detectados** atribuibles al nuevo deploy.
- **Funcionalidad crítica caída** (login, vista hoy, publicar plan) reportada por usuario o detectada por monitorización.

#### Procedimiento paso a paso

1. **Anuncio en el canal del equipo**: *"Iniciando rollback de producción por <motivo breve>"*. Marca el inicio del incidente.
2. **Identificar la versión anterior estable**: la última imagen `v<semver>` (o `main-<sha>` si no había release formal) que se desplegó a producción antes del actual. Visible en GHCR y en el historial de deploys.
3. **Disparar el workflow de rollback**: `gh workflow run rollback.yml -f environment=production -f version=<sha-anterior>`. El workflow:
   - Verifica que la imagen existe en GHCR.
   - Replica a ECR si hace falta (mismo paso de D2 etapa CD).
   - Reconfigura App Runner para apuntar a la imagen.
   - Espera a que el servicio esté `Running` con la nueva imagen.
   - Ejecuta smoke tests sobre producción.
4. **Verificar manualmente** que la funcionalidad afectada vuelve a funcionar.
5. **Confirmar en el canal**: *"Rollback de producción completado a versión <sha-anterior>. Investigando causa raíz."*

Objetivo: completar pasos 1-5 en **< 10 min** (NFR *Time-to-rollback*).

#### Migraciones y rollback

Como D11 garantiza compatibilidad hacia atrás, la base de datos **no se revierte**. La app vieja convive con el esquema nuevo. Pero:

- Si el incidente está causado **por la propia migración** (caso raro pero posible: una migración aplicó datos incorrectos), el rollback de la app **no soluciona el problema**. Es un escenario de **incidente de datos**, no de despliegue, y requiere recuperación desde backup (cruce con ADR-0004 D14) y/o migración correctiva.
- En el procedimiento, paso 4 incluye verificar el estado de los datos relacionados con el bug. Si no es coherente, **el rollback no es suficiente** y se escala a recuperación de datos.

#### Postmortem obligatorio

Tras cada rollback de `producción` se redacta un **postmortem ligero** (1 página, sin culpables) en `docs/incidentes/<fecha>-<resumen>.md` cubriendo:

- Línea de tiempo del incidente.
- Causa raíz.
- Por qué los tests / quality gates no lo detectaron.
- Acción correctiva: el bug específico + acción preventiva para que esta clase de bug no vuelva a pasar.

Plazo: **1 semana laboral** desde el rollback. Sin postmortem, los rollbacks se repiten.

## Consecuencias

### Positivas

- Despliegues y validaciones automatizados desde el día 1.
- La etapa de CI es **100 % agnóstica de nube**; el acoplamiento a AWS está contenido en la etapa de CD → cambiar de nube es cambiar el *job* de despliegue (cumple el objetivo de ADR-0006).
- Sin infraestructura de CI propia que mantener.
- Ningún cambio entra a `main` sin pasar los *quality gates*.
- OIDC elimina toda una clase de incidentes por leakage de claves AWS.

### Negativas / coste asumido

- El paso de réplica de imagen GHCR → ECR es un paso extra — precio de mantener la CI neutral.
- Los linters nativos no dan un panel de deuda técnica; se asume hasta externalizar el análisis estático a un servidor.
- Las migraciones compatibles hacia atrás (D11) son disciplina de equipo, no garantía técnica — un PR puede saltarse la regla si la revisión no la detecta.

### Riesgos y mitigaciones

- **Despliegue roto** → *smoke tests* post-despliegue + *rollback* por redespliegue de la imagen anterior (D12).
- **Migración no compatible que impide el rollback** → norma de migraciones compatibles hacia atrás (D11); revisión de PR de migraciones obligatoria (ADR-0004 D9).
- **Pipeline lento** → caché de dependencias, *jobs* en paralelo, *path filtering* en monorepo, *mutation testing* fuera del PR. Las sub-decisiones operativas concretas quedan para una segunda tanda.
- **Tests flaky que erosionan la confianza en CI** → política específica pendiente para una segunda tanda.
- **Coste de GitHub Actions** → monitorización y disparadores de optimización (NFR de este ADR).

## Notas

- **Externalizar el análisis estático a un servidor** (SonarQube / SonarCloud) es una tarea pendiente del proyecto. Disparador concreto: **el primero de** (a) el equipo supera 6 personas, (b) detekt/ESLint reportan deuda técnica difícil de tracear (más de 100 issues sin clasificar en `main`), (c) auditoría externa lo exige. Revisión obligatoria cada 6 meses.
- **DAST de seguridad** (OWASP ZAP) y **tests de accesibilidad** (axe-core) quedan como mejoras posteriores. Disparador concreto: **inicio de la fase H3 (consolidación) del plan de implementación**, no antes. Si la beta H1 expone vulnerabilidades concretas, se adelanta puntualmente la pieza necesaria.
- **Actualización de las actions de terceros**: las actions del workflow se pinean por SHA (D10) en lugar de tag móvil. **Renovate** (o Dependabot con `package-ecosystem: github-actions`) abre PRs automáticos cuando una action publica una nueva versión. Política: aceptar mensualmente las actualizaciones que el equipo audita; rechazar las que cambien el comportamiento sin migración documentada. Sin esta política, los pins por SHA congelan versiones obsoletas que dejan de mantenerse.
- **SBOM y firma de imágenes Docker** (Sigstore/cosign, SLSA provenance) son **maduración post-MVP**, no entran en el MVP. Anotación para no olvidarlos: cuando el proyecto entre en fase H3 (consolidación) o cuando un cliente exija cumplimiento de cadena de suministro, se cierran como decisión propia (ADR aparte previsto).
- El plan de formación [`docs/formacion/github-actions.md`](../formacion/github-actions.md) acompaña a este ADR.
- **Revisión del 2026-05-29 (Nivel 1 + cierre completo del ciclo del pipeline)**: el ADR se reestructura con índice, premisas heredadas y NFRs explícitos (incluyendo anclaje al plan Free de GitHub Actions), y se numeran las sub-decisiones D1-D23 con anchors para que cada una sea localizable y revisable de forma independiente. Se incorporan once sub-decisiones nuevas que cierran todos los huecos operativos detectados en la revisión profunda: **D13 — Umbrales de cobertura por capa** (90/80/60 con bloqueo por tendencia decreciente); **D14 — Catálogo unificado de tests críticos** con cruce explícito a ADR-0002, 0003, 0004, 0007 y 0008 sin duplicar sus tablas; **D15 — Caché de dependencias estratificada** (Gradle, npm, Docker layers vía `type=gha`); **D16 — Triggers por path en monorepo** (tabla de cambios → jobs ejecutados); **D17 — Concurrencia por PR** con `cancel-in-progress` excepto en deploys a `producción`; **D18 — Versionado de imágenes Docker** con tres patrones (`main-<sha7>`, `v<semver>`, `pr-<num>`) y retenciones definidas; **D19 — Reproducibilidad de builds** con lockfiles, toolchain fijada e imágenes base por SHA; **D20 — Política global de PRs** con branch protection en `main`, merge commit, sin CODEOWNERS por equipo pequeño, sin skip de quality gates; **D21 — Política de tests flaky** con 1 retry automático, cuarentena tras 3 rojos en `main` y SLA de 1 semana para corregir o eliminar; **D22 — Observabilidad del pipeline** con dashboard básico de GitHub Insights + tres alertas mínimas (DORA completo aplazado como evolución); **D23 — Procedimiento operativo de rollback** documentado con quién decide, disparadores concretos, pasos 1-5 con objetivo de 10 min y postmortem obligatorio. Disparadores concretos añadidos para SonarQube (6+ personas o deuda no capturada por linters), DAST/accesibilidad (fase H3), actualización de actions (Renovate mensual) y SBOM/firma de imágenes (maduración post-MVP). Alineado con ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0007 y ADR-0008 ya aceptados.
