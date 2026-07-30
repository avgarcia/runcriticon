-- Fontanería de consumo de eventos de este módulo: la proyección local de personas del club (`persona`) y la tabla de
-- idempotencia de sus listeners (`evento_procesado`). Es el primer módulo del repo que consume integration events de
-- otro (`identidad`), así que estas dos tablas sientan el precedente que heredarán planificacion, seguimiento y
-- auditoria.
--
-- Idioma de las columnas: castellano para las de negocio (`nombre`, `email`, `rol`, `estado`, `actualizado_en`) e
-- inglés para las técnicas de la fontanería de eventos (`event_id`, `processed_at`, `last_processed_event_id`,
-- `last_processed_event_ts`), que son las que fija la guía de persistencia para que el cálculo del lag y la limpieza
-- del histórico sean idénticos en todos los módulos.

-- Categoría RGPD: PII_PRIMARIA (categoría 1). Materializa nombre y email de alumnos y entrenadores del club a partir
-- de los eventos de identidad; requiere borrado físico al consumir el evento de baja del alumno
-- (StudentDeletionListener, pendiente de programar junto al evento que hoy identidad no publica).
--
-- `id` es el id del usuario en `identidad` (llega como `aggregateId` del evento): sin FK cruzada, que no está permitida
-- entre esquemas de módulos distintos — la coherencia la da el evento consumido, no una restricción de la BD.
CREATE TABLE club_taxonomia.persona (
    id                       UUID                     NOT NULL,
    club_id                  UUID                     NOT NULL,
    -- Anchos calcados de `identidad.usuario` (200 / 320), no elegidos aquí: la proyección es un espejo, y un ancho
    -- menor que el de la fuente convertiría un nombre legítimamente largo en un fallo del listener y, tras los
    -- reintentos del outbox, en una entrada de la DLQ.
    nombre                   VARCHAR(200)             NOT NULL,
    email                    VARCHAR(320)             NOT NULL,
    rol                      VARCHAR(20)              NOT NULL,
    estado                   VARCHAR(20)              NOT NULL,
    -- Columnas obligatorias de toda proyección local: identifican el último evento aplicado a la fila. Sostienen dos
    -- cosas distintas: el cálculo de `projection_lag_seconds` (política de proyección stale) y la guarda de orden del
    -- upsert — un evento cuyo `occurredAt` sea anterior al ya aplicado se descarta, porque la entrega del outbox no
    -- garantiza el orden entre `AlumnoInvitado` y `AlumnoActivado` y un reintento puede reentregar el viejo después
    -- del nuevo. La tabla `evento_procesado` no protege de eso: son `event_id` distintos, los dos son nuevos.
    last_processed_event_id  UUID                     NOT NULL,
    last_processed_event_ts  TIMESTAMP WITH TIME ZONE NOT NULL,
    actualizado_en           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT persona_pk PRIMARY KEY (id),
    -- Defensa en profundidad: los valores válidos los impone el dominio (PersonRole / PersonStatus); el CHECK evita
    -- que una fila escrita fuera de la aplicación quede con un valor que el mapeador no sepa leer.
    CONSTRAINT persona_rol_check CHECK (rol IN ('ENTRENADOR', 'ALUMNO')),
    CONSTRAINT persona_estado_check CHECK (estado IN ('INVITADO', 'ACTIVO'))
);

CREATE INDEX persona_club_id_idx ON club_taxonomia.persona (club_id);

-- Soporta el listado de personas del club filtrado por rol (alumnos y entrenadores se listan por separado) y la
-- búsqueda por email dentro del club. No es único a propósito: la unicidad del email la garantiza `identidad`, y
-- exigirla aquí convertiría un re-alta con el mismo email —mientras la fila anterior siga viva porque el borrado aún
-- no está programado— en un fallo del listener y, tras los reintentos, en una entrada de la DLQ.
CREATE INDEX persona_club_rol_idx ON club_taxonomia.persona (club_id, rol);
CREATE INDEX persona_club_email_idx ON club_taxonomia.persona (club_id, email);

-- Categoría RGPD: SIN_PII. Solo registra qué `event_id` ha procesado ya cada listener del módulo; no contiene datos de
-- persona física. La clave primaria compuesta es la que hace idempotente el consumo: el `INSERT ... ON CONFLICT DO
-- NOTHING` del tracker devuelve 0 filas cuando el evento ya estaba registrado.
CREATE TABLE club_taxonomia.evento_procesado (
    listener      VARCHAR(120)             NOT NULL,
    event_id      UUID                     NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT evento_procesado_pk PRIMARY KEY (listener, event_id)
);

-- Soporta la limpieza periódica de filas de más de 30 días, alineada con la retención del outbox.
CREATE INDEX evento_procesado_processed_at_idx ON club_taxonomia.evento_procesado (processed_at);
