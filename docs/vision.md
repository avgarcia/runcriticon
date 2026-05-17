# Visión y objetivos — Runcriticon

> Documento vivo. Se cierra al final de la semana 1 de discovery.

## Alcance del MVP (decidido)

> **El MVP soporta UN ÚNICO CLUB.** No es multi-tenant. El club, sus entrenadores y sus alumnos se conocen desde día 1 (cliente real concreto, no simulado). El sistema se construye para ese caso de uso y se rellena por seed/admin, no por signup público.

Esto fija tres consecuencias importantes:

1. **No hay onboarding público de clubs ni de entrenadores**: el admin del club da de alta a su gente.
2. **Los planes se asignan a grupos**, no a alumnos individuales.
3. Los alumnos pueden tener personalización dentro del grupo (notas, ajustes), pero el plan **nace en el grupo**.

La generalización multi-club queda **explícitamente fuera del MVP** y se retomará solo si tras la beta hay evidencia de demanda.

### Modelo de grupos en el MVP — **tags libres** (decidido 2026-05-17 tras card-sort)

> **Histórico**: la primera aproximación fue una taxonomía rígida de tres ejes (nivel × distancia × carrera). El [card-sort con RG y VG](research/findings.md#cierre-del-card-sort-con-rg-y-vg) la refutó parcialmente: solo *nivel* es universal; *distancia* y *carrera* son fricción real; emergen ejes nuevos (terreno, estado, tipo de objetivo). Se activa el plan B antes de programar.

#### Cómo funciona

- **El admin del club define la taxonomía de su propio club**. La taxonomía es un conjunto de **tags**, cada uno con su **lista de valores** posibles. Ejemplo de taxonomía del club piloto VG:

  | Tag | Valores |
  |---|---|
  | nivel | iniciación · medio · medio-alto · alto |
  | objetivo | maratón valencia · MMM · CACO · oposiciones · mantenimiento · sin objetivo |
  | terreno | asfalto · trail · pista |
  | estado | activo · lesión · post-parto · descanso |
  | día-de-entreno | lun-mié-vie · mar-jue · finde |

  Cada club puede inventarse sus tags. El sistema **pre-carga un conjunto sensato** (nivel, distancia preferida, objetivo, terreno, estado) para acelerar el alta — el admin los acepta, edita o borra.

- **El admin asigna tags a cada alumno**. Un alumno tiene N tags y puede tener **varios valores del mismo tag** (ej. *objetivo: maratón valencia + mantenimiento*).

- **Un grupo es una consulta nombrada sobre tags**. El entrenador o el admin crea un grupo escribiendo un filtro: *"alumnos donde objetivo = maratón valencia AND nivel ∈ {medio, medio-alto}"*. El grupo recibe un nombre libre (*"Maratón Valencia avanzado"*) y la membresía se calcula automáticamente.

- **Ajuste manual** sigue valiendo: el entrenador puede meter o sacar a un alumno de un grupo concreto sin tocar sus tags (excepción que prevalece sobre la query).

- **Un alumno puede pertenecer a varios grupos** (es lo normal).

- **Sugerencia de fusión de micro-grupos**: si dos grupos comparten ≥ 80% de los alumnos o si un grupo tiene ≤ 2 alumnos, el sistema sugiere fusionar o generalizar la query. Mitiga R16 (ver `risks.md`).

- **Snapshot al publicar plan**: cuando el entrenador publica el plan a un grupo, se congela la membresía en ese momento. Cambios posteriores en los tags no alteran el plan ya publicado.

#### Por qué este modelo y no la taxonomía rígida

- Los entrenadores reales no piensan en *(nivel, distancia, carrera)* — piensan en mezclas heterogéneas (RG por nivel; VG por objetivo + terreno + comunidad). Forzar tres ejes fijos genera 40-50 micro-grupos y rechazo.
- Cada club tiene su propia jerga interna; los tags libres respetan esa jerga.
- El modelo de datos interno **ya estaba diseñado como tags clave-valor desde el día 1**, así que el coste extra del MVP es solo de UI (editor de tags + constructor de grupos), no de base de datos.

#### Nota de arquitectura: regla de oro

**Toda lógica de agrupación se hace sobre tags. Nunca sobre columnas hardcodeadas.** Quien programe el MVP debe saber:

- Los tags son entidades de primera clase: `Tag(key, value)`. Los alumnos tienen una relación N-a-N con tags.
- Los grupos son `Group(name, query)` donde `query` es una expresión booleana sobre tags.
- Las funcionalidades como *catálogo de carreras* o *clasificación por nivel* son **casos particulares**: una lista de valores del tag `objetivo` (o `carrera`) y del tag `nivel` respectivamente. **No son tablas separadas.**

### Nota de arquitectura: ritmos del plan modelados como relativos desde día 1

Misma filosofía aplicada al **plan de entrenamiento**. La UI del MVP solo permite al entrenador introducir **ritmos absolutos** (ej. *"5x1000 a 4:00/km"*), pero el modelo de datos guarda cada ritmo como **expresable en términos relativos**: porcentaje de umbral, porcentaje de marca personal en la distancia objetivo, o ritmo absoluto. En MVP siempre será absoluto; en una iteración posterior se podrá introducir directamente *"al 95% de tu marca de 10k"* sin migración de datos.

Esto deja preparado el camino para el feature de COULD *Ritmos relativos a marcas del corredor* (ver H5) — el posible diferenciador real del producto. Cuando se decida activarlo, lo que cambia es la UI y la lógica de cálculo por alumno, no la base.

Regla de oro paralela: **toda sesión tiene ritmo modelado como `{tipo, valor}` (`absoluto:4:00`, `pct_umbral:95`, `pct_marca_10k:97`), nunca como un único string fijo**.

### Catálogo de carreras

El **admin del club** mantiene la lista de carreras de la temporada (nombre + fecha + distancia). Es el conjunto de valores del tag pre-cargado `carrera-objetivo` (o `objetivo`, según cómo prefiera nombrarlo cada club). Los alumnos solo pueden tener como valor de ese tag una carrera del catálogo o "sin carrera". Esto:

- Mantiene la calidad del dato (no "Maratón Madrid" vs "Madrid 2026" vs "MAdrid M").
- Permite vistas agregadas del club ("¿cuántos van a la MMM?").
- Pone un esfuerzo razonable en manos del admin (que ya conoce las carreras del club).

> **Implementación**: no es una tabla aparte. Es un **tag pre-cargado** del sistema con valores ricos (cada valor lleva fecha y distancia como metadata). Cuando la carrera pasa de fecha, el sistema avisa al admin para archivarla.

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
| H4 | Los entrenadores piensan en sus alumnos cruzando **nivel × distancia × carrera objetivo**, no por nombres libres de grupo | **Refutada parcialmente** por el [card-sort con RG y VG](research/findings.md#cierre-del-card-sort-con-rg-y-vg). Decisión: modelo de tags libres en MVP en lugar de taxonomía fija. |
| **H5** *(emergente)* | El verdadero diferenciador del producto es **"un plan, ritmos por corredor"**: el entrenador publica un plan único al grupo con ritmos relativos (% umbral, % marca), y cada alumno lo ve traducido a sus ritmos absolutos a partir de sus marcas | Surge en la [primera ronda de entrevistas](research/findings.md) (RG explícito, AVG y JM implícitos). Validar preguntando directamente a un segundo entrenador antes de programar |

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
