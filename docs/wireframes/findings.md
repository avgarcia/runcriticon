# Hallazgos — validación de wireframes

> Síntesis cruzada de la ronda de validación de wireframes (2026-05-19 y 2026-05-20).
>
> **Muestra**: 5 sesiones — 2 entrenadores/admin ([RG](validation/RG-validation.md), [VG](validation/VG-validation.md)) y 3 alumnos ([AVG](validation/AVG-validation.md) medio-alto, [PM](validation/PM-validation.md) medio-alto, [AM](validation/AM-validation.md) novata). Plan del ejercicio en [`validation.md`](validation.md).

## Resultado global

**Las 9 pantallas pasan su regla de decisión. Los 4 tiempos críticos pasan.** Es un resultado fuerte: los wireframes lo-fi están validados para pasar a sistema visual y prototipo. Los cambios pedidos son refinamientos, no rediseños.

### Tiempos cronometrados

| Tarea | Objetivo | RG | VG | AVG | PM | AM |
|---|---|---|---|---|---|---|
| 04 — Crear grupo de 2 condiciones | < 2 min | 1:38 ✅ | 1:15 ✅ | — | — | — |
| 05 — Construir semana (2º intento) | < 10 min | 6:12 ✅ | 4:45 ✅ | — | — | — |
| 06 — Saber qué entrena hoy | < 5 s | — | — | 2,8 s ✅ | 3 s ✅ | 4 s ✅ |
| 07 — Reportar "Hecho" | < 15 s | — | — | 7 s ✅ | 8 s ✅ | 12 s ✅ |

> Dato relevante: la pantalla 05 en **primer** intento (sin "copiar semana anterior") tardó 14:05 (RG) y 11:20 (VG) — por encima del objetivo. En **segundo** intento, con el atajo, baja a 6:12 y 4:45. Confirma que "copiar semana anterior" no es un extra: es lo que hace la pantalla viable.

### Veredicto por pantalla

| Pantalla | RG | VG | Alumnos | Veredicto |
|---|---|---|---|---|
| 01 Onboarding | pasa | pasa | — | ✅ |
| 02 Tag editor | pasa | pasa | — | ✅ |
| 03 Gestión alumnos | pasa | pasa | — | ✅ |
| 04 Constructor de grupos | pasa | pasa | — | ✅ |
| 05 Editor plan semanal | pasa | pasa | — | ✅ |
| 06 Vista "hoy" | — | — | pasa ×3 | ✅ |
| 07 Reporte + reajuste | — | — | pasa ×3 | ✅ |
| 08 Panel de alertas | pasa | pasa | — | ✅ |
| 09 Salud del club | pasa | pasa | — | ✅ |

## Estado de los riesgos

| Riesgo | Estado tras validación |
|---|---|
| **R17** — el admin se atasca al inicio | **Mitigado.** RG y VG completaron onboarding + editor de tags sin atascarse. RG: *"vas al grano"*. La pre-carga de tags y carreras populares funcionó. Único ajuste: botón "configurar más tarde" en el paso de grupos. |
| **R18** — el constructor de grupos es demasiado técnico | **Mitigado.** Ambos crearon un grupo de 2 condiciones en < 2 min, ninguno preguntó por sintaxis ("AND"), ambos lo elogiaron. VG: *"tenía pánico a que fuera como programar una base de datos"*. Único ajuste: comunicar que el filtro es reactivo (buscaron un botón "Aplicar"). **No hace falta diseñar las variantes B/C.** |
| **R2** — el modelo plan-por-grupo no encaja | **Mitigado.** VG: *"mi gran miedo era escribir el mismo plan 40 veces… me habéis solucionado la vida"*. "Copiar semana" + personalización en modal resuelven el dolor. |

## Hallazgos transversales

### H-V1 — Barrera del lenguaje técnico: novatos vs. avanzados (nuevo)

No estaba en el discovery. Los alumnos avanzados (AVG, PM) devoran ritmos, series y recuperaciones. La alumna novata (AM) sufre la jerga: *"a mí me pones 'Fartlek' o 'RPE 6' y me suena a chino"*. La RPE numérica le bloquea. La card "hoy" y el reporte deberían **adaptar el lenguaje según el tag `nivel`** del alumno (descripción simple tiempo/acción para iniciación, métricas para avanzados). Es el hallazgo más accionable de la ronda de alumnos.

### H-V2 — El sistema reactivo necesita comunicarse mejor

RG y VG buscaron un botón "Buscar / Aplicar / Guardar condición" en el constructor de grupos antes de entender que la vista previa se actualiza sola. El patrón reactivo (sin botón guardar) reduce carga cognitiva una vez entendido, pero el primer contacto genera un micro-bloqueo. Aplica al constructor de grupos y, potencialmente, a cualquier pantalla con filtros en vivo.

### H-V3 — Miedo al castigo visual (alumnos)

PM y AM coinciden: odian ver el calendario lleno de cruces rojas / estados "No hecho" por motivos de fuerza mayor (trabajo, viajes). El reajuste de día (M18) reduce esa ansiedad y mantiene el engagement. Implicación de copy: "No hecho" suena punitivo — PM propone "No he podido entrenar".

### H-V4 — Gestión por excepción confirmada como el corazón del producto

RG: *"esta pantalla es el corazón de mi día a día"* (panel de alertas). VG: *"para un club de 500 personas, esto es el corazón del sistema"*. La cita literal del alumno junto a la alerta fue muy valorada por ambos. Confirma P2 del discovery con fuerza.

## Lista priorizada de cambios a aplicar a los wireframes

### Prioridad ALTA

| # | Pantalla | Cambio | Origen |
|---|---|---|---|
| A1 | 04 Constructor | Indicador visual (loader sutil / texto) de que la vista previa es reactiva y se actualiza al instante. | RG (Alta), VG (Media) — H-V2 |
| A2 | 07 Reporte | Etiquetas textuales bajo la escala RPE (ej. "Muy suave" · "Moderado, puedo hablar" · "Esfuerzo máximo"). | AM (Alta) — H-V1 |
| A3 | 08 Alertas | Nueva regla de alerta: acumulación de 3 sesiones "Parciales" consecutivas (no solo "No hecho"). | VG (Alta) |

### Prioridad MEDIA

| # | Pantalla | Cambio | Origen |
|---|---|---|---|
| M1 | 01 Onboarding | Botón explícito "Configurar más tarde / Omitir" en el paso de grupos (y poder saltar entrenadores si no hay altas). | RG, VG |
| M2 | 06 Vista hoy | Para alumnos con tag `nivel: iniciación`, priorizar la descripción simple (tiempo/acción) sobre los ritmos. | AM — H-V1 |
| M3 | 06 Vista hoy | Acceso al plan de los próximos días sin cambiar de pestaña. | PM |
| M4 | 07 Reporte | Botón "Tengo molestias / dolor" más grande y visible dentro del flujo. | PM |
| M5 | 08 Alertas | Alerta de posible sobreentrenamiento: caída de ritmos significativa durante 3 sesiones. | RG |
| M6 | 09 Salud del club | KPI "Alumnos durmientes" (>21 días sin actividad), para retención proactiva. | VG |

### Prioridad BAJA

| # | Pantalla | Cambio | Origen |
|---|---|---|---|
| B1 | 03 Gestión alumnos | Feedback visual más evidente al activar las bulk actions. | RG |
| B2 | 02 Tag editor | "Duplicar tag" heredando tipo y metadatos (para ediciones anuales de carreras). | VG |
| B3 | 06 Vista hoy | Día "hoy" más marcado en el week-strip; icono de tipo de sesión más grande. | AVG |
| B4 | 07 Reporte | Microtexto explicando "Parcial"; copy de "No hecho" → "No he podido entrenar". | AVG, PM — H-V3 |
| B5 | 07 Reporte | Refuerzo positivo sutil (animación) al marcar "Hecho". | AM |

## Funcionalidades nuevas pedidas (candidatas a backlog)

Ninguna es bloqueante del MVP. Registradas para roadmap:

- **Autodetección de metadata de carreras populares** al escribir el nombre oficial (RG).
- **Glosario / tooltips de términos técnicos** para grupos de iniciación (AM) — relacionado con H-V1.
- **Campo de zapatillas/material** por sesión, para control de desgaste (PM).
- **Columnas adicionales en gestión de alumnos**: años de experiencia (RG), teléfono de emergencia / estado de pago de cuota (VG).
- **Importación Strava/Garmin** — confirmada como deseada por AVG y PM; ya está en SHOULD del backlog.

## Decisión y próximos pasos

1. **No** se diseñan variantes B/C de ninguna pantalla. La Opción A superó la validación en todas. Se ahorra esa iteración.
2. Se aplican los cambios **ALTA** a los wireframes ya (A1, A2, A3) — son baratos y validados por varios participantes.
3. Los cambios **MEDIA** y **BAJA** se incorporan al brief del diseñador para la fase de alta fidelidad (no requieren revalidar wireframes lo-fi).
4. Las funcionalidades nuevas se registran en `backlog.md` (SHOULD/COULD), sin tocar las 19 MUST.
5. **Vía libre para la siguiente fase**: sistema visual + prototipo navegable + ADR de stack técnico + plan de implementación del MVP.
6. RG y VG confirman beta: VG migra 2 grupos de maratón (~60 alumnos) en beta cerrada; RG lidera la migración del sector de fondo avanzado. AVG, PM y AM se ofrecen como alumnos beta.

## Ronda 2 — validación informal de los cambios posteriores (2026-05-27)

Los cambios introducidos tras la ronda 1 se han hablado **informalmente** con RG y VG (sin sesión formal con mockups). No se hizo guion ni se cronometraron tareas; el feedback emergió de conversaciones de trabajo. Se registra aquí para trazabilidad del equipo que arrancará H1.

### Cambios consolidados

| Cambio | Origen del feedback | Estado |
|---|---|---|
| **Personalización (M12)** como entidad de primera y con *mensaje opcional al alumno* — sin indicador "Personalizada para ti" en la vista hoy | RG, VG | Consolidado. La decisión "el alumno no ve indicador, solo el mensaje si lo hay" sale de la conversación: querían poder ajustar sin tener que dar explicaciones a todo el grupo. |
| **Ritmos relativos como delta sobre marca** (M19) — *"10K + 10 s/km"*, *"42K − 5 s/km"* — y **no** como porcentajes | RG, VG | Consolidado. RG: *"yo lo escribo así, no en porcentajes. Lo de los porcentajes lo veo en planes americanos pero aquí nadie lo usa."* Cambia el modelo de `Ritmo` en ADR-0002. |
| **Marcas privadas del alumno** (M20) — solo el alumno las ve, ni el entrenador ni el admin tienen acceso | VG | Consolidado. VG: *"no quiero que lo conozca, sería darle más trabajo. Que lo gestione el alumno."* Implica que la entidad `MarcaAlumno` vive en el módulo Seguimiento y no se filtra. |
| **Onboarding del alumno** — primera marca al activar la cuenta, con orden 10K → 5K → 21K → 42K | Inferido del flujo + sentido común | Consolidado sin contradicciones. Se valida en la beta H1. |
| **Umbral del corredor** queda **fuera del MVP** | RG | Consolidado. RG: *"el umbral es importante pero la mayoría de mis alumnos no lo conoce con precisión. Con las marcas ya basta."* Se relega a COULD como zonas. |

### Lo que NO se ha podido validar en esta ronda

- Reacción del **alumno** ante la pantalla "Mis marcas" con el banner verde de privacidad (V2 y V5 de la ronda formal). Se asume que el énfasis en *"solo tú las ves"* es suficiente; se confirmará en la beta H1 con AVG, PM y AM.
- Tasa real de **uso de personalización** por parte del entrenador (V4). Es la palanca que justifica el modelo plan-por-grupo; si baja del 10% de sesiones, M12 será revisable.
- Encuentro espontáneo del **CTA in-context** *"Añade tu marca de 10K para ver tu ritmo"* desde la vista "hoy" (V3). Se asume descubrible; se observará en la beta.

### Decisión

- **No** se programan sesiones formales para validar M12, M19, M20. La beta H1 hace de validación real.
- Las hipótesis V2-V5 quedan **abiertas** y se revisan en la primera demo quincenal con el piloto.
- Si alguna hipótesis se rompe en la beta, los cambios necesarios son **acotados**: el modelo de `Ritmo` ya está preparado para añadir zonas (COULD), y la decisión de privacidad se podría flexibilizar sin migración de datos.

---

> **Nota de calidad del dato**: el archivo de la sesión de la alumna novata se entregó como `AN-validation.md` pero el participante es **AM**. Renombrado a `AM-validation.md` para coherencia.
