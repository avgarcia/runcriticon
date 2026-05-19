# 09 — Vista de salud del club (admin)

> Dashboard del admin del club. Visión agregada de qué está pasando en el club: actividad, grupos huérfanos, micro-grupos sugeridos de fusión, alumnos inactivos. **Es lo que justifica que el CLUB adopte la herramienta institucionalmente** (no solo entrenadores individuales).

## Contexto

- **Rol**: admin del club.
- **Cuándo se accede**: idealmente 1-2 veces por semana de revisión + cuando hay una decisión (reorganizar grupos, hablar con un entrenador).
- **Frecuencia**: media. No es pantalla diaria, pero sí semanal.
- **MUSTs cubiertos**: M16 (vista de salud del club, ampliada con M9b — fusión de micro-grupos).
- **Decisión estratégica**: si el admin no ve valor agregado aquí, el club no adopta la herramienta institucionalmente y queda en uso de entrenadores sueltos.

## Objetivo del usuario

> "En un vistazo: ¿está mi club entrenando? ¿qué grupos van bien y cuáles no? ¿hay algo que me esté faltando?"

## Inputs

- Datos agregados: alumnos, grupos, entrenadores, sesiones publicadas, reportes de la última semana/mes.
- Sugerencias de fusión generadas por M9b.

## Layout principal — dashboard

Layout estándar de admin. La pantalla de inicio del admin (cuando termina el onboarding, cae aquí).

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Salud del club — Club Atletismo XYZ                Periodo: [Esta semana ▾]  │ region:page-header
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────┬──────────────┬──────────────┬──────────────────────────┐   │
│ │ 78 alumnos   │ 12 grupos    │ 5 entrenado- │ 67% cumplimiento medio   │   │ region:kpis
│ │ activos      │ activos      │ res          │ esta semana              │   │
│ │ (+2 vs sem   │ (3 sin       │              │ (vs 71% sem anterior)    │   │
│ │  anterior)   │  entrenador) │              │                          │   │
│ └──────────────┴──────────────┴──────────────┴──────────────────────────┘   │
│                                                                              │
│ ⚠ Necesitan tu atención (3)                                                  │ region:attention
│ ─────────────────────────────                                                │
│  • 3 grupos sin entrenador asignado    [Ver grupos]                          │
│  • 2 sugerencias de fusión pendientes  [Ver sugerencias]                     │
│  • 8 alumnos sin reportar > 14 días    [Ver alumnos]                         │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│ Actividad por grupo                                                          │ region:groups-table
│ ─────────────────────                                                        │
│ Grupo                              Entrenador   Alumnos  Reporta. Cumpli.   │
│ ──────────────────────────────────────────────────────────────────────────  │
│ Maratón Valencia avanzado          Carlos         12       11      83%      │
│ Maratón Valencia medio             Carlos          5        5      90% ★    │
│ Iniciación CACO                    Ana             8        6      67%      │
│ Trail finde                        (sin asign.)    6        2      33% ⚠   │
│ Los del martes                     Carlos          4        4      75%      │
│ Maratón Valencia iniciación        (sin asign.)    1        0       0% ⚠   │
│ …                                                                            │
│                                                                              │
│                                                            [Ver todos los grupos] │
├──────────────────────────────────────────────────────────────────────────────┤
│ Tendencia de actividad (últimas 4 semanas)                                   │ region:trend-chart
│ ─────────────────────────────                                                │
│ [Gráfico de líneas: sesiones reportadas / publicadas, por semana]            │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:page-header`

- Título "Salud del club — [nombre del club]".
- Selector de período: "Esta semana / Este mes / Últimas 4 semanas". Por defecto: esta semana.

### `region:kpis`

4 KPIs en cards horizontales. Cada uno con:

- **Número grande** (la métrica).
- **Etiqueta** debajo.
- **Comparativa** (vs periodo anterior si aplica). Símbolo + verde o − rojo.
- (Opcional) sparkline mini al lado del número.

KPIs MVP:

| KPI | Cálculo |
|---|---|
| Alumnos activos | Alumnos con al menos 1 reporte (hecho/parcial/no hecho) en el periodo. |
| Grupos activos | Grupos con al menos 1 sesión publicada en el periodo. |
| Entrenadores | Total de entrenadores asignados a algún grupo (no inactivos). |
| Cumplimiento medio | % de sesiones reportadas como "Hecho" sobre sesiones publicadas, agregado. |

### `region:attention`

Bloque con lo que **requiere acción del admin**, no de un entrenador. Tipos:

- **Grupos sin entrenador asignado** — número + CTA "Ver grupos" que lleva a la lista filtrada.
- **Sugerencias de fusión** (M9b) — número + CTA "Ver sugerencias" que abre modal o pantalla aparte con cada sugerencia.
- **Alumnos sin reportar prolongadamente** — número + CTA "Ver alumnos".
- **Carreras del catálogo a punto de caducar / ya caducadas** — si las hay, alerta para archivar.
- **Entrenadores invitados sin aceptar > 7 días** — si los hay, opción de reenviar invitación.

Cada item es una línea con icono + texto + CTA. Si no hay nada que atender, mostrar mensaje positivo: "Todo en orden esta semana 🎉".

### `region:groups-table`

Tabla con un grupo por fila. Columnas:

| Columna | Contenido |
|---|---|
| Grupo | Nombre. Clic abre [spec 04](04-group-builder.md) en modo edición. |
| Entrenador | Nombre del entrenador (o "sin asignar" en rojo). |
| Alumnos | Nº de alumnos en el grupo. |
| Reportando | Nº de alumnos del grupo que han reportado al menos 1 sesión en el periodo. |
| Cumplimiento | % de sesiones reportadas como "Hecho" en el periodo. Indicador visual: ★ si > 80%, ⚠ si < 50%, sin marca en medio. |

Comportamiento:

- Ordenable por cualquier columna.
- Resaltado de filas con problemas (sin entrenador, cumplimiento bajo).
- Limitada a 10 filas; "Ver todos los grupos" abre la pantalla completa (que es la lista de grupos del [spec 04](04-group-builder.md)).

### `region:trend-chart`

Gráfico simple de líneas con el periodo seleccionado.

Líneas:

- Sesiones publicadas (por semana).
- Sesiones reportadas como "Hecho" (por semana).
- Sesiones reportadas como "Parcial" o "No hecho" (línea fina, color apagado).

Eje X: semanas. Eje Y: número de sesiones.

Tooltip al hover: detalle por semana.

> En MVP, gráfico simple. Sin filtros adicionales. Si el club piloto pide más, se amplía.

## Acciones

| Acción | Resultado |
|---|---|
| Cambiar periodo | Recarga todos los datos con el nuevo periodo. |
| Click en KPI | (Opcional MVP) abre detalle. En MVP, no clickables. |
| Click en item de atención | Lleva a la pantalla/vista correspondiente. |
| Click en grupo de la tabla | Abre editor del grupo. |
| Ordenar columnas | Reordena la tabla. |
| Ver todos los grupos | Pantalla completa de gestión de grupos. |
| Hover en línea del gráfico | Tooltip con detalle. |

## Estados de la pantalla

1. **Club activo con datos** — lo descrito.
2. **Club recién creado (primer uso)** — empty state: "Aún no hay datos. Cuando los entrenadores publiquen planes y los alumnos reporten, verás aquí el resumen."
3. **Club inactivo (semanas sin actividad)** — banner amarillo: "Esta semana no hay actividad. Habla con tus entrenadores."
4. **Sin grupos** — empty state que redirige a crear grupos.
5. **Cargando** — skeletons por sección.
6. **Error de carga** — banner persistente.

## Interacciones clave

### Interacción A — Revisión semanal del admin

1. Admin entra el lunes por la mañana.
2. Ve KPIs: 78 alumnos activos, +2 vs semana anterior — bien.
3. Sección "Necesitan tu atención": 3 grupos sin entrenador, 2 sugerencias de fusión.
4. Pulsa "Ver grupos sin entrenador" → spec 04 filtrado.
5. Asigna entrenadores.
6. Vuelve al dashboard, ahora "0 grupos sin entrenador".

### Interacción B — Atender sugerencia de fusión

1. Admin ve "2 sugerencias de fusión pendientes".
2. Pulsa "Ver sugerencias" → modal o pantalla con cada sugerencia (mismo modal que en [spec 04](04-group-builder.md) interacción C).
3. Decide aplicar la fusión, cancelar o ignorar.

### Interacción C — Identificar grupo con baja actividad

1. Admin ve en la tabla "Trail finde — 33% cumplimiento ⚠".
2. Clic en la fila → editor del grupo.
3. Investiga: ¿el entrenador está ausente? ¿los alumnos están desmotivados?
4. Acción: habla con el entrenador, ajusta el grupo, o reasigna alumnos.

### Interacción D — Cambiar a vista mensual

1. Admin quiere ver tendencia de marzo.
2. Cambia el periodo a "Este mes".
3. KPIs, tabla y gráfico se recalculan.

## Validaciones y errores

- Si un grupo tiene 0 alumnos, mostrar "0%" en cumplimiento (no error).
- Si un grupo tiene 0 sesiones publicadas en el periodo, marcar "Sin actividad" en lugar de % engañoso.
- Las sugerencias de fusión solo aparecen si hay micro-grupos o solapamiento > 80% real.

## Responsive (móvil)

- KPIs en columna vertical (uno por fila) en móvil.
- Tabla de grupos pasa a lista de cards.
- Gráfico de tendencia se simplifica (menos detalle en eje X).
- Sección "Necesitan tu atención" sigue siendo prominente.

## Opciones de diseño a explorar

### KPIs — Opción A (recomendada): 4 cards horizontales con comparativa

Lo descrito. Cards visibles desde arriba.

### KPIs — Opción B: KPIs en lista vertical con sparklines más grandes

Más visual del trend, menos densidad superior.

**Pros de A**: vista de conjunto rápida. Buena para "echar un vistazo".
**Pros de B**: trend más legible.

**Recomendación**: **A** para escritorio (estándar dashboard); **B** opcional en móvil.

### Bloque de atención — Opción A (recomendada): lista compacta

Lo descrito. Una línea por tipo.

### Bloque de atención — Opción B: cards individuales con preview

Cada item con su propia card y preview de los elementos afectados.

**Pros de A**: escaneable, compacto.
**Pros de B**: contexto inmediato.

**Recomendación**: **A**. Validar y, si el admin no encuentra qué hacer, cambiar a B.

### Tabla de grupos — Opción A (recomendada): tabla tradicional ordenable

Lo descrito.

### Tabla de grupos — Opción B: cards con barras de progreso visuales

Cada grupo en una card con barra horizontal de cumplimiento.

**Pros de A**: comparativa entre grupos.
**Pros de B**: más visual, atractivo.

**Recomendación**: validar con el admin. Si tiene > 15 grupos, A gana por densidad.

### Gráfico de tendencia — Opción A (recomendada): gráfico de líneas simple

Lo descrito.

### Gráfico de tendencia — Opción B: gráfico de barras apiladas (publicadas / hechas / no hechas)

Más explícito sobre qué se cumple y qué no.

**Pros de B**: menos confusión sobre qué muestra cada línea.
**Pros de A**: más limpio, fácil de leer rápido.

**Recomendación**: validar con el admin del club piloto.

### Acciones por grupo en la tabla — Opción A (recomendada): clic en fila = editar

Lo descrito.

### Acciones por grupo en la tabla — Opción B: columna de acciones [···]

Menú por fila con: ver detalle, asignar entrenador, fusionar...

**Pros de A**: simple.
**Pros de B**: más control sin abrir editor.

**Recomendación**: **A** + alguna acción rápida en hover.

## Criterios de validación con usuario

- ✅ El admin (RG o VG), sin haber visto la pantalla, identifica en < 30s el estado general del club.
- ✅ Sabe en < 60s qué acciones tiene pendientes esta semana.
- ✅ Encuentra el grupo con peor cumplimiento sin titubear.
- ✅ Cambia el periodo y entiende que los datos se recalculan.
- ✅ Acepta o rechaza una sugerencia de fusión sin confusión.
- ❌ Si no entiende el cálculo de cumplimiento → cambiar la métrica o añadir tooltip explicativo.
- ❌ Si los KPIs no le aportan → revisar cuáles incluimos (RG dirá si necesita otros: alumnos lesionados, alumnos con dolor reportado, etc.).
- ❌ Si nunca vuelve a la pantalla → el dashboard no aporta valor o la frecuencia esperada es errónea.
