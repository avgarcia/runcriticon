# 07 — Reporte de sesión + reajuste de día (M18)

> Dos flujos relacionados que viven en la misma área conceptual: **reportar** una sesión ejecutada y **reajustar** un día cuando hay imprevisto. Ambos críticos para mantener al alumno enganchado.

## Contexto

- **Rol**: alumno.
- **Cuándo se accede**:
  - **Reporte**: después de entrenar (o al final del día si descansó).
  - **Reajuste**: cuando hay imprevisto (cansancio, viaje, lesión, trabajo).
- **Frecuencia**: 4-6 veces por semana (1 por sesión que se reporta o se reajusta).
- **MUSTs cubiertos**: M14 (marcar hecho/parcial/no hecho + nota), M18 (reajuste de día).
- **Patrón detectado** en entrevistas: **P3** (reajuste rápido por imprevisto, JM y AVG).

## Objetivo del usuario

> "Reportar mi sesión en menos de 15 segundos sin tener que pensar."
>
> "Cuando un día no puedo, mover la sesión sin tener que esperar respuesta de mi entrenador."

## Inputs

- Sesión existente en el plan del día (o del día seleccionado).
- Personalizaciones aplicadas (M12) si las hay.

---

## Flujo A — Reporte de sesión

Trigger: botón "Marcar como hecho" de la [vista hoy](06-student-today.md), o clic en una sesión pasada de la `week-strip`.

### Pantalla del reporte (móvil-first)

```
┌──────────────────────────────────┐
│ ← Volver           Reportar      │ region:header
├──────────────────────────────────┤
│  Series · Miércoles 3 abr        │ region:context
│  8 x 400 a 3:30/km · R 1:30      │
│                                  │
│  ¿Cómo ha ido?                   │ region:question
│                                  │
│  ┌────────────────────────────┐  │
│  │ ✓  Hecho  (tal cual)       │  │ region:status-options
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ ⚡  Parcial (no completo)   │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ ✗  No hecho                 │  │
│  └────────────────────────────┘  │
│                                  │
│  Cómo te has sentido (opcional)  │ region:effort
│  😩  😕  😐  🙂  💪              │
│   1   2   3   4   5              │
│                                  │
│  Notas (opcional)                │ region:notes
│  ┌────────────────────────────┐  │
│  │ Las primeras 4 me costaron, │  │
│  │ las otras 4 muy bien...     │  │
│  │                             │  │
│  └────────────────────────────┘  │
│                                  │
│  🟢  Adjuntar datos del reloj    │ region:watch-attach (post-MVP, SHOULD)
│      (subir FIT/GPX)             │
│                                  │
│  ⚠ ¿Algún dolor o molestia?      │ region:pain-flag
│  ☐ Sí — quiero avisar           │
├──────────────────────────────────┤
│         [Cancelar]   [Enviar]    │ region:footer
└──────────────────────────────────┘
```

### Componentes del reporte

#### `region:context`

Resumen de la sesión que se está reportando. Compacto, no editable.

#### `region:question`

Texto guía claro: *"¿Cómo ha ido?"*.

#### `region:status-options`

Tres cards/botones grandes, mutuamente excluyentes:

- ✓ **Hecho** — color verde. Implica que cumplió la sesión como estaba planteada.
- ⚡ **Parcial** — color amarillo. Hizo parte. Al seleccionar, aparece campo opcional: "¿Cuánto hiciste?" (texto libre, ej. "4 de 8 series").
- ✗ **No hecho** — color rojo apagado. No entrenó. Al seleccionar, aparece la pregunta "¿Por qué?" con razones predefinidas: **cansancio · trabajo · viaje · enfermedad · sin tiempo · molestias · otra** (texto libre). **Si elige "molestias", la bandera de dolor (`region:pain-flag`) se marca automáticamente** y se dispara la alerta al entrenador — el alumno no tiene que acordarse de marcarla aparte.

Selección con tap único. Visual claro del estado seleccionado.

#### `region:effort` (RPE simplificado)

Escala visual de 5 emojis 😩 (1) → 💪 (5), labeled como "Cómo te has sentido". **Obligatoria cuando el status es "Hecho" o "Parcial"**; no aparece si es "No hecho" (no procede valorar lo que no se hizo). Tap selecciona uno.

#### `region:notes`

Textarea libre, opcional. Placeholder: *"Algo que quieras contarle a tu entrenador..."*.

#### `region:watch-attach` (post-MVP / SHOULD)

Botón para subir un archivo FIT/GPX o conectar Strava/Garmin (cuando se active la importación). En MVP, **oculto o deshabilitado con tooltip "Próximamente"**.

#### `region:pain-flag`

Checkbox o toggle: "¿Algún dolor o molestia?". Si marcado, aparece textarea adicional: *"Cuéntale a tu entrenador (ubicación, intensidad)"*.

**Activación automática**: si el alumno selecciona "molestias" como motivo del "No hecho", la bandera de dolor se activa sin que tenga que marcarla aparte; el textarea de descripción se muestra para que pueda añadir detalle.

Importancia: este campo dispara una alerta directa al [panel de alertas del entrenador](08-coach-alerts.md), independiente del status (hecho/parcial/no hecho).

#### `region:footer`

- Botón "Cancelar" secundario: confirma si hay datos sin guardar.
- Botón "Enviar" primario: guarda y cierra. Solo se requiere que esté seleccionado un status. RPE, notas y demás son opcionales.

### Acciones del reporte

| Acción | Resultado |
|---|---|
| Seleccionar status | Marca visual. Si es "Parcial" o "No hecho", aparece subcampo. |
| Seleccionar RPE | Marca emoji. |
| Escribir nota | Texto libre. |
| Marcar dolor | Aparece textarea. |
| Enviar | Persiste reporte. Si dolor está marcado, dispara alerta. Toast: "Reporte enviado". Vuelve a vista "hoy" con card en estado "hecho/parcial/no hecho". |
| Cancelar | Confirma si hay cambios. |

### Estados del reporte

1. **Inicial** — formulario vacío.
2. **Status seleccionado** — botón "Enviar" activo.
3. **Reporte ya enviado (editando)** — al entrar de nuevo en una sesión ya reportada, el formulario se rellena con lo enviado. Banner: "Editando reporte enviado hace X minutos/horas". Botón cambia a "Actualizar".
4. **Error al enviar** — toast rojo + reintentar.

### Validaciones

- **Status**: obligatorio.
- **Valoración 1-5**: obligatoria si el status es "Hecho" o "Parcial"; no aplica si es "No hecho".
- **Motivo del "No hecho"**: obligatorio si el status es "No hecho".
- Notas: opcionales. No hay límite duro de longitud (razonable: 1000 caracteres).
- Si se marca dolor pero no se rellena la descripción, se acepta igualmente — con la marca de dolor basta.

---

## Flujo B — Reajuste de día (M18)

Trigger: botón "Reajustar día" de la [vista hoy](06-student-today.md), o clic en una sesión futura/de hoy.

### Pantalla del reajuste

Dos modos de acceso:

**Modo rápido (desde desplegable en vista hoy)**: 2-3 opciones directas, ver [spec 06](06-student-today.md) interacción B. Sin pantalla aparte.

**Modo extendido (esta spec)**: pantalla / side sheet con todas las opciones.

```
┌──────────────────────────────────┐
│ ← Volver        Reajustar día    │
├──────────────────────────────────┤
│  Series · Miércoles 3 abr        │ region:context
│  8 x 400 a 3:30/km               │
│                                  │
│  ¿Qué quieres hacer?             │ region:options
│                                  │
│  ┌────────────────────────────┐  │
│  │ 📅 Mover a otro día        │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 💤 Marcar como descanso    │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ ✗ Saltarla (sin recuperar) │  │
│  └────────────────────────────┘  │
│  ┌────────────────────────────┐  │
│  │ 🤕 Avisar de lesión        │  │
│  └────────────────────────────┘  │
│                                  │
│  Cuéntaselo a tu entrenador      │
│  (opcional):                     │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  └────────────────────────────┘  │
│                                  │
├──────────────────────────────────┤
│         [Cancelar]    [Aplicar]  │
└──────────────────────────────────┘
```

### Componentes del reajuste

#### `region:context`

Igual que en el reporte. Recuerda qué sesión se está modificando.

#### `region:options`

Cards grandes, mutuamente excluyentes:

1. **📅 Mover a otro día**
   - Al seleccionar, aparece selector de día (resto de días de esta semana o próxima semana, máximo +7 días).
   - Si el día destino tiene sesión, aviso: "Ese día tiene [Series]. ¿Reemplazar / Intercambiar / Cancelar?".

2. **💤 Marcar como descanso**
   - La sesión de hoy queda como "descansada". No se intenta recuperar.
   - Si el alumno estaba realmente cansado, esto es lo más honesto.

3. **✗ Saltarla (sin recuperar)**
   - Similar a "marcar como descanso" pero con motivo distinto (trabajo, viaje).
   - La diferencia conceptual: "descanso" es decisión fisiológica; "saltar" es agenda.

4. **🤕 Avisar de lesión**
   - Marca el reporte con flag de dolor.
   - Cambia el estado del alumno a "lesión" (vía tag `estado`) si se confirma en modal: "¿Cambio tu estado a 'lesión' hasta nuevo aviso?".
   - Dispara alerta inmediata al entrenador (alta prioridad).

#### `region:message`

Textarea opcional para mensaje al entrenador. Va junto al cambio.

#### `region:footer`

- Cancelar / Aplicar.

### Acciones del reajuste

| Acción | Resultado |
|---|---|
| Mover a otro día | Selector + confirmación. Sesión movida; vista "hoy" se actualiza. Si el día origen sigue siendo hoy, queda como "movida". |
| Marcar descanso | Sesión marcada como descansada. Confirmación. |
| Saltar | Sesión marcada como saltada con razón opcional. |
| Avisar lesión | Modal de confirmación del cambio de estado. Alerta al entrenador. |
| Aplicar | Persiste y cierra. Toast verde con descripción de lo aplicado. |

### Estados del reajuste

1. **Inicial** — opciones visibles, ninguna seleccionada.
2. **Opción seleccionada** — formulario complementario visible (selector de día, motivo, etc.).
3. **Conflicto al mover** — modal de resolución.
4. **Error** — toast rojo.

### Validaciones

- Si "mover", el día destino debe estar en el rango +7 días.
- Si "lesión", confirmar cambio de estado del alumno (afecta a grupos y planes futuros).
- No se permite mover una sesión de un día pasado (esos días solo se reportan, no se reajustan).

---

## Combinación con el flujo de reporte

A veces el flujo es mixto: el alumno "marca como parcial" y luego quiere mover lo que no hizo a otro día. Para evitar dos pantallas:

- En el reporte, si selecciona "Parcial" o "No hecho", aparece **al final** un enlace sutil: *"¿Quieres mover lo que falta a otro día?"* → lleva al flujo de reajuste con la sesión actual como contexto.

## Mensajes de feedback

- Reporte enviado → toast verde "Reporte enviado" (4s, opción "Deshacer").
- Sesión movida → toast verde "Movida al jueves" (con opción deshacer).
- Lesión avisada → toast "Avisado a tu entrenador. Esperamos que te recuperes pronto" (más cálido).
- Error → toast rojo persistente con opción reintentar.

## Responsive (escritorio)

- Pantallas similares pero centradas con ancho máximo de ~600px.
- En escritorio, el reporte podría aparecer como **modal** o **side sheet** en lugar de pantalla completa, conservando la vista "hoy" detrás. Decisión en validación.

## Opciones de diseño a explorar

### Reporte — Opción A (recomendada): pantalla completa con 3 status grandes

Lo descrito. Tap-friendly, foco total.

**Pros**: cumple el criterio "15s para reportar". Sin distracciones.
**Contras**: salir y volver puede sentirse abrupto.

### Reporte — Opción B: bottom sheet desde vista "hoy"

El reporte aparece como hoja inferior que sube en la vista "hoy" sin perder contexto.

**Pros**: contexto conservado.
**Contras**: más altura, más complejidad técnica.

### Reporte — Opción C: reporte ultra-rápido inline en la card de hoy

Los 3 botones de status como acciones directas en la card de la vista "hoy", sin pantalla aparte. RPE y notas opcionales en un acordeón debajo si el alumno quiere ampliar.

**Pros**: máxima rapidez (5s incluso).
**Contras**: pierde estructura para reportes más ricos (RPE, dolor, etc.).

**Recomendación**: validar **A** y **C** con alumnos. A es más completo; C es más rápido. Para H3 (5s), C gana; para reportes con info útil, A.

### Reajuste — Opción A (recomendada): cards con 4 opciones

Lo descrito.

### Reajuste — Opción B: lista compacta de 4 opciones (más densa)

Filas en lugar de cards.

**Pros**: más eficiente en altura.
**Contras**: menos visual / tactil.

**Recomendación**: **A** para móvil; **B** posible en escritorio.

### Avisar lesión — Opción A (recomendada): opción dentro de reajustar día

Lo descrito.

### Avisar lesión — Opción B: botón aparte siempre visible

Botón "🤕 Tengo molestias" en la vista "hoy", siempre accesible (no solo en reajuste).

**Pros**: visibilidad permanente para el caso urgente.
**Contras**: ocupa espacio prime real estate.

**Recomendación**: validar. Si las lesiones son recurrentes, B; si son esporádicas, A es suficiente.

## Criterios de validación con usuario

- ✅ El alumno reporta una sesión "Hecho" en < 15s desde abrir la app.
- ✅ Entiende la diferencia entre "Hecho" / "Parcial" / "No hecho" sin pensar.
- ✅ Encuentra el flag de dolor cuando es necesario (no se le pasa).
- ✅ Mueve una sesión a otro día en < 30s.
- ✅ Para una lesión real, sabe que su entrenador se va a enterar.
- ❌ Si el alumno tarda > 30s en un reporte simple → simplificar la pantalla, quitar campos opcionales del flujo principal.
- ❌ Si no encuentra el reajuste → mejorar visibilidad desde vista "hoy".
- ❌ Si confunde reporte y reajuste → diferenciar visualmente las pantallas.
