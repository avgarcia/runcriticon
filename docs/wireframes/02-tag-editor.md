# 02 — Editor de tags (taxonomía del club)

> Pantalla donde el admin define qué tags existen en su club y qué valores admite cada uno. Es la pieza más nueva conceptualmente del MVP y la que más arriesga R17.

## Contexto

- **Rol**: admin del club.
- **Cuándo se accede**:
  - **Embebida** en el paso 2 del [onboarding](01-admin-onboarding.md).
  - **Standalone** desde Ajustes > Taxonomía del club, en cualquier momento.
- **Frecuencia**: alta inicial + ediciones puntuales (añadir una carrera, un tag nuevo, archivar valores).
- **MUSTs cubiertos**: M4.
- **Riesgo principal**: R17 (admin se atasca si no hay tags pre-cargados sensatos).

## Objetivo del usuario

> "Que la herramienta hable el idioma de mi club: que los tags y sus valores sean los que yo uso, no los que vengan por defecto."

## Inputs

- Set de **tags pre-cargados** por el sistema:

  | Tag | Tipo de valor | Pre-carga |
  |---|---|---|
  | `nivel` | enum | iniciación · medio · medio-alto · alto |
  | `distancia` | enum | 1500m · 5k · 10k · media maratón · maratón |
  | `objetivo` | enum **con metadata** (fecha, distancia) | "Sin carrera" + 5-8 carreras populares de la región (MMM, San Silvestre, Maratón Valencia, etc.). |
  | `terreno` | enum | asfalto · trail · pista |
  | `estado` | enum | activo · lesión · post-parto · descanso |

- Catálogo de carreras populares precargadas, geo-segmentado por país (España por defecto).

## Layout

Layout estándar de admin (header + nav lateral + contenido). Esta es la pantalla del contenido principal.

```
┌────────────────────────────────────────────────────────────────────┐
│ Taxonomía del club                                  [+ Nuevo tag]  │ region:page-header
├────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────┐ ┌──────────────────────────────────────┐ │
│ │ Tags (lista)         │ │ Detalle del tag seleccionado         │ │
│ │                      │ │                                      │ │
│ │ ▸ nivel              │ │ Nombre: [ nivel              ]      │ │
│ │ ▸ distancia          │ │ Tipo:    enum simple                 │ │
│ │ ▶ objetivo  ★        │ │                                      │ │
│ │ ▸ terreno            │ │ Valores:                             │ │
│ │ ▸ estado             │ │ ┌──────────────────────────────────┐ │ │
│ │ ▸ día-de-entreno     │ │ │ • iniciación               [···] │ │ │
│ │                      │ │ │ • medio                    [···] │ │ │
│ │ region:tag-list      │ │ │ • medio-alto               [···] │ │ │
│ │                      │ │ │ • alto                     [···] │ │ │
│ │                      │ │ │ + Añadir valor                   │ │ │
│ │                      │ │ └──────────────────────────────────┘ │ │
│ │                      │ │                                      │ │
│ │                      │ │ Uso: 47 alumnos tienen este tag      │ │
│ │                      │ │                                      │ │
│ │                      │ │           [Archivar tag]  [Guardar]  │ │
│ │                      │ │ region:tag-detail                    │ │
│ └──────────────────────┘ └──────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

## Componentes

### `region:page-header`

- Título "Taxonomía del club" + descripción 1 línea: *"Los tags definen cómo agrupas a tus alumnos. Cada tag tiene una lista de valores posibles."*
- Botón "+ Nuevo tag (cta:new-tag)" primario.
- (En contexto embebido del wizard, esta cabecera la sustituye la del wizard.)

### `region:tag-list`

Lista vertical de tags del club. Cada elemento:

- Icono pequeño (drag handle).
- Nombre del tag.
- Indicador opcional ★ si tiene metadata avanzada (catálogo con fechas, por ejemplo `objetivo`).
- Contador a la derecha (en gris): "5 valores · 47 alumnos".
- Estado: activo o **archivado** (atenuado, con icono de archivo).

Comportamiento:

- Selección: clic en un tag lo abre en `region:tag-detail`.
- Drag-and-drop para reordenar (afecta solo al orden en que se muestran en otras pantallas).
- Si no hay tag seleccionado (primer acceso), `region:tag-detail` muestra empty state: *"Selecciona un tag para editarlo o crea uno nuevo."*

### `region:tag-detail`

Editor del tag seleccionado.

#### Cabecera

- Campo de texto editable: nombre del tag (kebab-case interno, label libre visible).
- Selector "Tipo": enum simple / enum con metadata. (En MVP los dos son válidos. La metadata se usa para `objetivo` y similares.)
- Toggle "Permite múltiples valores por alumno" (sí/no). Por defecto: no, excepto en `objetivo` donde es sí.
- (Si está archivado) banner amarillo: *"Este tag está archivado. Los alumnos lo conservan pero no se puede asignar a nuevos."* + botón "Reactivar".

#### Lista de valores

Lista vertical. Cada valor:

- Punto / bullet.
- Texto del valor (inline editing).
- Si el tipo es **enum con metadata** (caso `objetivo`):
  - Campo de fecha (opcional, ej. fecha de la carrera).
  - Selector de distancia (referenciando el tag `distancia` si aplica).
  - Aviso visual si la fecha ya pasó: *"Carrera pasada"* (icono reloj con tachado).
- Menú [···]:
  - Renombrar.
  - Archivar valor (mantiene asignaciones existentes pero ya no aparece para nuevas).
  - Fusionar con otro valor (modal de fusión, ver interacción C).
  - Ver alumnos que lo usan (abre side sheet).

Al final de la lista: "+ Añadir valor (cta:add-value)".

#### Pie

- Contador: "Uso: X alumnos tienen este tag".
- Botón secundario "Archivar tag (cta:archive-tag)" en rojo apagado. Confirmación modal.
- Botón primario "Guardar cambios (cta:save)" — solo activo si hay cambios sin guardar.

## Acciones

| Acción | Resultado |
|---|---|
| Crear tag | Modal con: nombre, tipo (enum simple / enum metadata), permite múltiples. Crea el tag vacío y lo selecciona. |
| Renombrar tag | Inline editing del nombre. Confirma con Enter o blur. |
| Añadir valor | Aparece nueva fila al final con foco en el campo de texto. Enter guarda y abre otra fila. |
| Renombrar valor | Inline editing. Afecta a todos los alumnos que lo usaban (no se duplica). |
| Archivar valor | Modal: "X alumnos tienen este valor. ¿Lo mantenemos en sus tags?" (Sí) o "Borrarlo de los X alumnos" (no recomendado, requiere segunda confirmación). |
| Fusionar valores | Ver interacción C. |
| Reordenar valores | Drag handle por valor. |
| Reordenar tags | Drag en la lista de tags. |
| Archivar tag | Confirmación: "El tag dejará de poder asignarse pero se conserva en alumnos actuales." |
| Reactivar tag archivado | 1 clic, sin confirmación. |
| Guardar | Persiste cambios. Toast verde: "Cambios guardados". |
| Descartar | Si hay cambios y el usuario sale del tag, modal: "Tienes cambios sin guardar. ¿Guardar / Descartar / Cancelar?". |

## Estados de la pantalla

1. **Primer acceso (vacío)** — solo aparecen los 5 tags pre-cargados, sin alumnos asignados. Empty state en el detalle.
2. **Tag seleccionado sin cambios** — detalle visible, botón "Guardar" deshabilitado.
3. **Tag seleccionado con cambios sin guardar** — botón "Guardar" activo y resaltado. Banner sutil: "Hay cambios sin guardar".
4. **Tag archivado seleccionado** — banner amarillo, edición limitada.
5. **Sin tags** — caso imposible (siempre hay pre-carga), pero por si acaso: empty state con CTA "+ Nuevo tag".
6. **Error de guardado** — toast rojo + el botón "Guardar" sigue activo para reintentar.
7. **Cargando** — skeleton en lista + detalle.

## Interacciones clave

### Interacción A — Renombrar el tag `terreno` a "tipo de carrera"

1. Admin pulsa sobre el nombre `terreno` en `region:tag-detail`.
2. Se convierte en input editable, foco automático.
3. Escribe "tipo de carrera".
4. Pulsa Enter o hace clic fuera.
5. El nombre se actualiza en `region:tag-list` y en cualquier otro lugar (vista de alumnos, grupos…).
6. Aparece toast verde: "Tag renombrado".

### Interacción B — Añadir un valor "ultra" al tag `distancia`

1. Admin selecciona el tag `distancia`.
2. Pulsa "+ Añadir valor".
3. Aparece fila vacía con input enfocado.
4. Escribe "ultra".
5. Pulsa Enter → la fila se confirma y aparece otra fila vacía debajo para añadir más.
6. Pulsa Esc o clic fuera para terminar.

### Interacción C — Fusionar dos valores del tag `objetivo`

1. Admin nota que tiene "MMM 2026" y "Media Maratón Madrid 2026" como valores distintos por error histórico.
2. Pulsa [···] en "MMM 2026" → "Fusionar con otro valor".
3. Modal: "¿Con qué valor fusionas 'MMM 2026'?" — desplegable con los demás valores del tag.
4. Selecciona "Media Maratón Madrid 2026".
5. Vista previa: "X alumnos que tenían 'MMM 2026' pasarán a tener 'Media Maratón Madrid 2026'. El valor 'MMM 2026' se borrará."
6. Confirma. Toast: "Valores fusionados".

### Interacción D — Crear un tag nuevo "día-de-entreno"

1. Admin pulsa "+ Nuevo tag".
2. Modal: nombre = "día de entreno", tipo = enum simple, permite múltiples = sí (un alumno puede entrenar en varios bloques).
3. Crea → se selecciona en la lista, el detalle aparece vacío.
4. Admin va añadiendo valores: "lun-mié-vie", "mar-jue", "finde", "mañanas", "tardes".
5. Pulsa Guardar.

## Validaciones y errores

- **Nombre de tag**: obligatorio, único en el club, máximo 40 caracteres. Caracteres permitidos: letras, números, guiones, espacios. Internamente se guarda como kebab-case derivado.
- **Valor de tag**: obligatorio, único dentro del tag, máximo 60 caracteres. Permite espacios y caracteres especiales.
- **Tag con valores asignados a alumnos no se puede borrar**, solo archivar.
- **No se puede archivar un tag que es referenciado por un grupo vivo** (M6). Aviso: "Este tag se usa en N grupos. Reescribe esos filtros antes de archivar." + lista clickable de grupos.
- **Fechas en `objetivo`**: opcional, pero recomendada. Si la fecha ya pasó, marcar visualmente (pero permitir).

## Mensajes de feedback

- Guardado exitoso → toast verde 3s.
- Error de guardado → toast rojo persistente hasta que el admin lo descarte.
- Cambios pendientes → banner sutil arriba del detalle.

## Responsive (móvil)

- Pasa de 2 columnas a 1 columna. La lista de tags aparece primero; al seleccionar uno, se navega a una pantalla aparte con el detalle (con botón "← Volver").
- Drag-and-drop de tags no funciona en móvil; se reemplaza por flechas arriba/abajo en cada tag.

## Opciones de diseño a explorar

### Opción A — Vista 2 columnas master-detail (recomendada)

Lo descrito arriba: lista a la izquierda, detalle a la derecha. Mantiene contexto y permite navegar rápido entre tags.

**Pros**: contexto siempre visible, eficiente para editar varios tags seguidos.
**Contras**: en pantallas pequeñas (laptop 13") el detalle queda estrecho.

### Opción B — Lista plana acordeón

Cada tag es una fila colapsable; al expandir, se ve la lista de valores in-line. Se pueden tener varios tags expandidos a la vez.

**Pros**: comparativa visual entre tags posible.
**Contras**: edición se vuelve confusa cuando hay muchos valores; scroll constante.

### Opción C — Modal por edición

Lista de tags como tabla densa. Click en un tag abre modal con editor.

**Pros**: lista limpia, mucha densidad de información.
**Contras**: modal interrumpe el flujo si el admin quiere editar varios tags.

**Recomendación**: diseñar **A y B**, validar con admin del club piloto. La hipótesis: A para uso recurrente, B podría ser mejor en onboarding (más visual, comparativa).

## Criterios de validación con usuario

- ✅ El admin del club piloto, sin haber visto la pantalla antes, identifica cómo añadir un tag nuevo en < 30s.
- ✅ Identifica que `objetivo` es donde gestiona las carreras del club.
- ✅ Renombra un valor sin pedir ayuda.
- ✅ Entiende la diferencia entre "archivar tag" y "borrar tag" (saben qué pasa con los alumnos).
- ❌ Si confunde "archivar" con "borrar" o no encuentra cómo añadir un valor → rediseñar.
- ❌ Si no encuentra la diferencia entre "tag" y "valor" del tag → revisar copy.
