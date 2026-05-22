# ADR-0009 — Modelo de autorización: RBAC + autorización a nivel de objeto

- **Estado**: Propuesto
- **Fecha**: 2026-05-22
- **Decisores**: Negocio (Antonio) · futuro equipo técnico
- **Relacionado con**: ADR-0003 (autenticación), ADR-0004 (base de datos), ADR-0006 (`club_id`), ADR-0007 (monolito modular, events-first), ADR-0008 (arquitectura hexagonal y DDD)

## Contexto y problema

El ADR-0003 resuelve la **autenticación** — probar quién es el usuario. Falta decidir la **autorización**: una vez dentro, qué operaciones puede ejecutar cada usuario y **a qué datos concretos** puede acceder.

Sin un modelo explícito el riesgo es doble:

- Operaciones ejecutadas por quien no debe (un alumno publicando un plan).
- Más sutil y más grave: un usuario accediendo a **datos de otro** — un alumno viendo el perfil de otro alumno. Es la vulnerabilidad **nº 1 del OWASP API Security Top 10**: *Broken Object-Level Authorization* (IDOR). Con datos de salud sensibles (RGPD), un fallo aquí es serio.

## Drivers de la decisión

- Tres roles fijos y conocidos: `admin`, `entrenador`, `alumno` (ADR-0003).
- Datos de salud sensibles → **minimizar quién ve cada ficha**; cumplir RGPD, incluida la *responsabilidad proactiva* (poder demostrar quién accedió).
- Hay que impedir el **acceso transversal a objetos de otros usuarios**, no solo restringir operaciones por rol.
- Coherencia con la arquitectura hexagonal y el monolito modular *events-first* (ADR-0007/0008).
- Equipo pequeño → modelo **simple y sistemático**, sin un motor de políticas pesado.
- Preparación multi-club: aislamiento por `club_id` desde el día 1 (ADR-0006).

## Opciones consideradas

- **Opción A** — RBAC + autorización a nivel de objeto, en capas.
- **Opción B** — Solo RBAC (control por rol).
- **Opción C** — ABAC / motor de políticas configurable (p. ej. OPA, Cerbos).

### Opción A — RBAC + autorización a nivel de objeto

Control "grueso" por rol **más** comprobación, para cada objeto concreto, de la relación entre quien pide y el objeto.

- 👍 Cubre las dos preguntas: qué operaciones (rol) y a qué datos (relación).
- 👍 Cierra la vulnerabilidad IDOR.
- 👍 Simple: con 3 roles fijos el RBAC es trivial; las reglas de relación son pocas.
- 👎 Exige disciplina: la comprobación a nivel de objeto hay que aplicarla **sistemáticamente** en cada caso de uso.

### Opción B — Solo RBAC

- 👍 Lo más simple.
- 👎 No distingue entre dos usuarios del mismo rol → **no impide que un alumno vea el perfil de otro**. Deja abierta la vulnerabilidad IDOR. Insuficiente.

### Opción C — ABAC / motor de políticas configurable

- 👍 Muy flexible: reglas dinámicas por atributos, externalizadas.
- 👎 Sobredimensionado para 3 roles fijos; un servicio o librería más que aprender, desplegar y operar; complejidad que un MVP con equipo pequeño no justifica.

## Decisión

**Opción A: RBAC + autorización a nivel de objeto, aplicadas en tres capas.**

### Capa 1 — RBAC (control por rol)

Control grueso atado al rol. Responde a *"¿este rol puede ejecutar esta operación?"*. Se implementa con Spring Security a nivel de endpoint/método (`@PreAuthorize`). Con 3 roles fijos, esta capa es simple y no necesita configuración dinámica.

### Capa 2 — Autorización a nivel de objeto

Responde a *"¿puede este usuario concreto tocar este objeto concreto?"*. Es la capa que impide ver datos de otro usuario. Tiene dos formas:

- **Objeto suelto** — al cargar un objeto por su `id`, se verifica la **relación** entre quien pide y el objeto.
- **Colección / listado** — los endpoints que devuelven listas (*"mis alumnos"*, *"planes del club"*) no comprueban un objeto: **filtran el conjunto** a lo que el que pide puede ver. La consulta se construye **ya acotada al alcance del *principal***; **nunca** se trae todo y se filtra después en memoria o en la UI — eso es una fuga esperando a ocurrir.

### Capa 3 — Aislamiento por club

Toda consulta se filtra por el `club_id` del usuario que pide. En el MVP hay un solo club, pero la disciplina se aplica desde el día 1 (ADR-0006): un fallo puntual nunca podría cruzar datos entre clubes.

### Matriz de visibilidad

| Recurso / operación | admin | entrenador | alumno |
|---------------------|:-----:|:----------:|:------:|
| Gestionar club y taxonomía | ✅ | lectura (para usar tags) | ❌ |
| Alta de entrenadores | ✅ | ❌ | ❌ |
| Alta de alumnos | ✅ | ✅ (los suyos) | ❌ |
| Ver perfil de alumno | todos del club | **solo los de sus grupos** | **solo el suyo** |
| Crear / editar planes | ✅ | **edita los suyos** | ❌ |
| Ver planes | todos del club | **ve todos los del club** | el suyo publicado |
| Reportar una sesión | ❌ | ❌ | ✅ (las suyas) |
| Ver reportes de sesión | todos del club | de sus alumnos | solo los suyos |
| Vista de salud del club | ✅ | su parte (sus grupos) | ❌ |

Reglas de relación que sostienen la matriz:

- Un **alumno** solo accede a objetos cuyo dueño es él mismo.
- Un **entrenador** accede a los alumnos de **sus grupos** y a los reportes de esos alumnos; **ve** todos los planes del club pero **solo edita los que ha creado**.
- Un **admin** accede a todo lo de **su** club.

### Dónde se enforce la autorización

La autorización se **enforce en cada módulo**, sobre sus propios recursos, usando sus **proyecciones locales** de los datos de relación (events-first, ADR-0007). **No hay un módulo central de autorización** al que llamar — sería el acoplamiento síncrono que events-first descarta.

Lo común vive en un **núcleo compartido** (*shared kernel*) pequeño: el *principal* (rol, `userId`, `clubId`) y las primitivas de decisión. Como la matriz es **fija** (reglas en código), esas reglas viven también en ese núcleo compartido — fuente única, sin un módulo en runtime que no tendría cambios que difundir.

### Dónde vive cada capa (arquitectura hexagonal — ADR-0008)

- **RBAC** → en el **adaptador de entrada** (controladores REST): primera reja, barata, declarativa.
- **Nivel de objeto** → en la **capa de aplicación** (los casos de uso): el caso de uso tiene el contexto de dominio para decidir si quien pide puede tocar el objeto. Las reglas de relación se centralizan en un **servicio de autorización por módulo** (apoyado en el núcleo compartido) para no duplicarlas ni olvidarlas; los datos de relación que provienen de otro módulo se leen de una **proyección local** mantenida por eventos de dominio (*events-first*, ADR-0007).
- **`club_id`** → en el **acceso a datos** (los repositorios): toda query filtrada por club — defensa en profundidad.

### Auditoría de accesos

Con datos de salud, el RGPD exige poder demostrar quién accedió a qué. Se mantiene un **registro de auditoría *append-only*** con alcance **ligero**:

- **Accesos denegados** — todo intento de autorización fallido (señal de ataque o de bug).
- **Accesos a datos sensibles** — quién vio o editó una ficha de alumno o un reporte de sesión, y cuándo.

Lo emite la capa de autorización. No se registra cada lectura trivial — solo lo que aporta (responsabilidad proactiva del RGPD para lo sensible, señal de seguridad por las denegaciones).

### Regla de oro

La autorización se comprueba **siempre en el servidor, en cada petición**. Que la interfaz oculte un botón es comodidad visual, **no** seguridad: la API se puede llamar directamente. La UI nunca es la barrera.

## Consecuencias

### Positivas

- Cierra la vulnerabilidad IDOR — un usuario no puede acceder a objetos de otro, ni sueltos ni en listados.
- Las operaciones quedan restringidas por rol de forma declarativa y simple.
- El aislamiento por `club_id` deja preparado el multi-club.
- Modelo proporcional al problema: sin motor de políticas que operar.
- La autorización por módulo respeta la autonomía de events-first; el núcleo compartido evita duplicar el *principal*.
- El registro de auditoría da la responsabilidad proactiva del RGPD para los datos sensibles.

### Negativas / coste asumido

- Exige **disciplina**: la comprobación a nivel de objeto debe aplicarse en **cada** caso de uso que cargue un objeto o devuelva una lista; un olvido es una fuga.
- La matriz de visibilidad se reparte entre los módulos (cada uno autoriza sus recursos) — hay que mantener la coherencia con revisión.
- El servicio de autorización necesita datos de relación (qué alumno está en qué grupo, qué grupo es de qué entrenador) cuyo origen es el módulo Club y taxonomía. Como la comunicación entre módulos es *events-first* (ADR-0007), cada módulo que autoriza mantiene una **proyección local** de esas relaciones, alimentada por eventos de dominio — no consulta a otro módulo.
- Escribir el registro de auditoría desde la capa de autorización es trabajo extra.

### Riesgos y mitigaciones

- **Comprobación a nivel de objeto olvidada en un caso de uso** → centralizar las reglas en el servicio de autorización; tests de autorización por caso de uso; revisión de código atenta; pruebas explícitas de acceso cruzado (intentar ver el objeto o la lista de otro y esperar un rechazo).
- **Datos de relación rancios** (un alumno cambia de grupo) → la proyección local que usa la autorización es *eventualmente consistente*: tras un cambio de relación hay una ventana breve hasta que el evento se procesa. Es aceptable para la autorización a esta escala; los eventos se procesan con prontitud y la ventana es mínima.
- **Fuga entre clubes** → filtro por `club_id` sistemático en el acceso a datos, como defensa en profundidad además de las capas 1 y 2.

## Notas

- El detalle fino de la matriz (p. ej. qué ve exactamente un entrenador en la vista de salud del club) se concreta al implementar cada funcionalidad; este ADR fija la política, no cada permiso.
- MFA y login con Google (ADR-0003) no afectan a este modelo: la autorización parte del usuario ya autenticado, sea cual sea el método.
- Si en el futuro la autorización deja de ser fija y pasa a ser **configurable** (p. ej. cada club definiendo sus propios roles y permisos), se reabre esta decisión con un **módulo central de Autorización** que posea esa configuración y difunda sus cambios al resto de módulos por eventos.
