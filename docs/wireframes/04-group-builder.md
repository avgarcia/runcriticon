# 04 — Constructor de grupos

> Pantalla donde el admin o el entrenador crea, edita y administra grupos del club. **Es la pantalla más arriesgada del MVP**: si el constructor de filtros parece una herramienta de power-user, el admin (perfil no técnico) se bloquea (R18).

## Contexto

- **Roles**: admin del club Y entrenador. Mismo flujo para ambos; pequeñas diferencias de scope (el admin ve todos los grupos del club, el entrenador ve los suyos).
- **Cuándo se accede**: desde nav lateral > "Grupos". Después del onboarding.
- **Frecuencia**: alta inicial + creación puntual de subgrupos durante la temporada.
- **MUSTs cubiertos**: M6 (crear grupo como consulta sobre tags), M7 (ajuste manual de pertenencia), M9b (sugerencia de fusión de micro-grupos), M8 (asignar entrenadores).
- **Riesgo principal**: **R18** (constructor demasiado técnico).

## Objetivo del usuario

> "Crear los grupos del club como los pienso en mi cabeza, sin tener que aprender sintaxis ni discutir con el sistema."

## Inputs

- Taxonomía del club ya definida (spec 02) — al menos 1 tag con valores.
- Alumnos del club ya creados y con tags asignados (spec 03).
- Lista de entrenadores activos (spec 01).

Si falta alguna pre-condición, mostrar empty state con CTA al setup correspondiente.

## Pantalla principal — lista de grupos del club

Layout estándar (header + nav lateral + contenido).

```
┌────────────────────────────────────────────────────────────────────────────┐
│ Grupos                                          [+ Nuevo grupo]            │ region:page-header
├────────────────────────────────────────────────────────────────────────────┤
│ 12 grupos · 3 sin entrenador · 2 sugerencias de fusión                     │ region:summary
│                                                                            │
│ ⚠ Sugerencias de fusión:  [Ver detalle →]                                  │ region:suggestions (si las hay)
├────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────────────┐  │
│ │ Maratón Valencia avanzado                                            │  │
│ │ Filtro: objetivo = Maratón Valencia AND nivel ∈ {medio-alto, alto}   │  │
│ │ Entrenador: Carlos · Alumnos: 12  · Última actividad: hace 2 días    │  │
│ │                                                          [···]       │  │
│ └──────────────────────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────────────────────┐  │
│ │ Trail finde                                                          │  │
│ │ Filtro: terreno = trail AND día-de-entreno = finde                   │  │
│ │ Entrenador: — (sin asignar) · Alumnos: 6                             │  │
│ │ ⚠ Este grupo no tiene entrenador asignado                            │  │
│ │                                                          [···]       │  │
│ └──────────────────────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────────────────────┐  │
│ │ Iniciación CACO                                                      │  │
│ │ Filtro: nivel = iniciación                                           │  │
│ │ Entrenador: Ana · Alumnos: 8                                         │  │
│ │                                                          [···]       │  │
│ └──────────────────────────────────────────────────────────────────────┘  │
│ ...                                                          region:list   │
└────────────────────────────────────────────────────────────────────────────┘
```

### Componentes de la lista

#### `region:summary`

- Total de grupos.
- Grupos sin entrenador (resaltado si > 0).
- Sugerencias de fusión pendientes (resaltado si > 0).

#### `region:suggestions`

Banner amarillo, plegable. Aparece solo si hay sugerencias. Lista:

- Grupos con ≤ 2 alumnos (micro-grupos).
- Pares de grupos que comparten ≥ 80% de alumnos.

Cada sugerencia con CTA "Revisar" que lleva a modal específico de fusión (ver interacción C).

#### `region:list`

Card por grupo. Contenido:

- **Nombre** (negrita).
- **Filtro** en lenguaje humano (formato "tag valor AND tag valor..."). Si es largo, truncar a 1 línea con "...". Tooltip muestra completo.
- **Entrenador(es) asignado(s)** o "sin asignar" en rojo.
- **Nº de alumnos** y enlace para verlos.
- **Última actividad reportada** del grupo (si aplica).
- **Avisos**: sin entrenador, micro-grupo (≤ 2 alumnos), filtro vacío (no encaja ningún alumno con la query actual).
- Menú [···]:
  - Editar.
  - Duplicar (para crear variante).
  - Archivar.
  - Eliminar (solo si no tiene plan publicado este mes).
  - Asignar entrenador.

Comportamiento:

- Clic en la card → abre el grupo en modo edición (pantalla siguiente o side sheet ancho).

## Pantalla — crear o editar un grupo

Aquí está el corazón del riesgo R18. Necesita ser **visual y forgiving**, no técnico.

```
┌────────────────────────────────────────────────────────────────────────────┐
│ ← Volver  ·  Nuevo grupo                                       [Guardar]   │ region:editor-header
├────────────────────────────────────────────────────────────────────────────┤
│ Nombre del grupo                                                           │
│ [ Maratón Valencia avanzado                                             ]  │ region:group-name
│                                                                            │
│ ¿Quién entra en este grupo?                                                │ region:filter-builder
│                                                                            │
│ Los alumnos que cumplan TODAS estas condiciones:                           │
│                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ objetivo  =  [chip] Maratón Valencia [×]                       [×]   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │ nivel     ∈  [chip] medio-alto [×] [chip] alto [×]              [×]   │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
│  [+ Añadir condición]                                                      │
│                                                                            │
├────────────────────────────────────────────────────────────────────────────┤
│ Vista previa de alumnos (12):                                              │ region:preview
│ ─────────────────────────────────────                                      │
│   • Pedro Cordero                                                          │
│   • María González                                                         │
│   • Juan Pérez                                                             │
│   • ... (mostrar primeros 10, "+2 más")                                    │
│                                                                            │
│  [⚙ Ajustes avanzados: ajustes manuales (0)]                               │ region:advanced
├────────────────────────────────────────────────────────────────────────────┤
│ Entrenador asignado                                                        │ region:coach-assignment
│ [ + Asignar entrenador ▾ ]                                                 │
└────────────────────────────────────────────────────────────────────────────┘
```

### Componentes del editor

#### `region:editor-header`

- Botón "← Volver" (con confirmación si hay cambios sin guardar).
- Título "Nuevo grupo" o "[Nombre del grupo]" en edición.
- Botón "Guardar (cta:save-group)" primario, deshabilitado si no hay nombre o filtro inválido.

#### `region:group-name`

- Input grande, foco automático al entrar.
- Placeholder: *"Ej: Maratón Valencia avanzado, Trail finde, Los del martes..."*
- Validación: obligatorio, único en el club, 3-60 caracteres.

#### `region:filter-builder`

**Este es el componente crítico.** Construye la query sobre tags sin que el usuario escriba sintaxis.

Estructura:

- Texto guía arriba: *"Los alumnos que cumplan TODAS estas condiciones:"*. (En MVP solo AND; OR queda para post-MVP. Mencionar abajo.)
- Lista de **condiciones**. Cada condición es una fila con:
  - **Selector de tag**: desplegable con todos los tags activos del club.
  - **Operador**: según el tag:
    - Para enum simple (un valor permitido): `=` o `≠`.
    - Para enum múltiple: `∈` (incluye uno de), `∉` (no incluye ninguno de).
  - **Selector de valores**: multiselect con chips. Los valores disponibles dependen del tag seleccionado.
  - Botón **[×]** a la derecha para quitar la condición.
- Botón "+ Añadir condición": añade una nueva fila vacía abajo.

Comportamiento:

- Al cambiar cualquier condición, la **vista previa se actualiza al instante** (debounce 200ms).
- Si no hay condiciones, el filtro abarca a TODOS los alumnos del club (vista previa lo refleja con aviso).
- Si una condición tiene tag pero no valores, queda en estado "incompleto" (borde amarillo); el botón Guardar se deshabilita y aparece aviso: "Completa o quita las condiciones marcadas".
- **Sin sintaxis textual visible**. El admin nunca ve `objetivo = "Maratón Valencia" AND nivel IN ('medio-alto', 'alto')`. Lo ve como chips.

#### `region:preview`

Lista en tiempo real de alumnos que entran en el filtro.

- Cabecera: "Vista previa de alumnos (N)". El número cambia con cada edición del filtro.
- Lista (mostrar primeros 10, "+N más" expandible).
- Cada alumno es clickable y abre tooltip con sus tags (para que el admin entienda por qué entró).
- Si N = 0: empty state "Ningún alumno cumple este filtro" + sugerencia: *"Quizá la condición es muy estricta. Revisa los valores."*
- Si N ≤ 2: badge amarillo "Micro-grupo: el sistema sugerirá fusión".
- Si N > 50: badge informativo: "Grupo grande, considera si el plan será apropiado para todos".

#### `region:advanced`

Acordeón colapsado por defecto. Contiene:

- **Ajustes manuales (M7)**: lista de alumnos que el filtro NO incluye pero están manualmente añadidos, y alumnos que el filtro SÍ incluye pero están manualmente excluidos.
  - Botón "+ Añadir alumno manualmente" → autocomplete de búsqueda.
  - Botón "Excluir alumno del grupo" sobre cualquier alumno de la preview.
- Aviso: *"Los ajustes manuales prevalecen sobre el filtro automático. Úsalos con moderación."*

#### `region:coach-assignment`

- Desplegable multiselect con los entrenadores del club.
- Permite asignar 0 o más entrenadores. 0 deja el grupo "sin entrenador" (con aviso).
- Si se asigna ≥ 1, aviso inferior: "Los entrenadores recibirán acceso a este grupo."

## Acciones

| Acción | Resultado |
|---|---|
| Nuevo grupo | Pantalla editor en blanco. |
| Editar grupo | Pantalla editor con datos cargados. |
| Cambiar nombre | Inline, validación en tiempo real. |
| Añadir condición | Nueva fila vacía. |
| Quitar condición | Fila eliminada. Preview se actualiza. |
| Cambiar tag de una condición | Reset de valores (no aplican al tag nuevo). |
| Cambiar valores | Preview se actualiza. |
| Añadir alumno manual | Modal de búsqueda + selección. |
| Excluir alumno | Modal de confirmación. |
| Asignar entrenador | Desplegable + guardado inmediato (no requiere "Guardar" general). |
| Guardar | Persiste el grupo. Si es nuevo, redirige a la lista con el grupo nuevo resaltado. |
| Duplicar grupo (desde lista) | Crea copia con sufijo "(copia)", abre editor. |
| Archivar grupo | Lo retira de vistas activas pero conserva historial. |
| Eliminar grupo | Confirmación destructiva con escribir el nombre. Bloqueado si tiene plan publicado este mes. |

## Estados de la pantalla (editor)

1. **Nuevo grupo vacío** — nombre vacío, sin condiciones, preview "Ningún alumno (añade una condición o deja el filtro vacío para todo el club)".
2. **Filtro válido y guardable** — botón Guardar activo.
3. **Condición incompleta** — borde amarillo, botón Guardar deshabilitado, banner aviso.
4. **Filtro sin resultados** — preview vacía con sugerencia.
5. **Filtro con micro-grupo** — preview muestra N ≤ 2 con badge amarillo.
6. **Editando grupo existente** — incluye ajustes manuales si los hay.
7. **Cambios sin guardar al salir** — modal: "¿Guardar cambios?".
8. **Error de guardado** — toast rojo persistente.
9. **Sin entrenador** — aviso al guardar: "¿Guardar sin entrenador asignado?" (Sí / Asignar uno ahora).

## Interacciones clave

### Interacción A — Crear "Trail finde" partiendo de cero

1. Admin pulsa "+ Nuevo grupo".
2. Escribe nombre: "Trail finde". Pulsa Tab.
3. Pulsa "+ Añadir condición".
4. Selecciona tag: `terreno`.
5. En valores, selecciona el chip "trail".
6. Vista previa: 14 alumnos.
7. Pulsa "+ Añadir condición".
8. Selecciona tag: `día-de-entreno`.
9. En valores, selecciona "finde".
10. Vista previa: 6 alumnos. El número se actualiza al instante.
11. Asigna entrenador "Carlos".
12. Pulsa "Guardar". Toast: "Grupo creado".

### Interacción B — Editar "Maratón Valencia avanzado" para excluir manualmente a un alumno

1. Admin entra al grupo.
2. Ve en la preview que "Juan Pérez" está incluido, pero sabe que Juan se ha lesionado y va a hacer un plan distinto.
3. Hover sobre Juan en la preview → botón "Excluir".
4. Modal: "Excluir a Juan Pérez del grupo manualmente. Esta excepción se mantendrá aunque sus tags coincidan con el filtro."
5. Confirma.
6. Acordeón "Ajustes avanzados" se expande, muestra Juan en "Excluidos".
7. Pulsa Guardar.

### Interacción C — Revisar sugerencia de fusión

Trigger desde la pantalla de lista, banner `region:suggestions`.

1. Admin pulsa "Revisar" en una sugerencia.
2. Modal:
   ```
   ┌──────────────────────────────────────────────────────────────┐
   │ Sugerencia de fusión                                    [×]  │
   ├──────────────────────────────────────────────────────────────┤
   │ Estos dos grupos comparten 9 de 10 alumnos:                 │
   │                                                              │
   │  • "Maratón Valencia medio" (5 alumnos)                     │
   │  • "Maratón Valencia avanzado" (10 alumnos)                 │
   │                                                              │
   │ Diferencia: el grupo "avanzado" excluye nivel "medio".      │
   │                                                              │
   │ Opciones:                                                    │
   │  ◯ Fusionar en "Maratón Valencia" (15 alumnos, sin nivel)   │
   │  ◯ Mantener separados                                        │
   │  ◯ Quizá quieras añadir un grupo de "Maratón Valencia        │
   │     iniciación" (0 alumnos hoy) para diferenciarlos          │
   │                                                              │
   │                              [Cancelar]  [Aplicar opción]    │
   └──────────────────────────────────────────────────────────────┘
   ```
3. Admin elige una opción y aplica.

### Interacción D — Construir un filtro con 4 condiciones

Comprobación de que la UI escala. Mismo flujo que A, pero el admin añade 4 condiciones. Verificar visualmente que la pantalla no se rompe (scroll interno si es necesario en `region:filter-builder`).

## Validaciones y errores

- Nombre del grupo: obligatorio, único, 3-60 caracteres.
- Filtro válido: o ninguna condición (= todo el club) o todas las condiciones completas (tag + ≥1 valor).
- Al menos 1 alumno en la preview NO es obligatorio (se permite guardar grupos vacíos, útil si se está preparando una temporada futura), pero aparece aviso.
- Borrar un grupo con plan publicado este mes: bloqueado, solo se puede archivar.
- Al cambiar el tag de una condición, los valores anteriores se borran (con aviso si había alguno).

## Mensajes de feedback

- Guardado → toast verde.
- Sin entrenador al guardar → modal de confirmación.
- Micro-grupo → badge amarillo (sin bloquear).
- Filtro vacío sin condiciones → banner informativo: "Este grupo incluye a todos los alumnos del club (N)".

## Responsive (móvil)

- La pantalla de lista funciona con cards apiladas.
- El editor en móvil es menos cómodo. Recomendar usar escritorio para construir grupos (aviso si entra desde móvil).
- Si insiste en móvil:
  - Filter builder con cada condición en card a pantalla completa.
  - Preview en una pestaña separada (tabs "Filtro" / "Vista previa") porque no caben juntos.

## Opciones de diseño a explorar

### Filter builder — Opción A (recomendada): condiciones apiladas con chips

Lo descrito. Cada condición es una fila con `tag + operador + chips de valores`.

**Pros**: visual, sin sintaxis, fácil de entender.
**Contras**: ocupa altura cuando hay muchas condiciones.

### Filter builder — Opción B: tabla compacta tipo Notion

Una tabla con columnas Tag · Operador · Valor(es) · [×]. Más densa que la A.

**Pros**: más eficiente en altura.
**Contras**: menos "respira", puede percibirse como hoja de cálculo (técnica).

### Filter builder — Opción C: pregunta-respuesta tipo chatbot

UI conversacional: *"¿Qué tag quieres usar primero? → seleccionas → ¿qué valores? → seleccionas → ¿añadir otro tag?"*.

**Pros**: extremadamente forgiving para usuarios no técnicos.
**Contras**: lento para editar; mal para usuarios recurrentes.

**Recomendación**: diseñar **A y B** para validar en wireframes con el admin. La C es interesante para onboarding pero molesta para uso recurrente; quizá como modo "asistido" opcional.

### Preview — Opción A (recomendada): lateral siempre visible

Como en el layout principal, columna derecha siempre visible.

### Preview — Opción B: pestaña "Vista previa"

Acordeón o tab que el admin abre on-demand.

**Pros de A**: feedback inmediato visual del impacto de cada cambio.
**Pros de B**: más espacio para el editor.

**Recomendación**: **A** en escritorio, **B** en móvil.

### Ajustes manuales (excepciones) — visibilidad

#### Opción A — Acordeón colapsado por defecto

Como está en el layout. Razón: la mayoría de grupos no tendrán excepciones, y mostrarlas siempre añade ruido.

#### Opción B — Pestañas "Filtro" / "Ajustes manuales"

Más visible pero exige que el admin entienda el concepto desde el principio.

**Recomendación**: **A**. Si en validación se ve que el admin no descubre los ajustes manuales, cambiar a B o destacar más el acordeón.

## Criterios de validación con usuario

- ✅ El admin (sin haber visto la pantalla) crea un grupo nuevo de 2 condiciones en < 2 min.
- ✅ Entiende qué hace el filtro al verlo en chips, sin haber leído documentación.
- ✅ Identifica que la vista previa es lo que va a recibir el plan.
- ✅ Edita una condición y ve que la preview cambia.
- ✅ Acepta una sugerencia de fusión y entiende qué pasó.
- ❌ Si pregunta por sintaxis ("¿cómo pongo AND?") → la UI debe esconder mejor el AND implícito (probablemente texto guía más claro).
- ❌ Si no encuentra cómo añadir un alumno manualmente → mejorar visibilidad del acordeón avanzado.
- ❌ Si construye un filtro de 4+ condiciones y se atasca → considerar plantillas o modo conversacional.
