# ADR-0004 — Base de datos: PostgreSQL con un esquema por módulo

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0002 (modelo de datos de tags), ADR-0007 (monolito modular), ADR-0008 (arquitectura hexagonal y DDD), ADR-0001 (stack), ADR-0006 (infraestructura), `risks.md` (R16)

## Contexto y problema

Este ADR cierra **dos decisiones** que conviene no confundir:

1. **Paradigma y motor** — ¿relacional, documental o grafo? ¿qué producto concreto?
2. **Topología de persistencia** — cómo se reparte el almacenamiento entre los cuatro módulos de ADR-0007 (monolito modular).

El modelo de datos (ADR-0002) tiene tags como entidad de primera clase, una relación N-M alumno⇄tag, grupos como consulta sobre tags, excepciones manuales y metadata estructurada en algunos valores de tag (fecha y distancia de las carreras). Además hay planes, sesiones, reportes, alertas, usuarios e invitaciones. La elección afecta a cómo se resuelven las consultas de pertenencia a grupo (el punto de rendimiento sensible, R16), a la independencia de los módulos y al ADR de infraestructura.

## Decisión 1 — Paradigma y motor

### Drivers

- **Qué forma tiene el dato**: muchos tipos de entidad con relaciones claras (club, usuario, alumno, entrenador, tag, grupo, plan, sesión, reporte, alerta, invitación) e **integridad referencial deseable dentro de cada contexto**.
- Necesidad de **consultas sobre tags** eficientes y de algo de **flexibilidad** para la metadata de los valores.
- Madurez y disponibilidad **gestionada en cualquier nube** (AWS/GCP/Azure — ADR-0006).
- Buen soporte desde el stack JVM (ADR-0001) y desde un equipo interno de 4 personas.
- Carga MVP baja: ~550 usuarios, <100 concurrentes, un club.

### Opciones consideradas — paradigma

- **Relacional** (PostgreSQL, MySQL/MariaDB).
- **Documental** (MongoDB).
- **Grafo** (Neo4j).

#### Relacional

- 👍 El modelo *es* relacional: entidades con relaciones FK, es el caso canónico.
- 👍 Integridad referencial y transacciones multi-entidad nativas — publicar un plan (snapshot + plan + N sesiones) es trivial.
- 👍 Resolver un grupo = una intersección/`JOIN` sobre tags: el terreno natural de SQL.
- 👍 Encaje directo con Spring Data JPA; pool de contratación amplio.
- 👎 Las consultas de pertenencia a grupo requieren diseño de índices — no es "gratis" (mitigado: la escala es pequeña).

#### Documental (MongoDB)

- 👍 Esquema flexible; la metadata variable de los tags encajaría de forma natural.
- 👎 Un tag **no es un documento**: es un valor del *catálogo del club* que muchos alumnos comparten. Embeberlo en el alumno **duplica el catálogo**; referenciarlo reconstruye un modelo relacional en un motor que no fuerza la integridad.
- 👎 Plan, sesión, reporte y snapshot cruzan entidades — se paga la flexibilidad documental y no se usa.
- 👎 Las queries de grupo cruzan varias entidades: terreno de SQL, no de un documental.

#### Grafo (Neo4j)

- 👍 *"Alumno tiene tags, grupo consulta tags"* suena a grafo.
- 👎 Es un malentendido: la relación alumno⇄tag es **N-M de un solo salto** — exactamente una tabla de enlace relacional. Un grafo se gana el sueldo con **travesías profundas y de longitud variable** (rutas, caminos más cortos, recomendaciones encadenadas) que Runcriticon no tiene ni contempla en su roadmap.
- 👎 Encaje más pobre con el stack JVM; servicio gestionado y pool de contratación más reducidos.

> **Nota de escala.** A ~550 usuarios y <100 concurrentes, *cualquier* paradigma rinde de sobra. La decisión no es de rendimiento bruto, sino de **encaje con el modelo** y **coste de equipo/operación**. En ambos gana lo relacional con claridad.

### Opciones consideradas — motor relacional

- **PostgreSQL** — relacional maduro; `JSONB` para la metadata flexible de los tags sin renunciar al modelo relacional; índices potentes (parciales, sobre expresiones, sobre `JSONB`); gestionado en las tres nubes (RDS/Aurora, Cloud SQL, Azure Database for PostgreSQL); excelente soporte en Spring Data JPA.
- **MySQL / MariaDB** — muy extendido y gestionado en todas las nubes, pero con soporte de JSON e índices más limitado que el `JSONB` de Postgres y sin ninguna ventaja decisiva para este caso.

### Decisión 1

**Relacional, motor PostgreSQL** (versión gestionada por la nube de ADR-0006). Cubre lo relacional con solidez y, gracias a `JSONB`, absorbe la parte flexible (metadata de valores de tag) sin obligar a un segundo motor. El documental obligaría a duplicar el catálogo de tags o a renunciar a la integridad; el grafo solo pagaría con travesías profundas que aquí no existen.

## Decisión 2 — Topología de persistencia

La premisa de "un esquema monolítico donde todo referencia a todo" no es deseable: el diseño es orientado a dominios (ADR-0008) sobre un monolito modular (ADR-0007), y los módulos deben poder evolucionar de forma independiente — incluida una eventual extracción a microservicios.

Conviene precisar qué hace DDD con la integridad: **no la elimina, la reubica**. *Entre* contextos desaparece la FK cruzada (la consistencia pasa a ser eventual); *dentro* de cada contexto el agregado sigue siendo la frontera de consistencia transaccional y la integridad es tan fuerte como siempre — y eso es exactamente lo que un motor relacional hace mejor que nadie.

### Opciones consideradas — topología

- **Opción A** — Un esquema compartido, FK por todas partes.
- **Opción B** — Una instancia PostgreSQL, **un esquema por módulo, sin FK cruzando fronteras**.
- **Opción C** — Una base de datos por módulo desde el día 1 (y posible *polyglot persistence*).

#### Opción A — Esquema compartido

- 👍 Lo más simple; integridad gratis en todas partes.
- 👎 Acopla los módulos por el esquema: extraer un módulo el día de mañana obliga a reescribir su acceso a datos. Es el *big ball of mud* que ADR-0007 quiere evitar.

#### Opción B — Una instancia, un esquema por módulo

Una sola instancia física de PostgreSQL. Internamente, **un *schema* por módulo**; ninguna FK cruza la frontera de un módulo; las referencias entre contextos son por **ID suelto** y los módulos hablan por puertos/API (ADR-0007 y ADR-0008).

- 👍 Independencia de dominios **real desde el día 1**: ningún módulo conoce el esquema de otro.
- 👍 Integridad fuerte **dentro** de cada contexto (FK y transacción por agregado).
- 👍 **Una sola pieza** que operar, respaldar y monitorizar — viable para un equipo de 4.
- 👍 Extraer un módulo a servicio en el futuro = levantar su *schema* a su propia base: trabajo acotado, no reescritura. Coherente con ADR-0007 y con el *enforcement* de Spring Modulith.
- 👎 Exige disciplina para no colar una FK cruzada (Spring Modulith la detecta en el build).
- 👎 Alguna consulta que sería un `JOIN` entre módulos pasa a ser dos llamadas o un *read model* (el de "salud del club" ya está previsto así en ADR-0007).

#### Opción C — Una base de datos por módulo (y *polyglot*)

- 👍 Independencia máxima; cada dominio escala por separado.
- 👎 Cuatro bases que operar, respaldar, parchear y monitorizar — sobrecoste desproporcionado para un equipo de 4 y un MVP mono-club.
- 👎 Sin transacción cuando dos módulos deben quedar consistentes a la vez (publicar un plan toca Planificación y Club/taxonomía).
- 👎 El *polyglot* multiplica la experiencia que el equipo necesita. Es el "microservicios prematuros" que ADR-0007 ya rechazó, aplicado a la capa de datos.

### Decisión 2

**Opción B: una instancia PostgreSQL gestionada, un esquema por módulo, prohibidas las FK entre módulos.**

Reglas:

- **Una instancia** PostgreSQL gestionada para todo el MVP.
- **Un *schema* por módulo** de ADR-0007: `identidad`, `club_taxonomia`, `planificacion`, `seguimiento` (nombres a concretar al implementar).
- **Ninguna FK cruza la frontera de un módulo.** Las referencias entre contextos se guardan como **ID suelto** (p. ej. una `Sesión` guarda un `alumnoId`, no una FK a la tabla de Identidad).
- **Dentro** de cada *schema*, FK e integridad referencial **sí** — son obligatorias.
- **Transacción** acotada a un agregado / un módulo. La consistencia entre módulos es eventual y la orquesta la capa de aplicación, no la base de datos.
- Los módulos se comunican por **eventos de dominio** (*events-first*, ADR-0007), nunca leyendo el *schema* de otro módulo.

Da la independencia de dominios que el diseño pide, sin renunciar a la integridad donde DDD la coloca (dentro del agregado), y deja la extracción futura a microservicios como trabajo acotado. La Opción C resuelve un problema que el MVP no tiene.

## Detalles de implementación

- **Acceso desde el backend**: Spring Data JPA / Hibernate para el grueso del modelo; SQL nativo para las consultas de resolución de grupos si el ORM se queda corto en rendimiento.
- **Migraciones de esquema versionadas con Flyway** desde el primer día (nada de `ddl-auto` en entornos reales). Migraciones como ficheros SQL planos; **cada módulo gestiona sus propias migraciones sobre su *schema***, con su propia tabla de historial. Se eligió Flyway sobre Liquibase porque el proyecto escribe SQL específico de PostgreSQL (`JSONB`, `unaccent`, índices de expresión y parciales) y la abstracción agnóstica de motor de Liquibase no aporta valor con un único motor ya decidido.
- **Enforcement de la frontera entre esquemas — blando**: un único usuario de BD con acceso a los cuatro esquemas. Spring Modulith verifica las dependencias entre módulos **a nivel de Java** en cada build; los accesos cruzados en SQL nativo se cubren con revisión de código. Un rol de BD por esquema (enforcement duro) queda como **primer paso de una eventual extracción a microservicio** — no se adelanta al MVP.
- **Proyecciones locales / read models**: con la comunicación *events-first* (ADR-0007), cada módulo mantiene **proyecciones locales** de los datos de otros módulos que necesita — materializadas como tablas en su propio *schema* y alimentadas por **eventos de dominio** (registro de eventos de Spring Modulith). La vista de "salud del club" es un ejemplo. **No se hacen consultas cross-schema.** Implica **consistencia eventual**.
- **Metadata de tags**: columnas relacionales para lo estable; `JSONB` solo para la metadata variable por tipo de tag (ver ADR-0002).
- **Extensiones de PostgreSQL**: se habilita `unaccent` — la usa la unicidad insensible a acentos de la taxonomía (ADR-0002).
- `club_id` está en todas las tablas de dominio desde la primera migración (ADR-0002, ADR-0006), aunque en el MVP siempre valga el mismo.

## Consecuencias

### Positivas

- Un único motor y una única instancia cubren lo relacional y lo flexible — operación mínima para un equipo pequeño.
- Independencia de dominios real: el esquema de un módulo no acopla a los demás.
- Integridad referencial y transacciones para los agregados de cada contexto (planes, reportes, snapshots de grupo).
- Servicio gestionado disponible sea cual sea la nube de ADR-0006.

### Negativas / coste asumido

- Exige disciplina para no introducir FK cruzadas entre módulos; sin *enforcement* la topología degenera en la Opción A.
- Las consultas que cruzan módulos dejan de ser un `JOIN`: cada módulo mantiene **proyecciones locales** (read models) de los datos de otros módulos, alimentadas por eventos de dominio (*events-first*, ADR-0007) — lo que introduce consistencia eventual.
- Las consultas de pertenencia a grupo requieren diseño de índices cuidadoso — no es "gratis".

### Riesgos y mitigaciones

- **Rendimiento de las queries de grupo a escala de ~500 alumnos** (R16) → diseñar índices sobre `alumno_tag`; resolver con SQL indexado, no en memoria; medir con datos reales del club piloto; usar SQL nativo si JPA no rinde.
- **Erosión de las fronteras** (una FK cruzada o una query cross-schema colada entre esquemas) → Spring Modulith verificando dependencias de Java en cada build; revisión de código atenta a los accesos cruzados en SQL nativo.
- **Read model desfasado** — la tabla de "salud del club" se actualiza por eventos, así que puede quedar momentáneamente desfasada. Aceptable para una vista de seguimiento; si hiciera falta, se ofrece un refresco bajo demanda.
- **Migraciones descontroladas** → Flyway obligatorio; nada de auto-generación de esquema en staging/producción.

## Notas

- La elección concreta del servicio gestionado (RDS/Aurora vs Cloud SQL vs Azure) se cierra en ADR-0006 junto con la nube.
- El día que un módulo deba extraerse como microservicio, su *schema* se lleva a su propia base de datos; **ese** es el momento de evaluar si ese servicio en concreto justifica otro paradigma (documental, grafo…) según su patrón de acceso real — no se adelanta esa decisión ahora.
- Si en el futuro aparece una necesidad de búsqueda de texto avanzada o analítica pesada, se evalúa por separado; no condiciona esta decisión.
