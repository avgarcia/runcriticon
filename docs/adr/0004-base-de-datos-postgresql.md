# ADR-0004 — Base de datos: PostgreSQL

- **Estado**: Propuesto
- **Fecha**: 2026-05-20
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0002 (modelo de datos de tags), ADR-0001 (stack), ADR-0006 (infraestructura)

## Contexto y problema

El modelo de datos (ADR-0002) tiene tags como entidad de primera clase, una relación N-M alumno⇄tag, grupos como consulta sobre tags, excepciones manuales y metadata estructurada en algunos valores de tag (fecha y distancia de las carreras). Además hay planes, sesiones, reportes y usuarios.

Hay que elegir el motor de base de datos. La elección afecta a cómo se resuelven las consultas de pertenencia a grupo (el punto de rendimiento sensible, R16) y al ADR de infraestructura.

## Drivers de la decisión

- El modelo es **fuertemente relacional**: entidades con relaciones claras (club, usuario, alumno, tag, grupo, plan, sesión, reporte) e integridad referencial deseable.
- Necesidad de **consultas sobre tags** eficientes y de algo de **flexibilidad** para la metadata de los valores.
- Madurez y disponibilidad **gestionada en cualquier nube** (AWS/GCP/Azure — ADR-0006 aún sin nube concreta cerrada).
- Buen soporte desde el stack JVM (ADR-0001).
- Coste bajo en beta.

## Opciones consideradas

- **Opción A** — PostgreSQL.
- **Opción B** — MySQL / MariaDB.
- **Opción C** — MongoDB (documental).

### Opción A — PostgreSQL

- 👍 Relacional maduro, con integridad referencial y transacciones sólidas.
- 👍 **`JSONB`** permite guardar la metadata flexible de los valores de tag (fecha, distancia) de forma estructurada y consultable, sin renunciar al modelo relacional.
- 👍 Índices potentes (incl. parciales y sobre expresiones / `JSONB`) — útiles para la resolución de queries de grupo.
- 👍 Servicio gestionado en las tres nubes (Amazon RDS / Aurora, Cloud SQL, Azure Database for PostgreSQL).
- 👍 Excelente soporte desde Spring Data JPA.
- 👎 Ligeramente más exigente de operar que MySQL si se autogestiona — se mitiga usando la versión gestionada.

### Opción B — MySQL / MariaDB

- 👍 Muy extendido, gestionado en todas las nubes.
- 👎 Soporte de JSON e índices más limitado que el `JSONB` de Postgres — peor encaje con la metadata de tags.
- 👎 Sin ventaja decisiva frente a Postgres para este caso.

### Opción C — MongoDB (documental)

- 👍 Esquema flexible; los tags por alumno podrían ir embebidos.
- 👎 El modelo de ADR-0002 es relacional (grupos, planes, reportes, integridad referencial). Forzarlo a documentos complica las consultas y pierde garantías transaccionales.
- 👎 Las queries de grupo cruzan varias entidades — terreno natural de SQL, no de un documental.

## Decisión

**Opción A: PostgreSQL**, en su versión **gestionada** por el proveedor de nube que se fije en ADR-0006.

PostgreSQL cubre lo relacional con solidez y, gracias a `JSONB`, absorbe la parte flexible (metadata de valores de tag) sin obligar a un segundo motor. Es la opción con mejor relación entre encaje con el modelo de ADR-0002, madurez y disponibilidad gestionada multi-nube.

Detalles:

- **Acceso desde el backend**: Spring Data JPA / Hibernate para el grueso del modelo; SQL nativo para las consultas de resolución de grupos si el ORM se queda corto en rendimiento.
- **Migraciones de esquema versionadas** con Flyway o Liquibase desde el primer día (no `ddl-auto` en entornos reales).
- **Metadata de tags**: columnas relacionales para lo estable (`key`, `value`); `JSONB` solo para la metadata variable por tipo de tag.
- **Una sola base de datos** para todo el MVP (mono-club). El `club_id` está en las tablas desde el día 1 (ADR-0006) aunque siempre valga el mismo.

## Consecuencias

### Positivas

- Un único motor cubre lo relacional y lo flexible.
- Servicio gestionado disponible sea cual sea la nube elegida en ADR-0006.
- Integridad referencial y transacciones para planes, reportes y snapshots de grupo.

### Negativas / coste asumido

- Las consultas de pertenencia a grupo requieren diseño de índices cuidadoso — no es "gratis".

### Riesgos y mitigaciones

- **Rendimiento de las queries de grupo a escala de 500 alumnos** (R16) → diseñar índices sobre `alumno_tag`; medir con datos reales del club piloto; usar SQL nativo si JPA no rinde.
- **Migraciones descontroladas** → Flyway/Liquibase obligatorio; nada de auto-generación de esquema en staging/producción.

## Notas

- La elección concreta del servicio gestionado (RDS/Aurora vs Cloud SQL vs Azure) se cierra en ADR-0006 junto con la nube.
- Si en el futuro aparece una necesidad de búsqueda de texto avanzada o analítica pesada, se evalúa por separado; no condiciona esta decisión.
