# ADR-0008 — Arquitectura hexagonal y DDD (aplicados con criterio)

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0001 (stack), ADR-0002 (modelo de datos de tags), ADR-0004 (persistencia), ADR-0007 (monolito modular, events-first), ADR-0010 (CI/CD — ArchUnit)

## Contexto y problema

ADR-0007 fija un monolito modular con cuatro módulos que se comunican *events-first*. Falta decidir **cómo se estructura el código dentro de cada módulo** y **qué enfoque de diseño** se sigue para modelar el dominio.

El dominio de Runcriticon tiene complejidad real que merece modelarse bien: los tags como entidad de primera clase, los grupos como consulta sobre tags, la publicación de plan con *snapshot* de membresía, los ritmos como `{tipo, valor}` (todo ello en ADR-0002). Pero el MVP son 19 funcionalidades y lo construye un equipo interno pequeño: pasarse de ceremonia es un riesgo tan real como quedarse corto.

## Drivers de la decisión

- El dominio tiene **reglas de negocio reales** (resolución de grupos, *snapshot* de plan, tipos de ritmo) que conviene tener centralizadas y bien modeladas, no dispersas.
- La **lógica de dominio debe ser testable** sin levantar base de datos ni framework.
- El producto es **longevo**: el código debe envejecer bien.
- Equipo interno pequeño + velocidad de MVP → **evitar ceremonia que no pague**.
- Coherencia con el monolito modular: las fronteras de módulo de ADR-0007 deben corresponderse con fronteras de dominio.

## Opciones consideradas

- **Opción A** — Arquitectura hexagonal + DDD táctico, con DDD estratégico ligero.
- **Opción B** — Capas tradicionales (controller → service → repository).
- **Opción C** — Arquitectura hexagonal + DDD completo (estratégico con ceremonia formal).

### Opción A — Hexagonal + DDD táctico, estratégico ligero

Cada módulo se estructura en **dominio / aplicación / infraestructura**, con el dominio aislado de la infraestructura mediante **puertos y adaptadores**. Se aplica el **DDD táctico** (agregados, *value objects*, eventos de dominio, lenguaje ubicuo, repositorios como puertos) y el **DDD estratégico de forma ligera**: los *bounded contexts* son los módulos de ADR-0007, identificados de forma pragmática a partir del discovery, sin talleres formales.

- 👍 El dominio queda aislado y **testable sin infraestructura** — tests rápidos.
- 👍 Las reglas de negocio viven en un sitio (el dominio), no filtradas en *services* anémicos.
- 👍 El lenguaje ubicuo alinea código y discovery — menos malentendidos.
- 👍 Encaja con el monolito modular: *bounded context* = módulo.
- 👎 Más estructura inicial que las capas tradicionales; el equipo debe entender hexagonal + DDD táctico (coste de onboarding).

### Opción B — Capas tradicionales

`controller → service → repository`, sin dominio rico ni puertos.

- 👍 Familiar, cero ceremonia, rápido de arrancar.
- 👎 Tiende al **dominio anémico**: la lógica de negocio se desparrama por los *services* y, con reglas como las de este dominio, deriva en *services* enormes difíciles de testear y mantener.
- 👎 El dominio queda acoplado a JPA/infraestructura — tests lentos y frágiles.

### Opción C — Hexagonal + DDD completo

Como la A, pero con **DDD estratégico formal**: *event storming*, *context mapping* elaborado, etc.

- 👍 Modelado de dominio muy riguroso.
- 👎 La ceremonia estratégica es **overhead que un MVP de 19 MUSTs con equipo pequeño no puede justificar**. Riesgo real de *parálisis por análisis*: meses de talleres antes de entregar.

## Decisión

**Opción A: arquitectura hexagonal + DDD táctico, con DDD estratégico aplicado de forma ligera.**

Da lo que el dominio necesita —aislamiento, testabilidad, reglas centralizadas, lenguaje compartido— sin la ceremonia estratégica que ahogaría al equipo antes de entregar el MVP. Las capas tradicionales envejecerían mal con este dominio; el DDD completo es prematuro.

### Estructura interna de cada módulo

Cada módulo de ADR-0007 se organiza en tres capas:

- **`domain`** — entidades, agregados, *value objects*, servicios de dominio, **eventos de dominio** y **puertos** (interfaces). **Sin ninguna dependencia ni anotación de framework** (ni Spring ni JPA): son clases puras. Es el corazón testable.
- **`application`** — casos de uso / servicios de aplicación que orquestan el dominio. Depende de `domain`. **Publica los eventos de dominio** y **consume los eventos entrantes** de otros módulos, actualizando las proyecciones locales.
- **`infrastructure`** — los **adaptadores**: controladores REST (adaptadores de entrada), el modelo de persistencia y los repositorios, el cliente de email, el adaptador de publicación de eventos. Implementan los puertos definidos en `domain`.

La regla de dependencias apunta siempre **hacia el dominio**: `infrastructure` → `application` → `domain`. El dominio no conoce a nadie de fuera. Esta regla la **verifica ArchUnit** en los tests (ADR-0010), incluida la ausencia de imports de framework en `domain`.

### DDD táctico — lo que se aplica

- **Agregados** con una raíz que protege sus invariantes — ej. `Grupo`, `PlanSemanal`, `Alumno`.
- **Value objects** para conceptos sin identidad propia — ej. el **`Ritmo`** (`{tipo, valor}` de ADR-0002), `TagKey`, `TagValue`.
- **Eventos de dominio** — un hecho relevante que ya ha ocurrido (`PlanPublicado`, `AlumnoAsignadoAGrupo`). Definidos en `domain`; son el mecanismo de comunicación *events-first* entre módulos (ADR-0007).
- **Repositorios como puertos**: interfaz en `domain`, implementación en `infrastructure`.
- **Lenguaje ubicuo**: los términos del discovery (alumno, entrenador, grupo, plan, sesión, reporte, tag) **son** los nombres en el código. El discovery ya fijó ese vocabulario en castellano — se respeta para no introducir deriva de traducción en los conceptos de dominio.

### DDD estratégico — lo ligero

- Los **bounded contexts son los cuatro módulos** de ADR-0007 (Identidad, Club y taxonomía, Planificación, Seguimiento), ya identificados a partir del discovery.
- **No** se hacen talleres de *event storming* ni mapas de contexto formales para el MVP. Si al crecer el producto las fronteras se vuelven dudosas, se revisará entonces.

### Persistencia — dominio puro y modelo de persistencia aparte

El dominio **no tiene ninguna anotación de persistencia**. Para lograrlo, la persistencia se modela con un **modelo separado**:

- El `domain` define los agregados, entidades y *value objects* como **clases puras**.
- La `infrastructure` tiene un **modelo de persistencia propio**: entidades JPA (`@Entity`) que reflejan las tablas, **separadas** de los agregados de dominio.
- Un **mapeador** convierte agregado de dominio ↔ entidad de persistencia en ambos sentidos.
- El repositorio: la interfaz (puerto) vive en `domain`; la implementación en `infrastructure` usa las entidades JPA y el mapeador.
- La persistencia es Spring Data JPA / Hibernate (ADR-0004). Su carácter invasivo queda **contenido en el modelo de persistencia** y **nunca toca el dominio** — por eso esta opción no obliga a cambiar ADR-0004.
- **Coste asumido**: el doble modelo (agregado de dominio + entidad de persistencia) y el *boilerplate* de mapeo. Se acepta a cambio de un dominio literalmente puro, que envejece independiente del motor de persistencia.

### El lado de lectura — proyecciones

- Los **agregados** protegen la **escritura** (sus invariantes).
- Las **proyecciones / read models** —locales, alimentadas por eventos de dominio (ADR-0007)— sirven la **lectura**: se consultan directamente, sin pasar por los agregados. Es un **CQRS ligero**: no se fuerza la ceremonia de agregado sobre las consultas.

### Hexagonal "con criterio"

Un puerto existe para lo que **habla con infraestructura externa** (base de datos, email) o para los **adaptadores de entrada**. La comunicación con **otros módulos no es un puerto**: es por **eventos** (ADR-0007). No se crea un puerto ni una abstracción para cada operación CRUD trivial. La regla: aislar lo que de verdad puede cambiar o necesita test aislado, no abstraer por abstraer.

## Consecuencias

### Positivas

- Dominio testable sin base de datos ni Spring → suite de tests rápida.
- Reglas de negocio centralizadas y protegidas por los agregados.
- Código alineado con el lenguaje del discovery — menos malentendidos negocio↔técnico.
- *Bounded context* = módulo → ADR-0007 y ADR-0008 se refuerzan.
- Dominio **literalmente puro**: cambiar el motor de persistencia, o un adaptador, no toca el dominio.

### Negativas / coste asumido

- **Doble modelo** (agregado de dominio + entidad de persistencia) y *boilerplate* de mapeo en cada módulo.
- Más estructura inicial que las capas tradicionales.
- El equipo debe conocer hexagonal y DDD táctico — coste de onboarding; conviene un documento de referencia y ejemplos en el repo.

### Riesgos y mitigaciones

- **Sobre-ingeniería** (puertos por todo, abstracción gratuita) → la regla de "hexagonal con criterio"; revisión de código; un módulo de ejemplo bien hecho como referencia.
- **Bugs en el mapeo dominio ↔ persistencia** → tests del mapeador en ambos sentidos; un módulo de ejemplo que fije el patrón.
- **Ceremonia estratégica que se cuela** → el *event storming* y el *context mapping* formal están **explícitamente fuera** del MVP.
- **Dominio anémico camuflado** (estructura hexagonal pero la lógica sigue en *services*) → revisión de código atenta a que los agregados contengan de verdad sus reglas.

## Notas

- Conviene escribir un breve documento técnico de referencia (o un módulo ejemplar en el repo) que muestre la estructura `domain/application/infrastructure`, un agregado bien modelado, el modelo de persistencia con su mapeador y una proyección — para acelerar el onboarding del equipo.
- La revisión de los *bounded contexts* mediante técnicas estratégicas formales se reabre solo si el crecimiento del producto lo justifica.
