# ADR-0008 — Arquitectura hexagonal y DDD (aplicados con criterio)

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0007 (monolito modular), ADR-0002 (modelo de datos de tags), ADR-0001 (stack)

## Contexto y problema

ADR-0007 fija un monolito modular con cuatro módulos. Falta decidir **cómo se estructura el código dentro de cada módulo** y **qué enfoque de diseño** se sigue para modelar el dominio.

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

Cada módulo se estructura en **dominio / aplicación / infraestructura**, con el dominio aislado de la infraestructura mediante **puertos y adaptadores**. Se aplica el **DDD táctico** (agregados, *value objects*, lenguaje ubicuo, repositorios como puertos) y el **DDD estratégico de forma ligera**: los *bounded contexts* son los módulos de ADR-0007, identificados de forma pragmática a partir del discovery, sin talleres formales.

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

- **`domain`** — entidades, agregados, *value objects*, servicios de dominio y **puertos** (interfaces). **Sin dependencias de framework** (ni Spring ni JPA). Es el corazón testable.
- **`application`** — casos de uso / servicios de aplicación que orquestan el dominio. Depende de `domain`.
- **`infrastructure`** — los **adaptadores**: controladores REST (adaptadores de entrada), repositorios JPA, cliente de email, etc. (adaptadores de salida). Implementan los puertos definidos en `domain`.

La regla de dependencias apunta siempre **hacia el dominio**: `infrastructure` → `application` → `domain`. El dominio no conoce a nadie de fuera.

### DDD táctico — lo que se aplica

- **Agregados** con una raíz que protege sus invariantes — ej. `Grupo`, `PlanSemanal`, `Alumno`.
- **Value objects** para conceptos sin identidad propia — ej. el **`Ritmo`** (`{tipo, valor}` de ADR-0002 es un *value object* de manual), `TagKey`, `TagValue`.
- **Repositorios como puertos**: interfaz en `domain`, implementación JPA en `infrastructure`.
- **Lenguaje ubicuo**: los términos del discovery (alumno, entrenador, grupo, plan, sesión, reporte, tag) **son** los nombres en el código. El discovery ya fijó ese vocabulario en castellano — se respeta para no introducir deriva de traducción en los conceptos de dominio.

### DDD estratégico — lo ligero

- Los **bounded contexts son los cuatro módulos** de ADR-0007 (Identidad, Club y taxonomía, Planificación, Seguimiento), ya identificados a partir del discovery.
- **No** se hacen talleres de *event storming* ni mapas de contexto formales para el MVP. Si al crecer el producto las fronteras se vuelven dudosas, se revisará entonces.

### Hexagonal "con criterio"

Un puerto existe para lo que **cruza la frontera del módulo o habla con infraestructura externa** (base de datos, email, otro módulo). No se crea un puerto ni una abstracción para cada operación CRUD trivial. La regla: aislar lo que de verdad puede cambiar o necesita test aislado, no abstraer por abstraer.

## Consecuencias

### Positivas

- Dominio testable sin base de datos ni Spring → suite de tests rápida.
- Reglas de negocio centralizadas y protegidas por los agregados.
- Código alineado con el lenguaje del discovery — menos malentendidos negocio↔técnico.
- *Bounded context* = módulo → ADR-0007 y ADR-0008 se refuerzan.
- El producto envejece bien: cambiar un adaptador (ej. de proveedor de email) no toca el dominio.

### Negativas / coste asumido

- Más estructura inicial que las capas tradicionales.
- El equipo debe conocer hexagonal y DDD táctico — coste de onboarding; conviene un documento de referencia y ejemplos en el repo.

### Riesgos y mitigaciones

- **Sobre-ingeniería** (puertos por todo, abstracción gratuita) → la regla de "hexagonal con criterio"; revisión de código; un módulo de ejemplo bien hecho como referencia.
- **Ceremonia estratégica que se cuela** → el *event storming* y el *context mapping* formal están **explícitamente fuera** del MVP.
- **Dominio anémico camuflado** (estructura hexagonal pero la lógica sigue en *services*) → revisión de código atenta a que los agregados contengan de verdad sus reglas.

## Notas

- Conviene escribir un breve documento técnico de referencia (o un módulo ejemplar en el repo) que muestre la estructura `domain/application/infrastructure` y un agregado bien modelado, para acelerar el onboarding del equipo.
- La revisión de los *bounded contexts* mediante técnicas estratégicas formales se reabre solo si el crecimiento del producto lo justifica.
