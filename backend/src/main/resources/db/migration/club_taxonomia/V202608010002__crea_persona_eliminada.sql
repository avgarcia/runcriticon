-- Lápidas de personas suprimidas: la marca que impide que un evento de alta rezagado vuelva a materializar en la
-- proyección a alguien que ya ejerció su derecho de supresión.
--
-- Por qué hace falta. El upsert de `persona` protege el orden con `WHERE ... last_processed_event_ts >= ...`, pero esa
-- condición cuelga de `ON CONFLICT DO UPDATE`: solo actúa cuando la fila existe. Si el borrado se procesa primero y
-- después llega un `AlumnoInvitado` pendiente del mismo alumno, la sentencia toma la rama `INSERT` —sin conflicto, sin
-- fila con la que comparar— y reinserta nombre y email. La tabla `evento_procesado` tampoco lo corta: son `event_id`
-- distintos y los dos son nuevos. No es teórico: un evento de alta que agota sus reintentos y cae a la DLQ puede
-- republicarse semanas después, cuando la persona ya está borrada, y entonces la PII vuelve para siempre — no llegará
-- ningún otro evento de supresión que la limpie.
--
-- Invariante en el que descansa: los identificadores de usuario son UUID v7 y NUNCA se reutilizan. Un realta con el
-- mismo email genera un id nuevo, así que una lápida no puede bloquear a una persona distinta. Por eso es
-- incondicional, sin comparar instantes: quien fue borrado no vuelve.
--
-- Categoría RGPD: SIN_PII. Guarda un identificador opaco y ningún atributo: ni nombre, ni email, ni club. En cuanto
-- desaparece la fila del usuario en su módulo dueño, deja de permitir reidentificar a nadie. Es el mínimo dato que
-- permite no resucitar a quien pidió ser olvidado.
CREATE TABLE club_taxonomia.persona_eliminada (
    id            UUID                     NOT NULL,
    eliminado_en  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT persona_eliminada_pk PRIMARY KEY (id)
);

-- Soporta la purga futura de lápidas anteriores a la retención del outbox: pasada esa ventana ya no puede llegar
-- ningún evento rezagado, así que la lápida deja de hacer falta.
CREATE INDEX persona_eliminada_eliminado_en_idx ON club_taxonomia.persona_eliminada (eliminado_en);
