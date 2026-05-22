# ADR-0007 — Monolito modular

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0002 (modelo de datos), ADR-0004 (esquema por módulo), ADR-0005 (email por eventos), ADR-0006 (infraestructura), ADR-0008 (arquitectura hexagonal y DDD), ADR-0009 (autorización), ADR-0010 (CI/CD), `risks.md` (R6 — deuda mono-tenant al generalizar)

## Contexto y problema

Hay que decidir la **forma estructural y de despliegue** del backend de Runcriticon: ¿un solo desplegable o varios servicios? ¿con fronteras internas o sin ellas? Y, dentro de esa forma, **cómo se comunican** las partes.

El MVP es mono-club, con carga baja, lo construye un equipo interno pequeño y se despliega como un contenedor en un servicio gestionado de AWS (ADR-0006). Pero el roadmap contempla multi-club, y `risks.md` (R6) advierte que un código sin fronteras internas convierte cualquier evolución futura en una reescritura.

## Drivers de la decisión

- **Equipo interno pequeño** → la simplicidad operativa pesa mucho: cuantos menos desplegables, redes y piezas que observar, mejor.
- **Velocidad de MVP**: llegar a algo usable con el club piloto sin gastar tiempo en infraestructura distribuida.
- **El dominio tiene áreas claramente diferenciadas** (identidad, club/taxonomía, planificación, seguimiento) — hay fronteras naturales que merece la pena respetar.
- **Multi-club y la extracción futura de servicios son evoluciones previstas** (R6) → conviene que los módulos sean lo más **autónomos** posible para que esa extracción sea trivial.
- Carga esperada en beta: baja (un club, decenas-cientos de usuarios).

## Opciones consideradas

- **Opción A** — Monolito modular: un único desplegable, dividido internamente en módulos con fronteras explícitas.
- **Opción B** — Microservicios: varios servicios desplegables de forma independiente.
- **Opción C** — Monolito tradicional: un único desplegable sin fronteras internas (solo capas).

### Opción A — Monolito modular

Un solo proceso Spring Boot. Internamente, el código se organiza en **módulos** que se corresponden con áreas del dominio; cada módulo tiene una API pública y un interior privado, y la comunicación entre módulos está controlada y es explícita.

- 👍 Operación simple: **un solo desplegable**, una base de datos, un *pipeline*. Ideal para un equipo pequeño.
- 👍 Fronteras internas explícitas → el código se razona por partes, no como una maraña.
- 👍 El día que un módulo deba convertirse en servicio (multi-club, escalado puntual), la frontera **ya existe** — la extracción es trabajo acotado, no reescritura.
- 👍 Dentro de un módulo, una transacción de base de datos sencilla; entre módulos, comunicación por eventos en el mismo proceso — sin la complejidad de red de los microservicios.
- 👎 Requiere **disciplina**: sin enforcement, los módulos se "filtran" unos en otros y acaba siendo un monolito tradicional encubierto.

### Opción B — Microservicios

- 👍 Despliegue y escalado independientes por servicio.
- 👎 Sobrecoste operativo brutal para un equipo pequeño: varios *pipelines*, comunicación por red, datos distribuidos, observabilidad distribuida, *fallos parciales*.
- 👎 **Prematuro**: un MVP mono-club de carga baja no tiene ningún problema que los microservicios resuelvan. Se pagaría todo el coste sin ninguno de los beneficios.

### Opción C — Monolito tradicional

Un único desplegable organizado solo por capas técnicas (controllers, services, repositories), sin módulos de dominio.

- 👍 Lo más rápido de arrancar; cero ceremonia.
- 👎 Sin fronteras internas, con multi-club y crecimiento de funcionalidad por delante, deriva en un *big ball of mud*: todo depende de todo, difícil de razonar e **imposible de extraer** después sin reescribir.

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

Flujo de eventos (forma un grafo acíclico) — un módulo construye sus proyecciones a partir de los eventos de los módulos de los que depende conceptualmente:

```
Identidad y acceso   → publica eventos (no consume de nadie)
Club y taxonomía     → consume de Identidad
Planificación        → consume de Club y taxonomía, Identidad
Seguimiento          → consume de Planificación, Club y taxonomía, Identidad
```

Esta descomposición es un **punto de partida**; el equipo la refina al modelar los *bounded contexts* en ADR-0008.

### Comunicación entre módulos — *events-first*

Los módulos se comunican **exclusivamente mediante eventos de dominio**. **No hay llamadas síncronas de un módulo a otro.**

- Cuando en un módulo ocurre algo relevante, publica un **evento de dominio** (p. ej. `AlumnoAsignadoAGrupo`, `PlanPublicado`, `UsuarioInvitado`).
- Cada módulo mantiene **proyecciones locales** (read models) de los datos de otros contextos que necesita, alimentadas por esos eventos. Un módulo **nunca pregunta** a otro: ya tiene su propia copia, actualizada.
- La **API pública** de un módulo la consumen sus **adaptadores de entrada** (controladores REST); **entre módulos, solo eventos**.
- Una **transacción** está acotada a un módulo. La consistencia entre módulos es **eventual**.
- El **registro de publicación de eventos de Spring Modulith** actúa como *outbox*: el evento se persiste en la misma transacción que el cambio de estado y se entrega **al menos una vez**, con reintentos ante fallo o reinicio.
- Los **consumidores son idempotentes**: un evento puede entregarse más de una vez y procesarlo dos veces debe ser inocuo.

**Por qué events-first.** Hace que cada módulo sea **autónomo de verdad**: no conoce a los demás, solo sus eventos. La extracción futura a microservicio es casi inmediata — el módulo ya se comunica exactamente como lo haría un servicio (eventos asíncronos, sin acoplamiento síncrono, datos propios). Es la materialización del driver de "no cerrarse la puerta a multi-club ni a la extracción de servicios".

**Coste asumido.** La **consistencia eventual** pasa a ser la norma en toda comunicación entre módulos; hay más eventos y más proyecciones que diseñar, versionar y testear; la idempotencia de los consumidores es obligatoria.

### Mecanismo de enforcement — Spring Modulith

Las fronteras no se mantienen solas. Se usa **Spring Modulith** (proyecto oficial de Spring para monolitos modulares): organiza los módulos por paquetes, **verifica las fronteras en los tests**, documenta las dependencias y aporta el **registro de publicación de eventos** que sostiene la comunicación *events-first*. Es más ligero que partir el build en submódulos Gradle y encaja nativamente con el stack (ADR-0001). Si en el futuro hiciera falta un aislamiento más duro, partir en submódulos Gradle queda como evolución.

## Consecuencias

### Positivas

- Operación simple: un desplegable, una BD, un *pipeline*. Coherente con ADR-0006.
- Código razonable por partes; onboarding más fácil.
- **Módulos autónomos**: no se conocen entre sí, solo sus eventos.
- Extracción de servicios futura **casi inmediata** (mitiga R6) — los módulos ya se comunican como servicios.
- Consistencia transaccional sencilla **dentro** de cada módulo.

### Negativas / coste asumido

- *Events-first* añade complejidad: **consistencia eventual** como norma entre módulos, más eventos y proyecciones que diseñar y testear, idempotencia obligatoria.
- Exige **disciplina** y enforcement; sin ello, degenera en monolito tradicional.
- Pensar las fronteras y los eventos desde el principio cuesta algo más que arrancar sin ellos.

### Riesgos y mitigaciones

- **Erosión de las fronteras entre módulos** → Spring Modulith verificando las dependencias en cada build; revisión de código atenta a los *imports* cruzados.
- **Consistencia eventual mal gestionada** (proyecciones que divergen, eventos perdidos) → *outbox* de Spring Modulith (entrega al menos una vez), consumidores idempotentes, tests de las proyecciones.
- **Descomposición de módulos o eventos equivocada** → la propuesta es un punto de partida; revisarla al modelar el dominio (ADR-0008) y de nuevo si la realidad del desarrollo la contradice.
- **Tentación de microservicios prematura** → no se extrae ningún módulo a servicio hasta que un problema real (escala, equipos independientes) lo justifique.

## Notas

- El paso a multi-club no obliga por sí solo a microservicios: un monolito modular puede ser multi-tenant. La extracción de servicios es una decisión aparte, posterior y guiada por necesidad real.
- La estructura interna de cada módulo (hexagonal) y el grado de DDD se deciden en ADR-0008.
- La comunicación *events-first* condiciona el ADR-0009: la autorización a nivel de objeto resuelve las relaciones contra una **proyección local**, no consultando a otro módulo.
- El plan de formación [`docs/formacion/arquitectura-dirigida-por-eventos.md`](../formacion/arquitectura-dirigida-por-eventos.md) acompaña a este ADR.
