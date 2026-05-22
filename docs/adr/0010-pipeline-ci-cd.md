# ADR-0010 — Pipeline de CI/CD

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack, monorepo, tests de contrato), ADR-0004 (migraciones Flyway), ADR-0006 (infraestructura, App Runner, Terraform, portabilidad), ADR-0007 (Spring Modulith), ADR-0008 (arquitectura hexagonal — ArchUnit)

## Contexto y problema

El despliegue y las validaciones de calidad deben estar **automatizados desde el principio** — es un requisito del proyecto, no algo opcional. Hay que decidir la plataforma de CI/CD, la estructura del pipeline, qué validaciones de calidad se ejecutan y el modelo de despliegue a los entornos.

Una restricción la fija ADR-0006: el **objetivo de portabilidad** — poder cambiar de nube tocando solo el despliegue, no el código.

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

### Estructura del pipeline — dos etapas

El pipeline se parte en dos para aislar lo específico de nube:

- **Etapa 1 — CI (agnóstica de nube)**: *checkout* → compilar backend + frontend → tests y *quality gates* → construir la **imagen Docker** → publicarla en **GHCR** (GitHub Container Registry, registro neutral). Nada de esta etapa conoce la nube.
- **Etapa 2 — CD (específica de nube)**: replicar la imagen de GHCR a **Amazon ECR** → aplicar **Terraform** → desplegar a **App Runner** (ADR-0006). Es la **única etapa que cambia** al cambiar de nube.

La **imagen Docker versionada** en GHCR es el *artefacto frontera* entre ambas etapas.

### Quality gates

Paquete de validaciones, **bloqueante en cada PR**:

- **Compilación** de backend y frontend.
- **Tests** (ver estrategia de tests).
- **Lint / formato**: detekt/ktlint (Kotlin), ESLint/Prettier (Angular).
- **Dependencias vulnerables (SCA)**: Dependabot + *dependency review*.
- **Escaneo de secretos**: gitleaks.
- **Cobertura**: se publica siempre, con umbral por capa — más exigente en el dominio (el corazón testable de ADR-0008).

El **análisis estático** se hace con los **linters nativos del lenguaje** para el MVP. Una herramienta de análisis profundo (SonarQube/SonarCloud) queda **fuera del MVP** por coste o por operación de servidor, y es una tarea pendiente del proyecto.

### Estrategia de tests

Se sigue la **pirámide de tests**: muchos unitarios rápidos, menos de integración, muy pocos E2E.

- **En cada PR**: unitarios + **integración con Testcontainers** (PostgreSQL real — necesario porque `JSONB`, `unaccent` e índices de expresión de ADR-0004 no funcionan en una BD de mentira) + **contrato de la API** (ADR-0001) + **arquitectura con ArchUnit** (verifica la regla de dependencias hexagonal de ADR-0008) + **fronteras de Spring Modulith** (ADR-0007).
- **Mutation testing (PITest)**: en ejecución **programada (nocturna)**, no por PR — es lento. Mide la calidad de los tests, no solo la cobertura.
- **E2E (Playwright/Cypress)**: suite pequeña de los *journeys* críticos.
- **Smoke tests post-despliegue**: tras desplegar, *health check* y un par de rutas críticas.
- **Tests de carga/rendimiento (k6/Gatling)**: antes de la beta, para validar los requisitos no funcionales de ADR-0001. Periódico, no por commit.

### Modelo de despliegue

- **Trunk-based**: se trabaja sobre `main` con PR de vida corta.
- **Entrega continua a `staging`**: *merge* a `main` con la CI en verde → despliegue **automático** a `staging`.
- **`Producción`**: **aprobación manual** — un *environment* de GitHub con revisor obligatorio. Un humano valida antes del despliegue final, lo prudente para un MVP con un club piloto real y datos de salud.
- **Rollback**: como cada imagen está versionada en GHCR, revertir un despliegue malo = **volver a desplegar la imagen anterior**. Operación rápida.
- **Migraciones Flyway** (ADR-0004): corren en el despliegue; deben ser **compatibles hacia atrás** para que un *rollback* de la app no choque con el esquema ya migrado.

### Secretos y autenticación con la nube

- GitHub Actions se autentica contra AWS vía **OIDC** — tokens temporales, **sin claves de larga vida** guardadas.
- Los secretos viven en **GitHub Secrets / environments**.
- Permisos mínimos del `GITHUB_TOKEN`; *actions* de terceros fijadas por SHA.

## Consecuencias

### Positivas

- Despliegues y validaciones automatizados desde el día 1.
- La etapa de CI es **100% agnóstica de nube**; el acoplamiento a AWS está contenido en la etapa de CD → cambiar de nube es cambiar el *job* de despliegue (cumple el objetivo de ADR-0006).
- Sin infraestructura de CI propia que mantener.
- Ningún cambio entra a `main` sin pasar los *quality gates*.

### Negativas / coste asumido

- El paso de réplica de imagen GHCR → ECR es un paso extra — precio de mantener la CI neutral.
- Los linters nativos no dan un panel de deuda técnica; se asume hasta externalizar el análisis estático a un servidor.

### Riesgos y mitigaciones

- **Despliegue roto** → *smoke tests* post-despliegue + *rollback* por redespliegue de la imagen anterior.
- **Migración no compatible que impide el rollback** → norma de migraciones compatibles hacia atrás.
- **Pipeline lento** → caché de dependencias, *jobs* en paralelo, *mutation testing* fuera del PR.

## Notas

- **Externalizar el análisis estático a un servidor** (SonarQube/SonarCloud) es una tarea pendiente del proyecto.
- **DAST de seguridad** (OWASP ZAP) y **tests de accesibilidad** quedan como mejoras posteriores.
- El plan de formación [`docs/formacion/github-actions.md`](../formacion/github-actions.md) acompaña a este ADR.
