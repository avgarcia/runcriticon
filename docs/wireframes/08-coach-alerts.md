# 08 — Panel de alertas del entrenador (feedback por excepción) — M17

> Pantalla donde el entrenador ve **solo lo accionable**: alumnos con dolor, sin reportar, fuera de plan. Es el patrón explícitamente pedido por RG (*"no me muestres los 500 entrenos, muéstrame las alertas"*) y por PC (*"semáforo verde/amarillo/rojo"*). Patrón nuevo, conceptualmente distinto a una vista de cumplimiento.

## Contexto

- **Rol**: entrenador del club.
- **Cuándo se accede**: idealmente cada mañana (para responder rápido) y al final de semana (para detectar tendencias). Puede ser la pantalla de inicio del entrenador.
- **Frecuencia**: alta (varias veces al día si hay volumen).
- **MUSTs cubiertos**: M15 (vista de seguimiento por grupo, mejorada), M17 (panel de alertas).
- **Patrón detectado**: **P2** (feedback por excepción). Mitigador clave del dolor del entrenador con volumen.

## Objetivo del usuario

> "Saber en 60 segundos a quién tengo que atender hoy. No quiero ver a los que van bien."

## Inputs

- Reportes de sesión de las últimas semanas de los alumnos de los grupos del entrenador.
- Plan publicado actual de cada grupo.
- Estado actual de cada alumno (vía tag `estado` u otros).
- Histórico de actividad reciente.

## Layout principal

Layout estándar de entrenador. Pantalla de bienvenida ideal al entrar al sistema.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ Alertas                                            Filtros: [Mis grupos ▾]  │ region:page-header
├─────────────────────────────────────────────────────────────────────────────┤
│ 8 alertas activas · 3 nuevas hoy · 2 urgentes                              │ region:summary
├─────────────────────────────────────────────────────────────────────────────┤
│  URGENTE                                                                    │ region:section-urgent
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ 🤕 Marta Sánchez — dolor reportado hoy en la sesión                    │  │
│  │ Maratón Valencia avanzado · Hace 2 horas                              │  │
│  │ "Pinchazo en isquio derecho durante las series 3 y 4"                 │  │
│  │ [Ver alumno]   [Responder]   [Descartar]                              │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ ⚠ Juan Pérez — 9 días sin reportar                                    │  │
│  │ Iniciación CACO · Última actividad: 24 mar                             │  │
│  │ [Ver alumno]   [Enviar mensaje]   [Descartar]                          │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ATENCIÓN                                                                   │ region:section-warn
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ 📉 Ana López — saltó 2 sesiones esta semana                            │  │
│  │ Trail finde · Última actividad: hace 3 días                            │  │
│  │ [Ver alumno]   [Enviar mensaje]   [Descartar]                          │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  …                                                                          │
│                                                                             │
│  INFORMATIVO                                                                │ region:section-info
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │ ⚡ Pedro Cordero — entrenó muy por encima del ritmo objetivo           │  │
│  │ Maratón Valencia avanzado · Sesión del martes 2                       │  │
│  │ Objetivo 3:30/km · Reportado: nota "fui a 3:15/km"                    │  │
│  │ [Ver alumno]   [Descartar]                                             │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│  …                                                                          │
│                                                                             │
│ ─────                                                                       │
│ Descartadas en los últimos 7 días: 12  [Ver]                                │ region:dismissed-link
└─────────────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:page-header`

- Título "Alertas".
- Filtro: "Mis grupos ▾" — desplegable para filtrar por grupos concretos del entrenador. Por defecto: todos.
- (Opcional) Pestañas: "Hoy / Esta semana / Histórico" — en MVP, vista única "activas" + acceso a descartadas.

### `region:summary`

Frase con métricas clave:

- Total de alertas activas.
- Nuevas hoy.
- Urgentes (resaltado en rojo si > 0).

### Secciones por prioridad

Tres secciones diferenciadas visualmente:

#### 🔴 URGENTE (`region:section-urgent`)

Alertas que requieren acción **hoy**. Tipos:

- **Dolor reportado** — alumno marcó la flag de dolor en su reporte. Llevará la cita literal.
- **Lesión declarada** — alumno cambió su estado a "lesión" desde reajuste de día.
- **Ausencia prolongada** — > 7 días sin reportar nada y antes reportaba regularmente.
- **Sobrecarga peligrosa** — varios reportes consecutivos con RPE 1-2 ("agotamiento") + ritmo por encima del objetivo (regla simple en MVP).

#### 🟡 ATENCIÓN (`region:section-warn`)

Alertas para atender en los próximos 2-3 días:

- **Cumplimiento bajo en la semana** — alumno con ≤ 50% de sesiones reportadas como "Hecho" en la última semana.
- **Saltó N sesiones consecutivas** — patrón emergente.
- **RPE alto sostenido** — ≥ 3 sesiones consecutivas con RPE 4-5 ("muy duro") aunque sean "hechas".

#### 🟢 INFORMATIVO (`region:section-info`)

Alertas de baja prioridad, "para tu información":

- **Ritmo significativamente fuera del objetivo** — sesión reportada con notas que sugieran ritmo distinto al planificado.
- **Comentario / pregunta del alumno** — si activamos comentarios por sesión (SHOULD), aparece aquí.
- **Alumno nuevo en el grupo** — primera semana del alumno; útil para "estar pendiente".

Cada sección está colapsable (con conteo en el encabezado). Por defecto: urgente y atención expandidos, informativo colapsado.

### Card de alerta

Estructura común:

- **Icono** según tipo (🤕 dolor, ⚠ ausencia, 📉 cumplimiento, ⚡ sobrecarga, 💬 comentario).
- **Línea principal**: nombre del alumno + descripción corta de la alerta.
- **Metadata**: grupo del alumno, fecha relativa ("hace 2 horas"), datos relevantes.
- **Cita literal** si aplica (texto del reporte que originó la alerta).
- **CTAs**:
  - "Ver alumno" — abre side sheet con el detalle del alumno (historial reciente, plan, contacto).
  - "Responder" o "Enviar mensaje" — abre composer de comentario (SHOULD: por sesión; alternativa MVP: mensaje fuera de sistema, ej. mailto:).
  - "Descartar" — marca la alerta como atendida. Se va a "Descartadas" pero no desaparece para siempre.
- Color de fondo o borde según prioridad.

### `region:dismissed-link`

Link discreto al final de la página para ver las alertas descartadas recientemente. Útil para revisar tendencias y "deshacer".

## Acciones

| Acción | Resultado |
|---|---|
| Filtrar por grupo(s) | Filtra la lista. |
| Ver alumno (en card) | Abre side sheet con detalle. |
| Responder | Si SHOULD activado (comentarios por sesión): abre composer. Si no: opens `mailto:` del email del alumno con contexto pre-rellenado. |
| Descartar | Marca como atendida. Toast con "Deshacer" 5s. |
| Ver descartadas | Pantalla aparte con histórico. |
| Marcar varias y descartar en lote | Selección múltiple opcional (MVP nice-to-have). |
| Suscribir / silenciar tipo de alerta | (post-MVP) controla qué tipos genera el sistema. |

## Estados de la pantalla

1. **Sin alertas (caso ideal raro)** — empty state: "Todo en orden. Vuelve más tarde."
2. **Con alertas** — lo descrito.
3. **Solo informativos** — secciones urgente y atención vacías o colapsadas; aviso "Sin alertas que requieran acción hoy".
4. **Filtrado vacío** — empty state filtrado.
5. **Cargando** — skeleton de cards.
6. **Error de carga** — banner persistente "No pudimos cargar las alertas. Reintentar."

## Interacciones clave

### Interacción A — Responder a una lesión

1. Carlos (entrenador) entra por la mañana.
2. Ve "🤕 Marta Sánchez — dolor reportado".
3. Pulsa "Ver alumno" → side sheet con: histórico de la semana, plan publicado, reporte que generó la alerta (cita completa).
4. Decide: pulsa "Responder" → composer con la sesión como contexto.
5. Escribe: *"Mañana hacemos rodaje suave en lugar de la tirada. Avísame cómo sigues."*
6. Envía. El sistema:
   - Si SHOULD activo: añade el comentario a la sesión del miércoles, visible para Marta en su [vista hoy](06-student-today.md).
   - Si SHOULD no activo: abre cliente de email del entrenador con destinatario y cuerpo pre-rellenado.
7. La alerta queda automáticamente marcada como "atendida" (descartada) y se mueve al histórico.

### Interacción B — Descartar alerta sin acción

1. Carlos ve "📉 Ana López — saltó 2 sesiones esta semana".
2. Sabe que Ana está de viaje y vuelve el lunes; ya hablaron.
3. Pulsa "Descartar". Toast: "Alerta descartada. Deshacer".
4. La alerta desaparece de la vista activa.

### Interacción C — Profundizar en una alerta informativa

1. Carlos ve "⚡ Pedro Cordero — entrenó por encima del ritmo objetivo".
2. Pulsa "Ver alumno" → side sheet con plan del alumno y reporte.
3. Lee la nota completa: *"Fui a tope porque me sentía bien, prefiero los próximos a 3:25 si me dejas"*.
4. Decide responder agregar nota o ajustar plan. Cierra y vuelve.

### Interacción D — Vista filtrada por grupo

1. Carlos lleva 2 grupos: "Maratón Valencia avanzado" y "Trail finde".
2. Quiere centrarse solo en el primero esta mañana.
3. Selecciona en el filtro "Mis grupos" → solo "Maratón Valencia avanzado".
4. La lista se filtra.

## Reglas de generación de alertas (para el diseñador, para entender qué se muestra)

Estas reglas son **configurables a futuro** pero el MVP las trae por defecto:

| Tipo | Regla |
|---|---|
| Dolor reportado | Cualquier reporte con flag de dolor marcada. |
| Lesión declarada | Cambio del tag `estado` a "lesión" por el propio alumno. |
| Ausencia prolongada | 7 días sin reportar nada cuando antes reportaba al menos 2/sem. |
| Cumplimiento bajo | < 50% de sesiones reportadas como "Hecho" en los últimos 7 días. |
| Saltó N consecutivas | 2+ sesiones marcadas como "saltada" en los últimos 5 días. |
| RPE alto sostenido | 3+ sesiones consecutivas con RPE 4-5. |
| Ritmo fuera de objetivo | Nota libre del reporte contiene palabras como "por encima", "más rápido", "más lento"; o adjunto FIT/GPX con desviación > 10% (post-MVP). |
| Comentario del alumno | Cualquier comentario nuevo en una sesión (si SHOULD activo). |
| Alumno nuevo | Primera semana desde alta del alumno en el grupo. |

## Validaciones y errores

- No mostrar alertas duplicadas (mismo alumno + mismo tipo + mismo día → consolidar).
- Cuando se atiende una alerta, no volver a generarla por el mismo evento.
- Si el alumno está en estado "descanso" o "post-parto", no generar alertas de ausencia.

## Responsive (móvil)

- La pantalla es útil en móvil también (entrenador revisa por la mañana antes de empezar el día).
- Cards apiladas, secciones con colapso.
- Side sheet del alumno ocupa pantalla completa en móvil.

## Opciones de diseño a explorar

### Layout — Opción A (recomendada): lista por prioridad

Lo descrito. Secciones Urgente / Atención / Informativo, cada una con cards.

**Pros**: el entrenador sabe qué hacer primero. Replica el patrón de "bandeja de entrada" mental.
**Contras**: si hay muchas alertas, scroll largo.

### Layout — Opción B: tabla con columna prioridad

Tabla densa con columnas: Prioridad · Alumno · Tipo · Grupo · Acciones. Filtrable y ordenable.

**Pros**: muchas alertas en poco espacio. Buen para entrenadores power.
**Contras**: menos visual, menos cálido. Pierde la sensación de "esto es importante".

### Layout — Opción C: dashboard con widgets

Sección superior con KPIs (total alertas, urgentes, alumnos sin reportar) + sección inferior con lista. Más visual.

**Pros**: visión general + detalle.
**Contras**: añade complejidad. KPIs pueden ser ruido.

**Recomendación**: diseñar **A y B** para validar con RG (volumen alto: 500 alumnos, 50 alertas activas posibles) y VG (volumen menor). A puede ser ideal para volumen medio; B para volumen alto.

### Acciones por card — Opción A (recomendada): 3 CTAs visibles

Lo descrito. Ver alumno + Responder/Mensaje + Descartar.

### Acciones por card — Opción B: 1 CTA primario + menú [···]

Solo "Ver alumno" visible; el resto en menú.

**Pros**: cards más limpias.
**Contras**: descartar (acción frecuente) requiere 2 clicks.

**Recomendación**: **A**. Las 3 acciones son las más frecuentes.

### Cita literal del alumno — Opción A (recomendada): visible inline

Como en el ejemplo. La cita aparece debajo de la línea principal.

### Cita literal del alumno — Opción B: tooltip al hover

Más compacta, pero menos escaneable.

**Recomendación**: **A**. La cita es el contexto que hace la alerta accionable.

### Pestañas Hoy / Semana / Histórico — Opción A: pestañas

Tres pestañas en `region:page-header` para cambiar el alcance temporal.

### Pestañas Hoy / Semana / Histórico — Opción B: filtro en el header

Como otros filtros, "Periodo: Hoy ▾".

**Pros de A**: visible siempre, fácil de cambiar.
**Pros de B**: más limpio.

**Recomendación**: B en MVP. Mantenerlo simple. Si la beta muestra que el entrenador cambia mucho de período, pasar a A.

## Criterios de validación con usuario

- ✅ El entrenador con volumen (RG) entra a la pantalla y en < 60s sabe a quién tiene que atender hoy.
- ✅ Distingue las 3 prioridades sin pensar.
- ✅ Entiende qué disparó cada alerta (sin tener que leer documentación).
- ✅ Responde / descarta sin titubear.
- ✅ Después de descartar, no aparece la misma alerta hasta que ocurra un nuevo evento.
- ❌ Si "demasiado ruido" (muchas alertas informativas) → considerar colapsar la sección por defecto, o silenciar tipos.
- ❌ Si "se pierde lo importante" → revisar reglas y prioridades.
- ❌ Si "no entiende por qué saltó la alerta" → mejorar mensaje de la card con datos explícitos.
