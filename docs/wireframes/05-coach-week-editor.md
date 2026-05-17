# 05 — Editor del plan semanal del entrenador

> Pantalla donde el entrenador diseña y publica las sesiones de la semana de un grupo. **Es la batalla del MVP**: si crear la semana cuesta más que el Excel actual, el entrenador no vuelve.

## Contexto

- **Rol**: entrenador del club.
- **Cuándo se accede**: cada vez que va a planificar la próxima semana (típicamente domingos por la noche) o a ajustar la actual.
- **Frecuencia**: 1-2 veces por semana por cada grupo que lleva (entre 1 y 5 veces por semana total).
- **MUSTs cubiertos**: M10 (editor de sesión), M11 (publicar plan al grupo), M12 (personalizar sesión por alumno).
- **Riesgo principal**: R2 (el modelo plan-por-grupo no encaja). VG explícito: *"si me obligas a separar por distancias voy a escribir 40 planes idénticos cambiando una línea"*.

## Objetivo del usuario

> "Tener publicado el plan de la semana del grupo en menos de 10 minutos (vs. 45 min hoy duplicando)."

## Inputs

- Grupos asignados al entrenador (con sus alumnos y tags) — ver [spec 04](04-group-builder.md).
- Plantillas de sesión del entrenador (opcional, post-MVP completo; en MVP solo las suyas guardadas a mano).

## Layout principal — calendario semanal del grupo

Layout estándar (header + nav lateral del entrenador + contenido).

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Grupo: Maratón Valencia avanzado (12 alumnos) ▾    [ ← Sem 14 → ]          │ region:header
│                                  Semana 14 (1 - 7 abr 2026)                │
├────────────────────────────────────────────────────────────────────────────┤
│ [Copiar semana anterior]  [Plantilla ▾]                  [Publicar semana] │ region:toolbar
├────────────────────────────────────────────────────────────────────────────┤
│  Lun 1     Mar 2     Mié 3     Jue 4     Vie 5     Sáb 6     Dom 7         │ region:day-headers
│ ┌────────┬────────┬────────┬────────┬────────┬────────┬─────────────────┐ │
│ │Descanso│Series  │Rodaje  │Series  │Descanso│Rodaje  │Tirada larga     │ │
│ │        │8x400 a │10 km   │5x1000 a│        │8 km    │25 km a Z2       │ │
│ │        │ 3:30   │ 5:20   │ 4:00   │        │ 5:30   │                 │ │
│ │        │R 1:30  │        │R 2:00  │        │        │                 │ │
│ │        │        │        │        │        │        │                 │ │
│ │        │[ajustes│        │        │        │        │ [ajustes 1]     │ │
│ │        │ 2]     │        │        │        │        │                 │ │
│ │   [+]  │        │   [+]  │        │   [+]  │        │                 │ │
│ └────────┴────────┴────────┴────────┴────────┴────────┴─────────────────┘ │
│  region:week-grid                                                          │
├────────────────────────────────────────────────────────────────────────────┤
│ Estado: borrador · Cambios sin guardar · Última edición: hace 3 min        │ region:status-bar
└────────────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:header`

- **Selector de grupo**: desplegable con los grupos del entrenador. Si solo lleva uno, muestra el nombre sin desplegable.
- **Navegación semanal**: flechas ← → + texto "Semana X (rango de fechas)".
- Botón rápido "Esta semana" (cta:current-week) para volver al presente, visible si no estamos en la semana actual.

### `region:toolbar`

- **[Copiar semana anterior]** (cta:copy-prev-week): copia las sesiones de la semana anterior. Si la semana actual tiene contenido, modal: "¿Sobrescribir o mezclar?".
- **[Plantilla ▾]** (cta:apply-template): desplegable con plantillas guardadas (en MVP solo las propias del entrenador). Aplicarla rellena la semana en blanco; si hay contenido, confirma.
- **[Publicar semana]** (cta:publish-week): primario. Solo activo si hay al menos 1 sesión o si se quiere publicar como "semana de descanso completo". Confirmación con resumen (ver interacción C).

### `region:week-grid`

Calendario de 7 columnas (días). Una fila (en MVP solo una sesión por día; multi-sesión en COULD).

Cada celda (día):

- Si vacía: fondo gris claro, icono "+" centrado al hacer hover. Clic abre el editor de sesión.
- Si con sesión: ver "Card de sesión" abajo.
- Fecha de cada día en el header de columna, hoy resaltado.

#### Card de sesión

```
┌────────────────────┐
│ Tipo: Series       │
│                    │
│ 8x400 a 3:30/km    │
│ R: 1:30            │
│                    │
│ Notas:             │
│ "Calentar 15 min" │
│                    │
│ [ajustes 2]        │
│ [···]              │
└────────────────────┘
```

- **Tipo de sesión** (etiqueta arriba): rodaje, series, fondo, tirada larga, descanso, fartlek, fuerza, otro. Cada tipo con su color/icono sutil.
- **Contenido principal**: distancia o tiempo, ritmo o intervalo, recuperación si aplica.
  - Para series: "8x400 a 3:30/km · R 1:30".
  - Para rodaje: "10 km a 5:20/km".
  - Para tirada larga: "25 km a Z2" o "2:30h a Z2".
  - Para descanso: solo "Descanso".
- **Notas libres**: 1 línea, truncada con "...".
- **[ajustes N]**: chip visible solo si hay personalizaciones para alumnos concretos (ver M12). Clic abre lista de personalizaciones.
- **[···]**: menú con: Editar · Duplicar a otro día · Eliminar · Convertir en plantilla.
- Clic en la card abre el editor de sesión.

### `region:status-bar`

Pie con información de estado:

- **Estado**: borrador · publicado · publicado con cambios sin guardar.
- **Última edición**: timestamp relativo.
- **Cambios sin guardar**: aviso visual + botón "Descartar cambios".

## Editor de sesión (side sheet)

Se abre al hacer clic en una celda vacía o sobre una sesión existente.

```
┌──────────────────────────────────────────────────────────┐
│ Sesión del miércoles 3 de abril                     [×]  │
├──────────────────────────────────────────────────────────┤
│ Tipo:    ( ) Descanso  ( ) Rodaje  (●) Series           │
│          ( ) Tirada    ( ) Fartlek ( ) Fuerza  ( ) Otro │
│                                                          │
│ Contenido principal (varía según tipo)                   │
│ ─────────────────────────────────────                    │
│ (caso Series, ej):                                       │
│   Estructura:  [8] x [400] m   o   [8] x [tiempo]       │
│   Ritmo:       [3:30] /km                                │
│   Recuperación:[1:30] [min ▾]                            │
│                                                          │
│ Calentamiento (opcional): [15] min                       │
│ Vuelta a la calma (opcional): [10] min                   │
│                                                          │
│ Notas para el alumno:                                    │
│ [ Calentar bien, el último 400 a tope si te sientes...]  │
│                                                          │
│ ───── Avanzado ─────                                     │
│ Personalizaciones para alumnos concretos: 2  [Gestionar] │
│                                                          │
├──────────────────────────────────────────────────────────┤
│            [Eliminar]  [Cancelar]    [Guardar sesión]    │
└──────────────────────────────────────────────────────────┘
```

### Componentes del editor de sesión

#### Tipo de sesión

Toggle/radio con los tipos comunes. La estructura del formulario cambia según el tipo (campos relevantes).

#### Contenido según tipo

| Tipo | Campos |
|---|---|
| Descanso | (ninguno) |
| Rodaje | Distancia OR tiempo, ritmo objetivo |
| Series | N x distancia (o tiempo), ritmo, recuperación |
| Tirada larga | Distancia OR tiempo, ritmo objetivo o zona |
| Fartlek | Estructura libre o "X min a ritmo / Y min suave" |
| Fuerza | Texto libre (en MVP), structured fields en COULD |
| Otro | Texto libre |

#### Ritmo objetivo

Input simple con formato `mm:ss` (validado). Opcional: selector "/km" o "/mi".

> **Nota arquitectónica**: aunque la UI del MVP solo permite ritmos absolutos, internamente se guarda como `{tipo: absoluto, valor: "3:30"}`. Cuando se active H5 (ritmos relativos a marcas), aparecerá una pestaña adicional "Relativo a marca" sin migrar datos. Ver [`vision.md`](../vision.md).

#### Notas para el alumno

Textarea libre. Visible en la vista "hoy" del alumno (spec 06). Soporta Markdown ligero (negrita, listas) — opcional en MVP, mejor texto plano.

#### Personalizaciones (acordeón)

Si hay personalizaciones aplicadas → contador visible. Clic en "Gestionar" abre modal:

```
┌──────────────────────────────────────────────────────────┐
│ Personalizaciones de la sesión del miércoles 3       [×] │
├──────────────────────────────────────────────────────────┤
│ Por defecto, todos los alumnos del grupo reciben:        │
│   Series 8x400 a 3:30/km · R 1:30                       │
│                                                          │
│ Personalizaciones:                                       │
│  ─────────────────                                       │
│  • Marta Sánchez   →  6x400 a 3:35/km  [editar] [quitar] │
│    Motivo: vuelve de lesión                              │
│                                                          │
│  • Juan Pérez      →  Descanso        [editar] [quitar]  │
│    Motivo: viaje                                         │
│                                                          │
│  [+ Añadir personalización]                              │
│                                                          │
│                                            [Cerrar]      │
└──────────────────────────────────────────────────────────┘
```

- Cada personalización: alumno + override + motivo opcional.
- Añadir: selecciona alumno (autocomplete entre los del grupo), abre editor de sesión inline para ese alumno solo. El motivo es un campo de texto libre opcional.

#### Botones del editor

- **Eliminar** (solo en sesión existente, en rojo apagado): confirma con modal.
- **Cancelar**: cierra. Si hay cambios, confirmar.
- **Guardar sesión** (primario): persiste. Toast.

## Acciones (vista calendario)

| Acción | Resultado |
|---|---|
| Seleccionar grupo | Carga el calendario del grupo. |
| Navegar semana | Carga la semana correspondiente. |
| Copiar semana anterior | Modal de confirmación + copia las sesiones (no personalizaciones). |
| Aplicar plantilla | Desplegable + aplicar. |
| Crear sesión (click "+" en día vacío) | Abre editor de sesión vacío para ese día. |
| Editar sesión | Abre editor con datos. |
| Duplicar sesión a otro día | Modal: selecciona día destino. Si hay sesión, sobrescribe con confirmación. |
| Eliminar sesión | Confirmación. |
| Publicar semana | Modal de confirmación con resumen (ver interacción C). |
| Descartar cambios | Vuelve al último estado publicado o al vacío si nunca se publicó. |

## Estados de la pantalla

1. **Semana vacía sin publicar** — todas las celdas con "+". Estado: borrador, sin publicar.
2. **Semana parcial sin publicar** — algunas sesiones, otras "+". Aviso: "X sesiones sin guardar".
3. **Semana publicada** — todas las sesiones bloqueadas con icono candado sutil. Banner: "Publicado el [fecha]. ¿Editar?".
4. **Editando una semana ya publicada** — al hacer cambios aparece aviso: "Tienes cambios. Vuelve a publicar para que los alumnos los reciban".
5. **Semana pasada** — modo lectura, sin editar. Aviso: "Esta semana ya ha pasado".
6. **Sin grupos asignados** — empty state con explicación: "Aún no tienes grupos asignados. Habla con el admin del club."
7. **Cargando** — skeleton de calendario.

## Interacciones clave

### Interacción A — Publicar la semana de un grupo en menos de 10 min

1. Entrenador entra, selecciona grupo "Maratón Valencia avanzado".
2. Pulsa "Copiar semana anterior". Modal: "Se copiarán 6 sesiones. ¿Mantener tus personalizaciones? (Sí / No)". Confirma.
3. Calendario lleno. Ajusta una sesión: clic en miércoles, modifica el número de series. Guarda.
4. Personaliza una sesión: en el viernes, clic, "Gestionar personalizaciones", añade override para Marta.
5. Pulsa "Publicar semana". Confirma. Toast: "Semana publicada para 12 alumnos."

Tiempo estimado: 5-8 minutos.

### Interacción B — Crear sesión desde cero

1. Click en una celda vacía.
2. Side sheet abierto. Selecciona "Series".
3. Estructura: 8 x 400, ritmo 3:30/km, R 1:30.
4. Notas: "Series progresivas, las dos últimas a tope".
5. Guarda. Side sheet se cierra, card aparece en la celda.

### Interacción C — Publicar y confirmar

1. Pulsa "Publicar semana".
2. Modal:
   ```
   ┌──────────────────────────────────────────────────────────┐
   │ Publicar semana 14 para Maratón Valencia avanzado    [×] │
   ├──────────────────────────────────────────────────────────┤
   │ Resumen:                                                 │
   │  • 6 sesiones planificadas (1 descanso)                 │
   │  • 12 alumnos recibirán el plan                          │
   │  • 2 personalizaciones aplicadas (Marta, Juan)           │
   │                                                          │
   │  □ Avisar por email a los alumnos                        │
   │                                                          │
   │                                  [Cancelar]  [Publicar]  │
   └──────────────────────────────────────────────────────────┘
   ```
3. Confirma. Snapshot de la membresía del grupo se congela para esta semana (cambios posteriores en tags no afectan al plan publicado).
4. Toast verde.

### Interacción D — Mover sesión por arrastre

Drag-and-drop de una card de sesión a otro día. Si el destino tiene sesión, modal: "Reemplazar la sesión existente?" (Sí / Intercambiar / Cancelar).

## Validaciones y errores

- Ritmo: formato `mm:ss` válido (1-60 min).
- Distancia / repeticiones: número positivo.
- Series sin estructura completa (ej. N x distancia sin ritmo) → no se puede guardar; aviso inline.
- Publicar sin ninguna sesión: aviso "¿Publicar una semana vacía como semana de descanso completo?".
- Editar una sesión de semana ya publicada: cambio visible inmediatamente al alumno (con aviso al entrenador: "El cambio se enviará a los 12 alumnos al guardar").

## Mensajes de feedback

- Guardado de sesión: toast verde inmediato.
- Publicación: toast verde + opción "Deshacer" (válida 30s).
- Cambios sin guardar al salir: modal de confirmación.

## Responsive (móvil)

El editor de plan es **principalmente desktop**, pero el entrenador puede querer ver y hacer cambios pequeños desde móvil:

- Calendario semanal → vista lista vertical (días apilados, cada día expandible).
- Editar una sesión → side sheet a pantalla completa.
- Drag-and-drop de sesiones no funciona; se reemplaza por opción "Mover a..." en el menú [···].
- Aviso al entrar desde móvil: "El editor funciona mejor en escritorio. Para cambios rápidos, sigue desde aquí."

## Opciones de diseño a explorar

### Vista principal — Opción A (recomendada): calendario de 7 columnas

Lo descrito. Estilo calendario semanal.

**Pros**: cubre la metáfora natural del entrenador (ya piensa por semana). Permite ver el conjunto.
**Contras**: en pantallas 13" las celdas son estrechas.

### Vista principal — Opción B: vista lista (un día por fila)

Lista vertical: cada fila un día. Más altura por día → más contenido visible.

**Pros**: cabe más información por sesión. Móvil-friendly por defecto.
**Contras**: pierde la vista de conjunto.

### Vista principal — Opción C: tabla densa con filas por tipo de sesión

Filas: Series, Rodaje, Fondo... Columnas: días. El entrenador ve "qué hago en cada día por tipo".

**Pros**: bien para clubs que estructuran por bloques temáticos.
**Contras**: rara para un entrenador con plan típico (3-4 tipos máximo).

**Recomendación**: A para escritorio, B para móvil. C es interesante pero secundaria.

### Editor de sesión — Opción A (recomendada): side sheet con formulario estructurado

Lo descrito. Campos según el tipo de sesión.

**Pros**: estructurado, valida, permite analytics futuras.
**Contras**: el entrenador a veces piensa en una frase libre; tener que llenar campos puede ser fricción.

### Editor de sesión — Opción B: textarea libre estilo "shorthand"

El entrenador escribe libremente: *"8x400 a 3:30, r 1:30, calentar 15 min, notas: las dos últimas a tope"*. El sistema NO interpreta; se guarda como texto.

**Pros**: rapidísimo. Replica cómo escriben hoy en WhatsApp.
**Contras**: imposible procesar para "ritmos relativos por marcas" (H5) o para análisis. Imposible mostrar bien en vista del alumno.

### Editor de sesión — Opción C: híbrida — formulario estructurado + textarea inferior

Lo descrito en A más una textarea de notas libres (ya está). Pero con un toggle "Modo libre" que oculta el formulario y muestra solo textarea para entrenadores que prefieren así.

**Recomendación**: validar A en wireframes. Si los entrenadores se quejan de fricción, evaluar C (toggle). B descartada en MVP (incompatible con H5).

### Personalización por alumno — Opción A (recomendada): modal aparte

Lo descrito. Mantiene el editor general limpio.

### Personalización por alumno — Opción B: vista expandida por alumno

Dentro del editor de sesión, lista plegable de "Por alumno" con todos los del grupo y campo de override.

**Pros**: visibilidad total.
**Contras**: muy ruidoso si el grupo es de 20+ alumnos y solo 1 tiene personalización.

**Recomendación**: A. Validar con entrenador del club piloto.

## Criterios de validación con usuario

- ✅ El entrenador del club piloto crea una semana completa (6 sesiones) en < 10 min en su segundo uso (primer uso será más lento mientras aprende la UI).
- ✅ Reutiliza "copiar semana anterior" sin pedir ayuda.
- ✅ Personaliza al menos 1 sesión para 1 alumno y entiende el flujo.
- ✅ Publica la semana y se siente seguro de que los alumnos recibirán el plan.
- ❌ Si abandona porque "es más fácil mi Excel" → revisar el editor de sesión y los atajos de copia/plantilla.
- ❌ Si la personalización por alumno le parece confusa → simplificar el flujo (probablemente eliminar el modal y poner inline).
