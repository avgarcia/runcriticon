# 06 — Vista "hoy" del alumno

> Pantalla de entrada del alumno cuando abre la app. **En menos de 5 segundos** tiene que saber qué tiene que hacer hoy. Si no, no la vuelve a abrir.

## Contexto

- **Rol**: alumno.
- **Cuándo se accede**: cada mañana antes de entrenar; o por la noche para ver el día siguiente.
- **Frecuencia**: ideal 1-2 veces al día.
- **MUSTs cubiertos**: M13 (vista "hoy").
- **Hipótesis crítica**: **H3** (el alumno entiende en < 5s qué tiene que hacer).

## Objetivo del usuario

> "Abrir la app, ver la sesión de hoy, salir a entrenar."

## Inputs

- Plan publicado por el entrenador para esta semana del grupo del alumno (ver [spec 05](05-coach-week-editor.md)).
- Personalizaciones aplicadas a este alumno (M12). Si las hay, prevalecen sobre la sesión del grupo.

## Layout principal — móvil-first

Esta pantalla está pensada **primero para móvil** (es el dispositivo de uso real). El escritorio es una variante.

### Móvil (vista principal)

```
┌──────────────────────────────────┐
│ ☰  Runcriticon         Marta 🔔  │ region:header
├──────────────────────────────────┤
│                                  │
│  Miércoles 3 de abril            │ region:date
│                                  │
│  HOY                             │ region:section-label
│  ┌────────────────────────────┐  │
│  │ 🔥 Series                  │  │
│  │                            │  │
│  │ 8 x 400 m                  │  │
│  │ a 3:30/km                  │  │
│  │ R: 1:30                    │  │
│  │                            │  │
│  │ Calentar 15 min            │  │
│  │ Vuelta a la calma 10 min   │  │
│  │                            │  │
│  │ 📝 "Las dos últimas a tope │  │
│  │ si te sientes bien"        │  │
│  │                            │  │
│  │ ✉ De tu entrenador:        │  │ (solo si hay
│  │ "Vuelves de lesión,        │  │  personalización
│  │  no te pases."             │  │  con mensaje)
│  │                            │  │
│  │  [Marcar como hecho]       │  │ region:primary-cta
│  │  [Reajustar día ▾]         │  │ region:secondary-cta
│  └────────────────────────────┘  │ region:today-card
│                                  │
│  ESTA SEMANA                     │ region:section-label
│  ─────────                       │
│  Lun  Mar  Mié  Jue  Vie  Sáb Dom│
│   💤   ⚡  [✓]   ⚡   💤   🏃  🏔  │ region:week-strip
│   ✓    ✓   hoy   —   —    —   — │
│                                  │
│                       [Ver plan] │
├──────────────────────────────────┤
│ 🏠 Hoy   📅 Plan   💬 Mensajes  │ region:bottom-nav
└──────────────────────────────────┘
```

### Escritorio (vista secundaria)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ☰  Runcriticon                                          Marta 🔔    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Miércoles 3 de abril                                               │
│                                                                     │
│  HOY                                                                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │ 🔥 Series                                                       │ │
│  │                                                                 │ │
│  │ 8 x 400 m a 3:30/km  ·  R: 1:30                                │ │
│  │ Calentar 15 min · Vuelta a la calma 10 min                     │ │
│  │                                                                 │ │
│  │ 📝 "Las dos últimas a tope si te sientes bien"                 │ │
│  │                                                                 │ │
│  │ ✉ De tu entrenador: "Vuelves de lesión, no te pases."          │ │ (solo si hay mensaje)
│  │                                                                 │ │
│  │     [Marcar como hecho]    [Reajustar día ▾]                    │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ESTA SEMANA                                                        │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Lun     Mar     Mié     Jue     Vie     Sáb     Dom            │ │
│  │  Desc.   Series  [Series] Series  Desc.   Rodaje  Tirada larga  │ │
│  │   ✓       ✓      hoy      —       —       —       —             │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:header` (móvil)

- Icono menú ☰ a la izquierda (abre nav drawer).
- Título "Runcriticon" o logo.
- Avatar + nombre del alumno a la derecha.
- Campana 🔔 con contador de notificaciones (no en MVP estricto, pero placeholder reservado).

### `region:date`

Fecha de hoy en formato legible: "Miércoles 3 de abril".

### `region:today-card`

**El elemento crítico.** Resume la sesión de hoy con cero ambigüedad.

Contenido:

- **Icono + tipo de sesión**: 🔥 Series · 🏃 Rodaje · 🏔 Tirada larga · 💤 Descanso · ⚡ Fartlek · 💪 Fuerza · 🎯 Otro.
- **Estructura principal**: distancia, ritmo, recuperación (formato según tipo, mismo formato que en [spec 05](05-coach-week-editor.md)).
- **Calentamiento y vuelta a la calma**: si aplican.
- **Notas del entrenador para el grupo**: con icono 📝, en estilo "cita". Vienen de la sesión base.
- **Mensaje del entrenador para ti** (opcional): con icono ✉, en estilo "cita" pero diferenciable. Solo aparece si la sesión tiene una personalización (M12) aplicada al alumno **y** el entrenador ha rellenado el campo de mensaje. No hay ningún otro indicador de que la sesión está personalizada; el alumno simplemente ve su sesión resuelta.
- **CTA primario**: "Marcar como hecho" (lleva al [spec 07](07-student-report.md)).
- **CTA secundario**: "Reajustar día" (desplegable o lleva a spec 07).

Estados de la card:

- **Pendiente** (lo descrito).
- **Hecho** (ya reportado): card en gris con tick verde, sin CTA primario, opción de "Editar reporte" o "Ver lo que reporté".
- **Hoy es descanso**: card más simple, fondo distinto, sin CTAs salvo "Cambiar a entrenar" (caso raro pero útil para reajuste M18 inverso).
- **No hay sesión hoy** (entrenador no publicó / día vacío): card con texto "Hoy no hay sesión programada" + sugerencia "Habla con tu entrenador si crees que es un error".

### `region:week-strip`

Vista compacta de la semana. Cada día:

- Letra del día (Lun, Mar, ...).
- Icono del tipo de sesión.
- Indicador de estado: ✓ hecho · ⚡ parcial · ✗ no hecho · "—" futuro · "hoy" si es el día actual (resaltado).

Comportamiento:

- Clic en un día pasado o futuro: lleva a vista de detalle de esa sesión.
- Comprende 7 días: lunes a domingo.
- En escritorio, más ancho con tipo de sesión en texto.

Link inferior "Ver plan" (cta:view-plan): lleva a la vista de calendario semanal/mensual del alumno (post-MVP completo; en MVP solo semana actual). Si no existe, ocultar enlace.

### `region:bottom-nav` (móvil)

Pestañas inferiores fijas:

- 🏠 **Hoy** (la pantalla actual).
- 📅 **Plan** (vista semanal completa, alternativa a `region:week-strip`).
- 💬 **Mensajes** (post-MVP; en MVP puede ser solo el badge con N comentarios sin leer).

> Decisión: en MVP estricto podemos prescindir de "Mensajes" si los comentarios contextuales (SHOULD) no entran. En ese caso, solo Hoy y Plan.

## Acciones

| Acción | Resultado |
|---|---|
| Marcar como hecho | Va a [spec 07](07-student-report.md). |
| Reajustar día | Desplegable rápido con: "No puedo entrenar hoy" (mueve sesión a mañana) · "Estoy muy cansado" (marca como descansado) · "Más opciones..." (lleva a spec 07 sección de reajuste). |
| Click en día de la semana | Vista detalle de ese día. |
| Ver plan | Vista de calendario semanal completo. |
| Tap en campana | Lista de notificaciones (post-MVP). |
| Tap en avatar | Menú de cuenta (cerrar sesión, mis datos). |

## Estados de la pantalla

1. **Sesión pendiente** — lo descrito.
2. **Sesión completada (reporte ya enviado)** — card en estado "hecho", muestra resumen del reporte.
3. **Descanso programado** — card simple, sin CTAs principales.
4. **Sin sesión programada** — empty state explicativo.
5. **Plan no publicado todavía** — banner: "Tu entrenador aún no ha publicado el plan de esta semana. Vuelve más tarde."
6. **Plan publicado pero estás "lesionado" / "descanso"** (estado del alumno via tag) — banner: "Estás en estado 'lesión'. No tienes sesiones esta semana. Habla con tu entrenador."
7. **Sin grupo asignado** — empty state: "Aún no estás en ningún grupo. Habla con el admin del club." (no debería pasar en condiciones normales).
8. **Cargando** — skeleton de la card.
9. **Sin conexión** — última versión cacheada con banner: "Sin conexión. Datos del [fecha]."

## Interacciones clave

### Interacción A — Camino feliz: ver sesión, salir a entrenar, marcar al volver

1. Marta abre la app por la mañana.
2. Ve la card grande con "Series · 8x400 a 3:30/km".
3. Lee las notas.
4. Sale a entrenar (cierra la app).
5. Vuelve por la tarde, abre la app.
6. Misma card pendiente. Pulsa "Marcar como hecho".
7. Va a [spec 07](07-student-report.md).

### Interacción B — Reajuste rápido por imprevisto

1. Marta abre la app, ve que toca "Series".
2. Sale tarde de trabajar.
3. Pulsa "Reajustar día" → desplegable.
4. Pulsa "No puedo entrenar hoy".
5. Modal: "¿Mover la sesión a mañana?" (Sí / Marcar como saltada / Cancelar).
6. Confirma "Sí" → la sesión de hoy se marca como movida; la del jueves se reemplaza por las series (con confirmación adicional si el jueves ya tenía algo: "Reemplazar [Series Jueves]?").
7. Vuelve a la vista "hoy" con confirmación: "Sesión movida al jueves".

### Interacción C — Día de descanso

1. Marta abre la app, hoy es lunes (descanso).
2. Ve card pequeña con icono 💤 y texto "Descanso".
3. Opción "Cambiar a entrenar" para casos en que quiera hacer algo (rara, pero útil).
4. Si no toca nada, no hay que reportar — el descanso se marca automáticamente al final del día.

### Interacción D — Plan no publicado

1. Marta entra el domingo por la noche para ver el plan de la semana que empieza.
2. Card: "Tu entrenador aún no ha publicado el plan de esta semana."
3. Botón sutil "Recordar más tarde" → notificación push (post-MVP).
4. La semana anterior (la que termina) aparece en la `region:week-strip` con sus estados.

## Validaciones y errores

- Esta pantalla es principalmente de lectura; pocas validaciones.
- Si el plan publicado tiene sesión con error de datos (campos vacíos), mostrar igual con mejor esfuerzo + banner sutil: "Algunos datos faltan, contacta a tu entrenador".

## Mensajes de feedback

- Reajuste exitoso → toast verde.
- Marcado como hecho → toast verde, vuelve a la vista con card en estado "hecho".
- Error de conexión → toast rojo + indicador permanente arriba.

## Responsive (escritorio)

- Card de hoy más ancha pero misma estructura.
- Week strip con más espacio por día (texto en lugar de solo icono).
- Sin bottom nav (sustituida por nav lateral del alumno: Hoy / Plan / Cuenta).

## Opciones de diseño a explorar

### Card de hoy — Opción A (recomendada): card grande única dominante

Una sola card central que ocupa la mayor parte de la pantalla. La semana abajo en formato resumido.

**Pros**: cumple "en 5 segundos entiendo qué hago hoy".
**Contras**: el alumno pierde un poco la vista de conjunto.

### Card de hoy — Opción B: card de hoy + 3 cards (ayer, hoy, mañana)

Carrusel horizontal con día anterior, hoy y siguiente.

**Pros**: contexto temporal mayor.
**Contras**: más densidad, riesgo de no cumplir los 5s.

### Card de hoy — Opción C: timeline vertical de la semana, "hoy" expandido

Lista de los 7 días, hoy expandido con todo el detalle, los demás colapsados.

**Pros**: contexto + foco en hoy.
**Contras**: más scroll inicial.

**Recomendación**: validar **A y C** con alumnos. A para cumplir H3, C si emerge la necesidad de contexto.

### Notas del entrenador — Opción A (recomendada): visible inline en la card

Lo descrito. La nota se ve directamente al abrir la card.

### Notas del entrenador — Opción B: acordeón "Ver notas"

La nota se oculta tras un botón.

**Pros**: card más limpia.
**Contras**: el alumno se pierde info clave.

**Recomendación**: **A**. Las notas son contexto esencial.

### Botón "Reajustar día" — Opción A (recomendada): desplegable rápido con 2-3 opciones

Lo descrito. Acceso rápido a las opciones más comunes sin abrir spec 07.

### Botón "Reajustar día" — Opción B: lleva directamente a spec 07

Sin desplegable. Click → pantalla completa de reajuste.

**Pros**: simpler.
**Contras**: más fricción para el 80% de casos comunes.

**Recomendación**: **A** para los 2-3 casos típicos + "Más opciones" para casos raros.

## Criterios de validación con usuario

- ✅ El alumno abre la app y, sin pensar, identifica la sesión de hoy en < 5s.
- ✅ Entiende qué significa cada campo (distancia, ritmo, recuperación) sin preguntar.
- ✅ Si su sesión lleva mensaje del entrenador, lo lee con claridad (y entiende que va dirigido a él, no a todo el grupo).
- ✅ Encuentra el botón para reportar sin titubear.
- ✅ Para reajustar día tarda < 15s desde abrir la app hasta confirmar el cambio.
- ❌ Si pregunta "¿qué tengo que hacer?" → la card es ambigua, rediseñar copy.
- ❌ Si toca el día equivocado en `week-strip` por confusión → mejorar identificación visual del día "hoy".
- ❌ Si no abre la app al día siguiente → o no necesita la app o la card no le da valor.
