# Plan de formación — GitHub Actions

Objetivo: dominar **GitHub Actions** para construir el pipeline de CI/CD de Runcriticon — compilación, validaciones de calidad y despliegue automatizados (ver ADR-0010).

> Recurso transversal: la **documentación oficial de GitHub Actions**. Practicar en un repositorio de pruebas propio antes de tocar el pipeline real.

---

## Nivel 0 — Fundamentos

**Objetivo:** entender qué es CI/CD y el modelo mental de Actions.

- **CI/CD**: integración continua (cada cambio se compila y se valida automáticamente) y entrega/despliegue continuos (el cambio validado llega a un entorno sin pasos manuales).
- Qué es **GitHub Actions**: la plataforma de automatización integrada en GitHub.
- Conceptos clave y cómo se anidan:
  - **Workflow** — un proceso automatizado, definido en un fichero YAML en `.github/workflows/`.
  - **Event / trigger** — qué dispara el workflow (un *push*, un *pull request*, un disparo manual…).
  - **Job** — un bloque de trabajo; los jobs pueden correr en paralelo o encadenados.
  - **Step** — cada paso dentro de un job: un comando o una *action*.
  - **Action** — una pieza reutilizable (propia o del *marketplace*).
  - **Runner** — la máquina donde corre el job.

**Conexión Runcriticon:** el pipeline del ADR-0010 será uno o varios workflows en `.github/workflows/`.

---

## Nivel 1 — Anatomía de un workflow

**Objetivo:** saber leer y escribir el YAML de un workflow.

- Estructura de un fichero de workflow: `name`, `on`, `jobs`.
- **Eventos**: `push`, `pull_request`, `workflow_dispatch` (manual), `schedule` (programado).
- **Jobs y dependencias**: `needs` para encadenar; ejecución en paralelo por defecto.
- **Steps**: `run` (comandos de shell) y `uses` (invocar una action).
- **Runners**: alojados por GitHub (Ubuntu, etc.) vs *self-hosted*.
- **Matrix** — ejecutar el mismo job con varias combinaciones (p. ej. varias versiones de JDK).
- **Variables, `inputs`, `outputs`** y contexto (`${{ ... }}`).

Esqueleto mínimo de ejemplo:

```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew build
```

**Conexión Runcriticon:** el monorepo (ADR-0001) tendrá pasos para el backend y para el frontend.

---

## Nivel 2 — El pipeline de Runcriticon: compilar y empaquetar

**Objetivo:** construir la parte de **integración** (compilar y empaquetar).

- Compilar el **backend** Spring Boot (Gradle o Maven sobre la JVM).
- Compilar el **frontend** Angular (`npm`/`build`).
- **Caché de dependencias** (`actions/cache` o el caché integrado de `setup-java` / `setup-node`) para acelerar.
- Construir la **imagen Docker** de la aplicación.
- **Artefactos** — guardar y compartir resultados entre jobs.

**Conexión Runcriticon:** la imagen Docker es el artefacto que despliega App Runner (ADR-0006). Esta parte es **agnóstica de nube** — clave para el objetivo de portabilidad.

---

## Nivel 3 — Quality gates

**Objetivo:** automatizar las validaciones de calidad y que **bloqueen** lo que no cumple.

- **Tests** automatizados: unitarios, de contrato de la API (ADR-0001), de fronteras de Spring Modulith (ADR-0007).
- **Cobertura** de tests y umbrales.
- **Análisis estático** de código (p. ej. SonarCloud).
- **Análisis de dependencias vulnerables** (SCA) y **escaneo de secretos**.
- **Lint / formato** de backend y frontend.
- **Branch protection** y *required checks*: cómo hacer que un PR no se pueda mergear si un check falla.

**Conexión Runcriticon:** el detalle de qué checks bloquean se fija en el ADR-0010; este nivel enseña a montarlos. Conecta con el plan de **Seguridad Web/API** (SCA, secretos).

---

## Nivel 4 — Secretos y seguridad en Actions

**Objetivo:** automatizar sin abrir agujeros de seguridad.

- **GitHub Secrets** y *environments* — dónde viven las credenciales.
- **OIDC** — autenticar contra AWS **sin claves de larga vida** (Actions obtiene un token temporal). La forma recomendada.
- Permisos del `GITHUB_TOKEN` — concederle el mínimo (`permissions:`).
- Seguridad de **actions de terceros**: fijarlas por SHA, no por etiqueta móvil; revisar lo que se ejecuta.

**Conexión Runcriticon:** desplegar a AWS desde Actions debe usar OIDC, no claves guardadas — coherente con el principio de mínimo privilegio del plan de Seguridad.

---

## Nivel 5 — Despliegue

**Objetivo:** automatizar la entrega a los entornos.

- **Environments** de GitHub (`staging`, `producción`) y reglas de protección.
- **Aprobaciones manuales** antes de desplegar a producción.
- Desplegar la imagen a **AWS App Runner** y aplicar **Terraform** (ADR-0006).
- **Separar** la parte agnóstica (compilar/validar/imagen) de la parte específica de nube (desplegar) — para poder cambiar de nube tocando solo el job de despliegue.
- Estrategia de **rollback**.

**Conexión Runcriticon:** es la materialización del objetivo del ADR-0006 — cambiar de nube cambiando solo el pipeline de despliegue. El modelo concreto (disparadores, aprobaciones) se fija en el ADR-0010.

---

## Nivel 6 — Buenas prácticas

**Objetivo:** un pipeline rápido, mantenible y reutilizable.

- **Reutilización**: *composite actions* y *reusable workflows* para no repetir YAML.
- **Velocidad**: caché, `concurrency` para cancelar ejecuciones obsoletas, jobs en paralelo.
- **Observabilidad**: leer logs, *annotations*, *job summaries*.
- Mantener los workflows **versionados y revisados por PR**, como el resto del código.

**Conexión Runcriticon:** backend y frontend comparten patrones — los *reusable workflows* evitan duplicar.

---

## Práctica recomendada

En un repositorio de pruebas: crear un workflow que compile una app sencilla, ejecute tests, construya una imagen Docker y la despliegue a un entorno gratuito. Es el ensayo del pipeline del ADR-0010.

## Recursos de partida

- **Documentación oficial de GitHub Actions** (guías, referencia de sintaxis del workflow, *security hardening*).
- El **GitHub Marketplace** de actions — para conocer las acciones reutilizables disponibles.
- Documentación de **OIDC entre GitHub Actions y AWS**.
