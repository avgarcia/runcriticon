# Mejoras documentales y de producto del MVP

## Propósito

Esta carpeta consolida la auditoría de `docs` y las decisiones tomadas para corregirla. Su objetivo es preparar una actualización posterior, controlada y trazable de la documentación normativa existente.

`docs_mejoras` es un espacio **temporal de trabajo**. No sustituye todavía a `docs/vision.md`, `docs/backlog.md`, los ADR ni los wireframes. Cuando sus propuestas sean aprobadas, deberán trasladarse a las fuentes normativas y esta carpeta quedará archivada o cerrada.

## Posicionamiento decidido

> El MVP de Runcriticon es un **gestor de entrenamientos para un club de running**, no un gestor integral de clubes de running.

El producto cubre la organización de alumnos y grupos necesaria para planificar, publicar, personalizar y seguir entrenamientos. No cubre la relación comercial o societaria del club.

## Alcance del MVP

- Un único club.
- Aplicación web responsive para móvil y escritorio.
- Interfaz y emails únicamente en español.
- Huso horario fijo `Europe/Madrid`.
- Running exclusivamente y unidades métricas.
- Roles exclusivos: Administrador, Entrenador y Alumno.
- Altas individuales y masivas, invitaciones y ciclo operativo de cuentas.
- Tags configurables, sugerencia de grupo y pertenencia de cada alumno a un máximo de un grupo.
- Planificación semanal estructurada, publicación, personalizaciones y copia de la semana anterior.
- Ritmos absolutos, relativos a marcas y zonas de frecuencia cardiaca como referencia textual.
- Reportes de ejecución, comentarios, alertas, notificaciones y dashboard de seguimiento.
- Preparación jurídica y operativa obligatoria antes de una beta con datos reales.

## Fuera del MVP

- Cuotas, pagos, facturas, suscripciones y renovaciones.
- Contabilidad.
- Gestión comercial de membresías.
- Inscripciones y venta de dorsales.
- Organización logística de carreras o eventos.
- Control de asistencia presencial.
- Comunicaciones generales del club no vinculadas al entrenamiento.
- Web pública, tienda, patrocinadores y merchandising.
- Aplicaciones nativas iOS o Android.
- Registro o gestión de varios clubes.
- Login con Google.
- Importación CSV; se sustituye en el MVP por pegado de emails.
- Ciclismo, natación, gimnasio y multideporte.
- Técnica y fuerza hasta disponer de un modelo que describa ejercicios de forma suficiente.
- Biblioteca de plantillas; el MVP solo copia la semana anterior.
- Exportaciones CSV o PDF del dashboard.
- Histórico deportivo visible de marcas.

## Documentos de esta carpeta

- [`auditoria-documental.md`](auditoria-documental.md): diagnóstico, evidencia y resolución adoptada.
- [`backlog-mejoras.md`](backlog-mejoras.md): trabajo priorizado con criterios verificables.
- [`decisiones-pendientes.md`](decisiones-pendientes.md): únicos asuntos que todavía requieren responsable o revisión humana.

## Reglas de gobierno

1. No implementar una mejora marcada como bloqueada hasta resolver su decisión pendiente.
2. No modificar silenciosamente alcance, permisos, datos sensibles ni reglas de planificación.
3. Cada mejora trasladada a `docs` debe indicar qué documentos y prototipos sustituye.
4. Las decisiones de privacidad requieren revisión jurídica; este material no constituye aprobación legal.
5. Los responsables y fechas desconocidos se indican como `Pendiente de asignar`; no se inventan.
6. El término funcional canónico es **Alumno**. `Atleta` y `Corredor` solo podrán aparecer en citas históricas o contexto no normativo.

## Estado

- Alcance de producto: **decidido**.
- Modelo funcional principal: **decidido**.
- Backlog de corrección: **listo para revisión humana**.
- Privacidad y preparación de beta: **bloqueadas hasta aportar las evidencias indicadas**.
- Modificación de `docs`: **no autorizada en esta fase**.
