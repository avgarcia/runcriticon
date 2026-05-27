# 10 — Marcas del alumno

> Pantalla donde el alumno introduce y mantiene sus marcas (PRs) en distancias estándar. Las marcas son **privadas del alumno**: nadie más del club las ve. Habilitan los ritmos relativos del plan (M19).

## Contexto

- **Rol**: alumno.
- **Cuándo se accede**:
  - **Onboarding** — al activar la cuenta, si el alumno acepta, se le invita a rellenar al menos una marca.
  - **Standalone** desde el menú de cuenta del alumno (icono de perfil → "Mis marcas").
  - **Empty state** — desde la vista "hoy" (spec 06) cuando una sesión tiene ritmo relativo a una distancia sin marca, el CTA *"Añade tu marca de 10K"* lleva aquí.
- **Frecuencia**: baja. Alta al empezar; actualización tras una carrera o test.
- **MUSTs cubiertos**: M20 (marcas del alumno). Habilita M19 (ritmos relativos).
- **Riesgo principal**: que el alumno no rellene las marcas y vea sesiones sin ritmo → no entrena bien → abandona la app.

## Objetivo del usuario

> "Que mi plan se ajuste a mi nivel sin que yo tenga que entender nada técnico. Solo escribo mis tiempos de carrera."

## Decisión de producto clave — privacidad fuerte

Las marcas las gestiona **solo el alumno**. El entrenador y el admin **no las ven** en ninguna pantalla, listado ni contador agregado. Esta decisión:

- Reduce trabajo y responsabilidad al entrenador (el usuario lo pidió: *"no quiero que lo conozca, sería darle más trabajo"*).
- Crea confianza en el alumno: sus tiempos son su asunto.
- Implica que el entrenador, al diseñar un ritmo relativo, **no sabe** si el alumno tiene esa marca. Si no la tiene, el alumno se entera al mirar su sesión.

Esto se respeta arquitectónicamente: las marcas viven en el módulo **Seguimiento** y no salen de allí (ADR-0002).

## Inputs

- 4 distancias estándar: **5K**, **10K**, **21K** (media maratón), **42K** (maratón).
- Cada distancia: tiempo opcional, expresado como `hh:mm:ss` o `mm:ss`.

## Layout principal — móvil-first

Esta pantalla está pensada **para móvil** (es donde el alumno la abrirá).

```
┌──────────────────────────────────┐
│ ← Mis marcas               Marta │ region:header
├──────────────────────────────────┤
│                                  │
│  Tus marcas son privadas.        │ region:intro
│  Solo tú las ves. Tu entrenador  │
│  no las conoce.                  │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 5K                         │  │
│  │ 22:45                      │  │ region:mark-card
│  │ Actualizada hace 3 meses   │  │
│  │                  [✎ Editar]│  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 10K                         │  │
│  │ 47:30                      │  │
│  │ Actualizada hace 6 semanas │  │
│  │                  [✎ Editar]│  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 21K (Media maratón)        │  │
│  │ Sin marca                  │  │ (estado vacío)
│  │                  [+ Añadir]│  │
│  └────────────────────────────┘  │
│                                  │
│  ┌────────────────────────────┐  │
│  │ 42K (Maratón)               │  │
│  │ Sin marca                  │  │
│  │                  [+ Añadir]│  │
│  └────────────────────────────┘  │
│                                  │
└──────────────────────────────────┘
```

## Componentes

### `region:header`

- Flecha "← Volver" a la izquierda (vuelve al menú de cuenta o a "hoy" según de dónde se accedió).
- Título "Mis marcas".
- Avatar del alumno a la derecha.

### `region:intro`

Texto breve que **subraya la privacidad** desde el primer renderizado:

> *"Tus marcas son privadas. Solo tú las ves. Tu entrenador no las conoce."*

Necesario para neutralizar la duda implícita del alumno (*"¿esto lo va a ver mi entrenador?"*).

### `region:mark-card`

Una card por distancia (4 en total). Cada una muestra:

- **Distancia**: 5K, 10K, 21K (Media maratón), 42K (Maratón).
- **Tiempo** en grande, formato `mm:ss` o `hh:mm:ss`. Si no hay marca: *"Sin marca"*.
- **Fecha relativa**: "Actualizada hace X" — para que el alumno vea si está vieja. Solo si hay marca.
- **Acción**: `✎ Editar` si hay marca; `+ Añadir` si está vacía. Abre el editor de marca (ver más abajo).

### Editor de marca (modal)

Al pulsar Editar/Añadir, se abre un modal compacto:

```
┌──────────────────────────────────┐
│  Tu marca de 10K            [×] │
├──────────────────────────────────┤
│                                  │
│  Tiempo                          │
│  [ 47 ] : [ 30 ]   min : seg     │
│                                  │
│  [Cancelar]     [Guardar marca]  │
└──────────────────────────────────┘
```

Para 21K y 42K se añade un campo `[ horas ]` adicional (formato `hh:mm:ss`).

Acciones extra cuando ya hay marca:

- Botón **"Borrar marca"** en el pie izquierdo, en rojo apagado. Borrar emite `MarcaRetirada` y el read model recalcula a "no resuelta" donde tocaba.

## Acciones

| Acción | Resultado |
|---|---|
| Añadir marca | Modal vacío de esa distancia. Al guardar: marca persistida, evento `MarcaActualizada` emitido, toast verde *"Marca de 10K guardada"*. Si el alumno tenía planes pendientes con ritmo relativo a esa distancia, los verá resueltos en su próxima recarga. |
| Editar marca | Modal con la marca actual. Al guardar: mismo flujo. |
| Borrar marca | Confirmación: "¿Borrar tu marca de 10K? Las sesiones con ritmo relativo a 10K dejarán de mostrar el ritmo concreto." → emite `MarcaRetirada`. |

## Estados de la pantalla

1. **Primera vez** — las 4 cards en estado vacío con `+ Añadir`. Si se llega desde el CTA de la vista hoy, **se abre directamente el editor** de la distancia que faltaba.
2. **Con alguna marca** — mezcla de cards con tiempo y sin.
3. **Marca antigua (> 6 meses)** — la fecha relativa se muestra en color amber con un pequeño icono ⓘ y tooltip: *"Tu marca lleva tiempo sin actualizarse. Si has corrido una mejor, actualízala."*. Sin obligar.
4. **Sin conexión** — última versión cacheada; los cambios se encolan y se reintenta al recuperar conexión.

## Interacciones clave

### Interacción A — Rellenar la marca de 10K desde el empty state de "hoy"

1. Marta abre la app por la mañana, hoy toca *"Tempo: 10K + 10s/km"*.
2. La card de "hoy" muestra el bloque base de la sesión, pero el ritmo aparece como *"Sin ritmo"* + CTA primario azul: **"Añade tu marca de 10K para ver tu ritmo"**.
3. Pulsa el CTA → llega aquí con el modal de **10K** ya abierto.
4. Introduce `47:30`, pulsa "Guardar marca".
5. Toast verde + vuelve a "hoy". Ahora la card muestra el ritmo resuelto: *"47:30/10 ≈ 4:45/km + 10s ≈ 4:55/km"*. (En la práctica se redondea y se muestra **"4:55/km"** + texto sutil *"basado en tu 10K"*.)

### Interacción B — Actualizar una marca tras una carrera

1. Marta corrió una San Silvestre 10K y mejoró a `46:50`.
2. Abre la app → menú de cuenta → "Mis marcas".
3. Card de 10K muestra `47:30` con *"Actualizada hace 6 semanas"*.
4. Pulsa `✎ Editar`, escribe `46:50`, guarda.
5. Toast verde. Si tenía un plan publicado en curso con ritmos relativos a 10K, las sesiones se recalculan en segundos.

## Validaciones y errores

- **Formato del tiempo**: validación inline. Segundos `0-59`. Minutos `0-59` para 5K/10K; horas `0-23` para 21K/42K.
- **Tiempo no plausible** (ej. 5K en `1:00`): aviso sutil *"¿Estás seguro?"* + permitir continuar. No bloquear.
- **Conexión perdida al guardar**: el cambio se guarda local y se sincroniza al recuperar la red; banner *"Sincronizando…"*.

## Mensajes de feedback

- Guardado → toast verde con la distancia y el tiempo: *"Marca de 10K guardada: 47:30"*.
- Borrado → toast neutro: *"Marca de 10K eliminada"*.

## Responsive (escritorio)

- Layout horizontal 2×2 (cuatro cards en una rejilla).
- El editor sigue siendo modal.
- Mantiene el énfasis en la privacidad arriba.

## Opciones de diseño a explorar

### Tarjetas individuales (Opción A, recomendada) — lo descrito

Una card por distancia, con CTA explícito.

**Pros**: cada distancia tiene la misma prominencia visual. Empty state claro.
**Contras**: en móvil ocupa scroll.

### Tabla compacta (Opción B)

Una fila por distancia, denso.

**Pros**: cabe en una pantalla sin scroll.
**Contras**: el `+ Añadir` para empty state queda menos invitador.

**Recomendación**: A.

## Criterios de validación con usuario

- ✅ El alumno entiende, sin preguntar, que sus marcas son privadas.
- ✅ Rellena una marca en < 20s desde el CTA de "hoy".
- ✅ Encuentra cómo actualizar una marca antigua.
- ❌ Si pregunta *"¿esto lo va a ver mi entrenador?"* → reforzar el copy del intro.
- ❌ Si no rellena su primera marca tras ver el CTA → revisar el copy del empty state en "hoy".
