# JSON Schemas de integration events

Esquemas versionados de los **integration events públicos** que publica cada módulo del backend. Cruce: **ADR-0007 D11**, [`docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md) §6.

## Convención de nombres

```
schemas/{modulo}/{evento}-v{N}.json
```

Ejemplos:

```
schemas/
├── identidad/
│   ├── alumno-eliminado-v1.json
│   ├── consentimiento-concedido-v1.json
│   └── consentimiento-revocado-v1.json
├── planificacion/
│   ├── plan-publicado-v1.json
│   └── sesion-personalizada-v1.json
└── salud/
    └── marca-actualizada-v1.json
```

## Regla del contrato

Cada integration event que el código publica **debe** tener su JSON Schema correspondiente en este directorio. La validación es **obligatoria** en el job `contractTest` del CI (ver [`docs/arquitectura/testing-de-modulos.md`](../docs/arquitectura/testing-de-modulos.md) §7).

## Estructura mínima de un schema

Cada evento debe declarar los 6 campos obligatorios + `traceparent` opcional (ADR-0007 D11 + observabilidad):

- `eventId` (UUID)
- `aggregateId` (UUID)
- `occurredAt` (ISO 8601 timestamp)
- `version` (integer)
- `clubId` (UUID)
- `actorId` (UUID o null)
- `traceparent` (string opcional — W3C Trace Context, ADR-0011 D4)

Más los campos específicos del evento.

## Versionado breaking

Cuando un evento cambia de forma rompiente:

1. Se añade `{evento}-v{N+1}.json` con el nuevo shape.
2. Durante 4 semanas de ventana, el emisor publica v{N} y v{N+1} simultáneamente.
3. Cada consumidor migra a v{N+1} sin presión.
4. Pasada la ventana, el emisor retira v{N}.

Cruce: ADR-0007 D11 + [`docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md) §6.

## Payloads de ejemplo (retrocompatibilidad)

Cada versión publicada de un schema tiene su payload de ejemplo en
`backend/src/test/resources/schemas/{modulo}/{evento}-v{N}-example.json`. `EventBackwardCompatibilityTest` lo
deserializa contra la clase actual, la misma comprobación que haría el outbox al reentregar un evento escrito por la
versión anterior de la app. Un payload commiteado no se edita para que el test pase; cada versión nueva añade el suyo.
Detalle en [`docs/arquitectura/testing-de-modulos.md`](../docs/arquitectura/testing-de-modulos.md) §7.

## Estado actual

Un schema `v1` por cada integration event que publica el código. `IntegrationEventArchTest` exige que no falte
ninguno.
