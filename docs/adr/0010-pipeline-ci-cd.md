# ADR-0010 — Pipeline de CI/CD

- **Estado**: Propuesto
- **Fecha**: 2026-05-22 · revisado 2026-05-29 (reorganización Nivel 1: índice + premisas heredadas + NFRs + numeración de sub-decisiones D1-D12 con anchors estables; sin cambios en el contenido técnico)
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack, monorepo, contract-first, mismo origen), ADR-0002 (modelo de datos — tests críticos), ADR-0003 (autenticación — tests críticos), ADR-0004 (PostgreSQL, migraciones Flyway, UUID v7), ADR-0006 (infraestructura, App Runner, Terraform, portabilidad), ADR-0007 (Spring Modulith, events-first — tests críticos), ADR-0008 (arquitectura hexagonal — ArchUnit, mapper roundtrip, criterios de éxito del proceso)

## Índice de sub-decisiones

Este ADR fija una **decisión arquitectónica compuesta** sobre el pipeline de CI/CD del proyecto. Las doce sub-decisiones se agrupan en cuatro áreas:

- **Plataforma y forma del pipeline (D1-D3)** — qué herramienta, cómo se reparte el pipeline y qué cruza la frontera entre etapas.
- **Modelo de despliegue (D4-D6)** — cómo se entrega el código a `staging` y a `producción`.
- **Quality gates y tests (D7-D9)** — qué se verifica en cada PR y qué se ejecuta de forma programada.
- **Operación y seguridad (D10-D12)** — autenticación contra la nube, compatibilidad de migraciones y rollback.

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
| **Consumo mensual de GitHub Actions minutes** | Monitorizado. Si supera el 60 % de la cuota del plan contratado, se activan las optimizaciones pendientes (caché agresiva, *path filtering* y/o *self-hosted runners*). |
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

Las doce sub-decisiones desarrolladas a continuación. Cinco son **estratégicas** (D1, D2, D3, D4, D8 — plataforma, estructura, artefacto frontera, modelo trunk-based, estrategia de tests); el resto son **operativas** (D5, D6, D7, D9, D10, D11, D12) y derivan o implementan las anteriores.

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

- **Externalizar el análisis estático a un servidor** (SonarQube / SonarCloud) es una tarea pendiente del proyecto. Disparador para abordarla: cuando el equipo supere las 6 personas o cuando aparezca una métrica de deuda técnica que los linters nativos no capturen.
- **DAST de seguridad** (OWASP ZAP) y **tests de accesibilidad** quedan como mejoras posteriores. Disparador: cierre del Hito H1 (beta arrancada) y entrada a la fase H3 (consolidación) del plan de implementación.
- El plan de formación [`docs/formacion/github-actions.md`](../formacion/github-actions.md) acompaña a este ADR.
- **Revisión del 2026-05-29 (Nivel 1 parcial)**: el ADR se reestructura con índice, premisas heredadas y NFRs explícitos, y se numeran las sub-decisiones D1-D12 con anchors para que cada una sea localizable y revisable de forma independiente. **No se introducen sub-decisiones nuevas** en esta pasada — el contenido técnico es el mismo del ADR original. Las sub-decisiones que la revisión profunda identificó como pendientes (umbrales de cobertura por capa, estrategia de caché y filtrado por path en monorepo, versionado de imágenes Docker, política global de PRs y branch protection, política de tests flaky, catálogo unificado de tests críticos cruzando con ADR-0002/0003/0004/0007/0008, observabilidad del propio pipeline con métricas DORA, política operativa de rollback) quedan abiertas para una segunda tanda. Alineado con ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0007 y ADR-0008 ya aceptados.
