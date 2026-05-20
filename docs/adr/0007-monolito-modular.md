# ADR-0007 — Monolito modular

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0006 (infraestructura), ADR-0008 (arquitectura hexagonal y DDD), `risks.md` (R6 — deuda mono-tenant al generalizar)

## Contexto y problema

Hay que decidir la **forma estructural y de despliegue** del backend de Runcriticon: ¿un solo desplegable o varios servicios? ¿con fronteras internas o sin ellas?

El MVP es mono-club, con carga baja, lo construye un equipo interno pequeño y se despliega como un contenedor en un servicio gestionado de AWS (ADR-0006). Pero el roadmap contempla multi-club, y `risks.md` (R6) advierte que un código sin fronteras internas convierte cualquier evolución futura en una reescritura.

## Drivers de la decisión

- **Equipo interno pequeño** → la simplicidad operativa pesa mucho: cuantos menos desplegables, redes y piezas que observar, mejor.
- **Velocidad de MVP**: llegar a algo usable con el club piloto sin gastar tiempo en infraestructura distribuida.
- **El dominio tiene áreas claramente diferenciadas** (identidad, club/taxonomía, planificación, seguimiento) — hay fronteras naturales que merece la pena respetar.
- **Multi-club es una evolución prevista** (R6) → no cerrarse la puerta a extraer partes como servicios el día que haga falta.
- Carga esperada en beta: baja (un club, decenas-cientos de usuarios).

## Opciones consideradas

- **Opción A** — Monolito modular: un único desplegable, dividido internamente en módulos con fronteras explícitas.
- **Opción B** — Microservicios: varios servicios desplegables de forma independiente.
- **Opción C** — Monolito tradicional: un único desplegable sin fronteras internas (solo capas).

### Opción A — Monolito modular

Un solo proceso Spring Boot. Internamente, el código se organiza en **módulos** que se corresponden con áreas del dominio; cada módulo tiene una API pública y un interior privado, y las dependencias entre módulos están controladas y son explícitas.

- 👍 Operación simple: **un solo desplegable**, una base de datos, un *pipeline*. Ideal para un equipo pequeño.
- 👍 Fronteras internas explícitas → el código se razona por partes, no como una maraña.
- 👍 El día que un módulo deba convertirse en servicio (multi-club, escalado puntual), la frontera **ya existe** — la extracción es trabajo acotado, no reescritura.
- 👍 Una sola transacción de base de datos cuando hace falta consistencia entre áreas — sin la complejidad de la consistencia distribuida.
- 👎 Requiere **disciplina**: sin enforcement, los módulos se "filtran" unos en otros y acaba siendo un monolito tradicional encubierto.

### Opción B — Microservicios

- 👍 Despliegue y escalado independientes por servicio.
- 👎 Sobrecoste operativo brutal para un equipo pequeño: varios *pipelines*, comunicación por red, datos distribuidos, observabilidad distribuida, *fallos parciales*.
- 👎 **Prematuro**: un MVP mono-club de carga baja no tiene ningún problema que los microservicios resuelvan. Se pagaría todo el coste sin ninguno de los beneficios.

### Opción C — Monolito tradicional

Un único desplegable organizado solo por capas técnicas (controllers, services, repositories), sin módulos de dominio.

- 👍 Lo más rápido de arrancar; cero ceremonia.
- 👎 Sin fronteras internas, con multi-club y crecimiento de funcionalidad por delante, deriva en un *big ball of mud*: todo depende de todo, difícil de razonar y **imposible de extraer** después sin reescribir.

## Decisión

**Opción A: monolito modular.**

Es el equilibrio correcto para este proyecto: la simplicidad operativa de un único desplegable (lo que necesita un equipo pequeño y un MVP) más fronteras internas explícitas que mantienen el código sano y dejan abierta la extracción futura de servicios. Los microservicios son prematuros; el monolito tradicional hipoteca el futuro.

### Descomposición en módulos (propuesta)

A partir del dominio recogido en discovery, specs y wireframes, se proponen **cuatro módulos**. La frontera de cada módulo es un *bounded context* de DDD (ver ADR-0008):

| Módulo | Responsabilidad | Entidades principales |
|--------|-----------------|------------------------|
| **Identidad y acceso** | Usuarios, roles, invitaciones, login. | Usuario, Rol, Invitación |
| **Club y taxonomía** | El club, los tags, el catálogo de carreras, alumnos y entrenadores como miembros, los grupos (consultas sobre tags). | Club, Tag, Alumno, Entrenador, Grupo |
| **Planificación** | Planes semanales, sesiones, editor, publicación a grupos, personalizaciones por alumno. | PlanSemanal, Sesión, Personalización |
| **Seguimiento** | Reportes de sesión, reajuste de día, panel de alertas, salud del club. | ReporteSesión, Alerta |

Dependencias permitidas (forman un grafo acíclico):

```
Identidad y acceso   ← (no depende de nadie)
Club y taxonomía     → Identidad
Planificación        → Club y taxonomía, Identidad
Seguimiento          → Planificación, Club y taxonomía, Identidad
```

- La comunicación entre módulos es a través de **su API pública** (interfaces/puertos), nunca accediendo a su interior.
- La vista de **salud del club** (wireframe 09) es un *read model*: lee datos de Planificación y Seguimiento; vive en el módulo Seguimiento.
- Esta descomposición es un **punto de partida**; el equipo la refina al modelar los *bounded contexts* en ADR-0008.

### Mecanismo de enforcement

Las fronteras no se mantienen solas. Se recomienda **Spring Modulith** (proyecto oficial de Spring para monolitos modulares): organiza los módulos por paquetes, **verifica las fronteras en los tests** y documenta las dependencias. Es más ligero que partir el build en submódulos Gradle y encaja nativamente con el stack (ADR-0001). Si en el futuro hiciera falta un aislamiento más duro, partir en submódulos Gradle queda como evolución.

## Consecuencias

### Positivas

- Operación simple: un desplegable, una BD, un *pipeline*. Coherente con ADR-0006.
- Código razonable por partes; onboarding más fácil.
- Extracción de servicios futura acotada (mitiga R6) — sin reescritura.
- Consistencia transaccional sencilla mientras todo viva en un proceso.

### Negativas / coste asumido

- Exige **disciplina** y enforcement; sin ello, degenera en monolito tradicional.
- Pensar las fronteras desde el principio cuesta algo más que arrancar sin ellas.

### Riesgos y mitigaciones

- **Erosión de las fronteras entre módulos** → Spring Modulith verificando las dependencias en cada build; revisión de código atenta a los *imports* cruzados.
- **Descomposición de módulos equivocada** → la propuesta es un punto de partida; revisarla al modelar el dominio (ADR-0008) y de nuevo si la realidad del desarrollo la contradice.
- **Tentación de microservicios prematura** → no se extrae ningún módulo a servicio hasta que un problema real (escala, equipos independientes) lo justifique.

## Notas

- El paso a multi-club no obliga por sí solo a microservicios: un monolito modular puede ser multi-tenant. La extracción de servicios es una decisión aparte, posterior y guiada por necesidad real.
- La estructura interna de cada módulo (hexagonal) y el grado de DDD se deciden en ADR-0008.
