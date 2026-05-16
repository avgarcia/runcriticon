# Visión y objetivos — Runcriticon

> Documento vivo. Se cierra al final de la semana 1 de discovery.

## Alcance del MVP (decidido)

> **El MVP soporta UN ÚNICO CLUB.** No es multi-tenant. El club, sus entrenadores y sus alumnos se conocen desde día 1 (cliente real concreto, no simulado). El sistema se construye para ese caso de uso y se rellena por seed/admin, no por signup público.

Esto fija tres consecuencias importantes:

1. **No hay onboarding público de clubs ni de entrenadores**: el admin del club da de alta a su gente.
2. **Los planes se asignan a grupos**, no a alumnos individuales.
3. Los alumnos pueden tener personalización dentro del grupo (notas, ajustes), pero el plan **nace en el grupo**.

La generalización multi-club queda **explícitamente fuera del MVP** y se retomará solo si tras la beta hay evidencia de demanda.

### Modelo de grupos en el MVP (primera aproximación, a validar)

Un grupo **no** es un nombre libre que pone el admin: se define por el cruce de **tres dimensiones taxonómicas** que reflejan cómo piensan los entrenadores en la práctica.

| Dimensión | Valores |
|---|---|
| **Nivel** | iniciación · medio · medio-alto · alto |
| **Distancia objetivo** | 1500m · 5k · 10k · media maratón · maratón |
| **Carrera objetivo** | una de las del [catálogo del club](#cat%C3%A1logo-de-carreras) **o** "sin carrera objetivo" |

Reglas:

- Cada alumno tiene un perfil con esas 3 etiquetas. La carrera objetivo **puede ser "ninguna"** para el corredor de mantenimiento.
- El sistema **sugiere automáticamente** un grupo por cada combinación con alumnos. El entrenador puede **ajustar manualmente** la pertenencia (sacar a alguien que no encaje, meter a alguien que la taxonomía no incluye). Modelo **híbrido**: la sugerencia ahorra trabajo, el ajuste manual cubre los casos reales.
- Un alumno puede pertenecer a **más de un grupo** (ej. está en "medio · 10k · Madrid 10k" y también en "medio · media · MMM").
- Cuando una carrera objetivo pasa de fecha, el grupo se archiva; sus alumnos vuelven al pool y se les reasigna grupo (manual o automáticamente).

> **Esto es una primera aproximación**: hay que validar en las entrevistas si los entrenadores del club piensan realmente en estos tres ejes, o si tienen su propia taxonomía. Si la suya es muy distinta, replantear.

#### Nota de arquitectura: "diseña con tags, lanza con taxonomía"

Aunque la **UI del MVP** solo expone los tres desplegables (nivel, distancia, carrera), **el modelo de datos interno es de tags clave-valor desde día 1**. Es decir: la clasificación de un alumno se almacena como un conjunto de tags (`nivel=medio`, `distancia=10k`, `carrera=mmm-2026`), no como tres columnas fijas.

Esto consigue:

- Coste de MVP igual al de la taxonomía rígida (solo se exponen 3 ejes en la UI).
- Posibilidad de añadir nuevos ejes (ej. *día de entreno*, *fase de macrociclo*) sin migración de base de datos.
- Camino natural a un futuro modelo de **tags libres** (post-MVP) en el que el grupo sea una *consulta* sobre tags y los entrenadores puedan crear su propia taxonomía.

Quien programe el MVP debe saber que esos tres campos no son columnas fijas: son tags predeterminados que la UI expone. La regla de oro: **toda lógica de agrupación se hace sobre tags, nunca sobre columnas hardcodeadas**.

### Catálogo de carreras

El **admin del club** mantiene la lista de carreras de la temporada (nombre + fecha + distancia). Los alumnos eligen su carrera objetivo de esa lista (no se permite texto libre en MVP). Esto:

- Mantiene la calidad del dato (no "Maratón Madrid" vs "Madrid 2026" vs "MAdrid M").
- Permite vistas agregadas del club ("¿cuántos van a la MMM?").
- Pone un esfuerzo razonable en manos del admin (que ya conoce las carreras del club).

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
| H4 | Los entrenadores piensan en sus alumnos cruzando **nivel × distancia × carrera objetivo**, no por nombres libres de grupo | Preguntar cómo agrupan hoy mentalmente a sus alumnos al diseñar el plan |

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
