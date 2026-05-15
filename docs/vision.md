# Visión y objetivos — Runcriticon

> Documento vivo. Se cierra al final de la semana 1 de discovery.

## Alcance del MVP (decidido)

> **El MVP soporta UN ÚNICO CLUB.** No es multi-tenant. El club, sus entrenadores y sus alumnos se conocen desde día 1 (cliente real concreto, no simulado). El sistema se construye para ese caso de uso y se rellena por seed/admin, no por signup público.

Esto fija tres consecuencias importantes:

1. **No hay onboarding público de clubs ni de entrenadores**: el admin del club da de alta a su gente.
2. **Los planes se asignan a grupos**, no a alumnos individuales. La estructura del club (grupo "iniciación", "avanzados", "preparación maratón"…) es de primera clase en el modelo.
3. Los alumnos pueden tener personalización dentro del grupo (notas, ajustes), pero el plan **nace en el grupo**.

La generalización multi-club queda **explícitamente fuera del MVP** y se retomará solo si tras la beta hay evidencia de demanda.

## Visión (1 frase)

> *(Borrador)* Que un club de running amateur pueda planificar y seguir el trabajo de todos sus grupos en un único sitio, sin Excel ni WhatsApp.

## Problema que resolvemos

- Los **clubes amateur** no tienen una herramienta común: cada entrenador trabaja con sus propios Excel/PDF/WhatsApp, sin vista de club.
- Los **entrenadores del club** repiten trabajo: el mismo plan se duplica por cada alumno del grupo.
- Los **alumnos** reciben planes en formatos distintos según su entrenador, sin canal claro de feedback ni adaptación.
- El **admin del club** no tiene control real de qué se entrena ni de quién está activo.

## Hipótesis críticas (a validar en entrevistas)

| # | Hipótesis | Cómo la validamos |
|---|---|---|
| H1 | Los entrenadores del club hoy duplican planes manualmente por cada alumno del grupo, y eso les duele | Preguntar cuántos minutos invierten en preparar la semana del grupo entero |
| H2 | El admin del club no tiene visibilidad agregada de qué se entrena en sus grupos | Preguntar cómo sabe hoy si los planes se están ejecutando |
| H3 | Los alumnos quieren saber "qué toca hoy" en < 5 segundos y reportar cómo fue en < 15 | Test con prototipo en papel del flujo "abrir app → ver hoy → marcar hecho" |

> **Nota**: las hipótesis sobre marketplace, multi-club y diferenciación frente a TrainingPeaks dejan de ser críticas en MVP. Las recuperaremos si y solo si decidimos generalizar.

## Objetivos a 12 meses (a fijar con negocio)

Como el MVP es para **un club concreto**, los objetivos se miden dentro de ese club:

- [ ] % de entrenadores del club usando la herramienta de forma activa: _____ %
- [ ] % de alumnos que reportan al menos 3 sesiones por semana: _____ %
- [ ] Reducción de tiempo del entrenador en preparar la semana (vs. método actual): _____ %
- [ ] NPS del admin del club a los 3 meses: _____
- [ ] Decisión go/no-go para abrir a un segundo club: fecha _____

## No-objetivos en esta versión

- Multi-club / multi-tenant.
- Signup público de clubes, entrenadores o alumnos.
- Monetización (no hay modelo de negocio decidido).
- App móvil nativa (web responsive cubre el móvil en MVP).
- Marketplace de entrenadores.
- IA generadora automática de planes.
- Otros deportes distintos a running.
