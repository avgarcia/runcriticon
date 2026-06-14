# 03 — Gestión de alumnos (lista, alta individual, alta masiva CSV, edición de tags)

> Pantalla y flujos donde el admin da de alta a sus alumnos, los etiqueta y los gestiona. Es donde más fricción real hay (alta masiva de 50-500 socios). Si el CSV import falla, el admin abandona.

## Contexto

- **Rol**: admin del club.
- **Cuándo se accede**:
  - **Embebida** en el paso 5 del [onboarding](01-admin-onboarding.md) (en formato bulk-first).
  - **Standalone** desde nav lateral > "Alumnos".
- **Frecuencia**: alta inicial masiva + edición continua a lo largo del año (altas, bajas, cambios de tags).
- **MUSTs cubiertos**: M3 (alta de alumno), M5 (asignar tags), M9 (editar tags).
- **Riesgo principal**: que el alta masiva sea coñazo y el admin lo abandone.

## Objetivo del usuario

> "Tener a mis 80 socios dados de alta y bien etiquetados, sin pasarme la tarde uno a uno."

## Inputs

- Taxonomía del club ya definida (spec 02). Si está vacía, mostrar empty state que redirige a spec 02.
- Lista de entrenadores del club (para futura asignación, no obligatoria aquí).

## Layout principal — lista de alumnos

Layout estándar de admin. La lista es el primer punto de entrada.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ Alumnos                                  [Importar CSV]  [+ Nuevo alumno]      │ region:page-header
├────────────────────────────────────────────────────────────────────────────────┤
│ [🔍 Buscar por nombre o email]   Filtros: [nivel ▾] [objetivo ▾] [estado ▾]    │ region:toolbar
│                                                                                │
│ 47 alumnos · 3 pendientes de aceptar · 2 en estado "lesión"                    │ region:summary
├────────────────────────────────────────────────────────────────────────────────┤
│ ☐ Nombre              Email             Tags                       Estado      │ region:table-header
├────────────────────────────────────────────────────────────────────────────────┤
│ ☐ Marta Sánchez       marta@…           [medio] [10k] [MMM 26] +2  Activa      │
│ ☐ Pedro Cordero       pcor@…            [alto] [maratón] +3        Activa  ★   │
│ ☐ Juan Mira           jm@…              [medio] [10k]              Pendiente   │
│ ☐ Luis Sastre         ls@…              [iniciación]               Activa      │
│ …                                                                              │
│                                                                  region:table   │
├────────────────────────────────────────────────────────────────────────────────┤
│ Acciones en lote (3 seleccionados):                                            │
│ [Asignar tag] [Quitar tag] [Cambiar estado] [Eliminar]                         │ region:bulk-actions
└────────────────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:page-header`

- Título "Alumnos".
- Botón secundario "Importar CSV (cta:csv-import)".
- Botón primario "+ Nuevo alumno (cta:new-student)".

### `region:toolbar`

- Campo de búsqueda con icono lupa. Busca en nombre y email. Búsqueda incremental (debounce 200ms).
- Filtros: cada tag del club aparece como desplegable. El admin elige uno o varios valores; los alumnos mostrados son los que cumplen TODOS los filtros activos (AND).
- Chips de filtros activos debajo del toolbar, descartables individualmente.
- Reset de filtros: "Limpiar filtros" si hay alguno activo.

### `region:summary`

Frase de una línea con datos agregados:

- Total de alumnos.
- Pendientes de aceptar invitación (si los hay).
- Cuántos están en estado "lesión" u otro estado relevante (configurable: mostrar siempre el que aparezca con > 0 alumnos en ese instante).

### `region:table`

Tabla con columnas:

| Columna | Contenido |
|---|---|
| Checkbox | Selección múltiple. |
| Nombre | Nombre del alumno. Clic abre el side sheet de detalle (ver más abajo). |
| Email | Email del alumno. |
| Tags | **Chips de los tags asignados.** Solo se muestran los 2-3 más relevantes (configurable: nivel y objetivo por defecto). El resto se indica como "+N" clickable que abre tooltip con todos. |
| Estado | Activo / Pendiente / Inactivo / Lesión / etc. (basado en el tag `estado` u otro tag marcado como "estado principal" en la taxonomía). |

Comportamiento:

- Ordenación clickando cabecera (nombre, email, estado).
- Paginación o scroll infinito (preferible scroll infinito si la performance lo admite).
- Hover sobre fila muestra acciones rápidas a la derecha: editar (lápiz), eliminar (basura).
- Indicador ★ sutil junto al nombre si el alumno tiene **alguna alerta** asociada (lesión, sin reportar > 7d, etc.) — vincula con [panel de alertas](08-coach-alerts.md).

### `region:bulk-actions`

Barra que aparece sticky abajo cuando hay ≥ 1 alumno seleccionado. Contiene:

- Texto "X seleccionados".
- Botones: "Asignar tag", "Quitar tag", "Cambiar estado", "Eliminar".
- Cada uno abre modal/side sheet con el flujo correspondiente.

## Flujo A — Alta individual de un alumno

Trigger: CTA "+ Nuevo alumno" del header.

Abre un **side sheet** lateral (no modal). Razón: el admin probablemente quiere dar de alta varios seguidos y el side sheet permite ver la lista al fondo.

### Layout del side sheet

```
┌──────────────────────────────────────┐
│ Nuevo alumno                    [×]  │
├──────────────────────────────────────┤
│ Nombre*           [                ] │
│ Email*            [                ] │
│                                      │
│ Tags                                 │
│ ─────                                │
│ nivel             [ Selecciona... ▾] │
│ distancia         [ Selecciona... ▾] │
│ objetivo          [ Selecciona... ▾] │
│ terreno           [ Selecciona... ▾] │
│ estado            [ activo (preset)▾]│
│ (+ tags adicionales según taxonomía) │
│                                      │
│ □ Crear otro al guardar              │
├──────────────────────────────────────┤
│              [Cancelar]    [Crear]   │
└──────────────────────────────────────┘
```

### Componentes del side sheet

- Nombre y email: obligatorios, validación inline.
- Tags: aparecen TODOS los tags activos del club como desplegables. El admin no está obligado a rellenar todos (puede dejar el alumno con pocos tags y completar más tarde).
- Si el tag permite múltiples valores (ej. `objetivo`), el desplegable es multiselect.
- Checkbox "Crear otro al guardar": si está activado, tras guardar se vacía el formulario y queda listo para otro alumno (caso real: dar de alta varios sin volver a la lista).
- Botones: Cancelar (cierra sin guardar, con confirmación si hay cambios) / Crear (cta:create-student).

### Resultado

- Toast verde: "Alumno creado. Hemos enviado invitación por email."
- Si "Crear otro" estaba activo → formulario vacío.
- Si no → side sheet se cierra, foco vuelve a la tabla, fila nueva resaltada brevemente.

## Flujo B — Edición de un alumno existente

Trigger: clic en la fila del alumno O lápiz en hover.

Abre el mismo side sheet con los datos del alumno. Diferencias:

- Cabecera muestra el nombre del alumno.
- Botón inferior cambia a "Guardar cambios (cta:save-student)".
- Aparece sección extra al final: **Acciones de cuenta**:
  - "Reenviar invitación" (si está pendiente).
  - "Desactivar alumno" (no borra; los datos históricos se mantienen, pero deja de aparecer en grupos activos).
  - "Eliminar alumno" (destructivo, requiere confirmación con escribir el nombre del alumno).

## Flujo C — Importación masiva CSV

Trigger: CTA "Importar CSV" del header (o seleccionado en el paso 5 del onboarding).

Pantalla **a página completa** (no side sheet) porque el flujo tiene varios pasos.

### Paso C.1 — Descargar plantilla

```
┌────────────────────────────────────────────────────────────┐
│ Importar alumnos desde CSV                       [Cancelar]│
├────────────────────────────────────────────────────────────┤
│ Paso 1 de 4: Plantilla                                     │
│                                                            │
│ Te recomendamos descargar la plantilla con las columnas    │
│ correctas según la taxonomía de tu club:                   │
│                                                            │
│      [⬇ Descargar plantilla.csv]                           │
│                                                            │
│ La plantilla incluye una columna por cada tag activo:      │
│ nombre, email, nivel, distancia, objetivo, terreno, estado │
│                                                            │
│ Tip: si un alumno tiene varios valores en un tag (ej. dos  │
│ carreras objetivo), sepáralos con punto y coma: MMM;MV.    │
│                                                            │
│                                       [Ya tengo CSV →]     │
└────────────────────────────────────────────────────────────┘
```

### Paso C.2 — Subir CSV

```
┌────────────────────────────────────────────────────────────┐
│ Importar alumnos desde CSV                       [Cancelar]│
├────────────────────────────────────────────────────────────┤
│ Paso 2 de 4: Subir archivo                                 │
│                                                            │
│   ┌────────────────────────────────────────────────────┐   │
│   │                                                    │   │
│   │   Arrastra aquí tu CSV                             │   │
│   │   o haz clic para seleccionar                      │   │
│   │                                                    │   │
│   │   Formato: CSV separado por comas, UTF-8           │   │
│   │   Tamaño máximo: 5 MB                              │   │
│   │                                                    │   │
│   └────────────────────────────────────────────────────┘   │
│                                                            │
│                                       [← Atrás]  [Subir]   │
└────────────────────────────────────────────────────────────┘
```

### Paso C.3 — Preview y mapeo de columnas

Después de subir, el sistema lee el CSV y muestra:

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Importar alumnos desde CSV                                       [Cancelar]│
├────────────────────────────────────────────────────────────────────────────┤
│ Paso 3 de 4: Revisa los datos                                              │
│                                                                            │
│ Detectamos 82 filas. Mapeo de columnas:                                    │
│                                                                            │
│  Columna del CSV     →  Tag de Runcriticon                                 │
│  ─────────────────────   ─────────────────────────                         │
│  nombre               →  Nombre del alumno (obligatorio)                   │
│  email                →  Email del alumno (obligatorio)                    │
│  nivel                →  nivel ▾                                           │
│  distancia            →  distancia ▾                                       │
│  objetivo             →  objetivo ▾                                        │
│  terreno              →  terreno ▾                                         │
│  estado               →  estado ▾                                          │
│  obs                  →  No mapear ▾                                       │
│                                                                            │
│  Vista previa (primeras 5 filas):                                          │
│ ┌────────────────────────────────────────────────────────────────────────┐ │
│ │ Marta Sánchez | marta@..  | medio | 10k    | MMM     | asfalto | OK  │ │
│ │ Pedro Cordero | pc@..     | alto  | maratón| Valencia| asfalto | OK  │ │
│ │ Juan Mira     | jm@..     | medio | 10k    | <vacío> | asfalto | OK  │ │
│ │ Luis Sastre   | (vacío)   | iniciación | 5k| <vacío> | asfalto | ⚠   │ │
│ │ Ana Vega      | av@..     | XXX   | 10k    | Maratón | asfalto | ⚠   │ │
│ └────────────────────────────────────────────────────────────────────────┘ │
│                                                                            │
│ ⚠ 2 filas con errores (mostradas en rojo). Las puedes corregir aquí o     │
│ en tu CSV y volver a subir.                                                │
│                                                                            │
│                                                  [← Atrás]  [Importar →]   │
└────────────────────────────────────────────────────────────────────────────┘
```

Detalles:

- El sistema intenta **mapear automáticamente** las columnas del CSV con los tags del club por nombre.
- El admin puede ajustar manualmente cada mapeo desde un desplegable.
- Las filas con errores se marcan ⚠ y se muestran en rojo. Tipos de error:
  - Email faltante u obligatorio.
  - Valor de tag que no existe en la taxonomía (ej. nivel = "XXX"). Acción sugerida: "¿Añadir 'XXX' como nuevo valor a nivel?" inline.
  - Email duplicado con un alumno existente o con otra fila del CSV.
- El admin puede editar valores inline en la preview para corregir errores rápidos. Si los errores son muchos, mejor corregir el CSV y resubir.
- El botón "Importar" solo se activa cuando todos los errores están resueltos O el admin marca un checkbox: "Saltar filas con errores (X filas)".

### Paso C.4 — Confirmación y resultado

```
┌────────────────────────────────────────────────────────────┐
│ Importación completada                                     │
├────────────────────────────────────────────────────────────┤
│ ✅ 80 alumnos creados                                       │
│ ⏭ 2 filas saltadas (errores)                                │
│ 📨 Hemos enviado 80 invitaciones por email                  │
│                                                            │
│ Tus alumnos aparecen ahora en la lista. Puedes crear       │
│ grupos en cualquier momento.                               │
│                                                            │
│ [Ver alumnos]      [Crear grupos ahora]                    │
└────────────────────────────────────────────────────────────┘
```

## Acciones (vista lista)

| Acción | Resultado |
|---|---|
| Buscar | Filtra la tabla por nombre o email (incremental). |
| Filtrar por tag | Filtra la tabla. Combinable con búsqueda. |
| Click en fila | Abre side sheet de edición. |
| Nuevo alumno | Side sheet vacío. |
| Importar CSV | Pantalla full (flujo C). |
| Seleccionar fila(s) | Activa la `region:bulk-actions`. |
| Asignar tag (bulk) | Modal: selecciona tag + valor a asignar. Se añade a los N alumnos. Si ya lo tenían, se mantiene. |
| Quitar tag (bulk) | Modal: selecciona tag + valor a quitar. Se elimina de los N alumnos que lo tuvieran. |
| Cambiar estado (bulk) | Modal: selecciona nuevo valor del tag `estado` (o el marcado como estado principal). |
| Eliminar (bulk) | Modal de confirmación destructiva. |

## Estados de la pantalla

1. **Vacío inicial** — empty state grande: "Aún no tienes alumnos en el club. Empieza importando un CSV o añadiendo uno a uno." + dos CTA.
2. **Con alumnos** — lista visible.
3. **Filtrado sin resultados** — empty state: "Ningún alumno cumple los filtros. Cambia o limpia los filtros."
4. **Pendiente de taxonomía** — si no hay tags activos en el club: empty state con CTA "Definir taxonomía primero" → spec 02.
5. **Importación en curso** — barra de progreso superior persistente. Resto de la app accesible.
6. **Error de importación** — pantalla del paso C.3 con detalle del error.

## Validaciones y errores

- Email obligatorio, formato válido, único en el club.
- Nombre obligatorio, mínimo 2 caracteres.
- En CSV, valores de tag que no existen → opción de "crear sobre la marcha" o saltar la fila.
- Borrar alumno: si tiene historial de planes ejecutados → ofrecer "Desactivar (mantener historial)" en lugar de borrar.

## Responsive (móvil)

- Tabla pasa a lista de cards. Cada card: nombre + estado + chips de tags (todos abreviados a 2-3 + "más").
- Bulk actions disponibles pero con UX limitada.
- **Importación CSV no disponible en móvil**: aviso "Para importar CSV, usa la versión de escritorio."
- Side sheet ocupa pantalla completa.

## Opciones de diseño a explorar

### Tags visibles en la tabla — Opción A (recomendada): solo 2-3 principales + "+N"

Mostrar solo los tags más relevantes (nivel y objetivo) como chips. El resto en "+N" clickable que abre un tooltip con todos.

**Pros**: tabla legible, escaneable.
**Contras**: el admin tiene que hacer 1 clic extra para ver todos los tags.

### Tags visibles en la tabla — Opción B: chips compactos con todos los tags

Mostrar todos los tags en una columna ensanchada, en chips muy compactos (texto pequeño).

**Pros**: información completa visible.
**Contras**: filas muy altas, tabla con scroll horizontal.

### Edición de tags en lote — Opción A (recomendada): modal con selectores

El admin selecciona N alumnos, abre modal, selecciona tag y valor, aplica.

**Pros**: simple, predecible.
**Contras**: una operación a la vez.

### Edición de tags en lote — Opción B: panel de edición masiva en side sheet

Un side sheet con todos los tags del club; el admin va asignando valores y se aplican a los N alumnos al final.

**Pros**: cambios complejos en un solo flujo.
**Contras**: el admin puede olvidarse de aplicar.

**Recomendación**: implementar A primero. B si la beta muestra que A es insuficiente.

### Side sheet vs modal para alta individual

Side sheet (A, recomendada) → mantiene contexto de la lista. Modal (B) → más enfocado.

## Criterios de validación con usuario

- ✅ El admin del club piloto importa un CSV de 50+ alumnos en < 10 min, incluyendo corregir 2-3 errores.
- ✅ Da de alta a un alumno individual en < 1 min.
- ✅ Edita los tags de 5 alumnos en lote en < 2 min.
- ✅ Encuentra a un alumno por nombre o email en < 5s.
- ❌ Si abandona el CSV import porque "no me deja corregir" → rediseñar paso C.3.
- ❌ Si no descubre los bulk actions tras seleccionar varios → mejorar visibilidad.
