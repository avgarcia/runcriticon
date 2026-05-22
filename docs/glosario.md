# Glosario — lenguaje ubicuo de Runcriticon

Este glosario fija el **lenguaje ubicuo** del proyecto (DDD, ADR-0008): los términos del dominio que se usan **igual** en el discovery, en las conversaciones de negocio, en los wireframes y en el código. Un término, un significado. El vocabulario está en **castellano** y así se escribe también en el código, para no introducir deriva de traducción.

Si un término cambia o se añade uno nuevo, se actualiza **aquí primero**.

## Personas y roles

- **Club** — la organización deportiva que usa Runcriticon. El MVP es mono-club (ADR-0006).
- **Admin** (del club) — administra el club: da de alta entrenadores, gestiona la taxonomía, ve la salud del club.
- **Entrenador** — crea y publica planes; da de alta y sigue a sus alumnos.
- **Alumno** — el corredor; recibe su plan, ejecuta las sesiones y las reporta.

## Taxonomía y grupos

- **Tag** — una etiqueta `{clave, valor}` que el club asigna a sus alumnos. Es la unidad con la que se construye la taxonomía (ADR-0002).
- **TagKey** (clave de tag) — un eje de la taxonomía del club (p. ej. *nivel*, *objetivo*, *terreno*).
- **TagValue** (valor de tag) — un valor posible de una `TagKey` (p. ej. *medio* para *nivel*).
- **Taxonomía** — el conjunto de `TagKey` y `TagValue` que un club ha definido. Cada club inventa la suya.
- **Grupo** — una **consulta nombrada sobre tags**: el conjunto de alumnos que cumplen unos tags requeridos. No es una lista estática; se recalcula (ADR-0002).
- **Carrera** / **objetivo** — un `TagValue` de la clave *objetivo* que representa una carrera; lleva metadata (fecha, distancia).
- **Override de grupo** — excepción manual de pertenencia que prevalece sobre la consulta del grupo.

## Planificación

- **Plan semanal** — el plan de entrenamiento de una semana que un entrenador publica a un grupo.
- **Sesión** — una unidad de entrenamiento dentro de un plan (un día): distancia, ritmo, descripción.
- **Ritmo** — la intensidad de una sesión, modelada como `{tipo, valor}`: *absoluto*, *pct_umbral* o *pct_marca* (ADR-0002).
- **Personalización** — el ajuste del plan de un grupo para un alumno concreto.
- **Publicar** (un plan) — la acción de entregar un plan semanal a un grupo; congela un *snapshot* de membresía.
- **Snapshot** — la lista de alumnos resueltos en el momento de publicar; cambios posteriores de tags no la alteran.

## Seguimiento

- **Reporte de sesión** — lo que el alumno registra sobre una sesión ejecutada (hecha / no hecha / parcial, nota).
- **Alerta** — una señal que el sistema levanta para el entrenador a partir de los reportes.
- **Salud del club** — la vista agregada del estado del club; es un *read model* (ADR-0004, ADR-0007).

## Identidad y acceso

- **Invitación** — el mecanismo por el que nace una cuenta: un token de un solo uso enviado por email (ADR-0003). No hay registro público.
- **Magic link** — enlace de un solo uso para entrar sin contraseña (ADR-0003).

## Conceptos técnicos transversales

- **Módulo** / **bounded context** — una de las cuatro áreas del dominio con frontera explícita: Identidad y acceso, Club y taxonomía, Planificación, Seguimiento (ADR-0007).
- **Evento de dominio** — un hecho relevante que ya ha ocurrido en un módulo y que otros consumen; es el medio de comunicación entre módulos (*events-first*, ADR-0007).
- **Proyección** / **read model** — la copia local que un módulo mantiene de datos de otro, alimentada por eventos (ADR-0007).
- **`club_id`** — el identificador de club presente en todas las tablas de dominio desde el día 1, para preparar el multi-club (ADR-0006).

> Referencias: ADR-0002 (tags, grupos, ritmos), ADR-0007 (módulos y eventos), ADR-0008 (lenguaje ubicuo), y los documentos de discovery en `docs/`.
