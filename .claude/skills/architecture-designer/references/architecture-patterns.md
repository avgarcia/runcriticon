# Patrones de Arquitectura

## Comparación de Patrones

| Patrón               | Ideal para                            | Tamaño del equipo | Ventajas e inconvenientes                                         |
|----------------------|---------------------------------------|-------------------|-------------------------------------------------------------------|
| **Monolito**         | Dominio simple, equipo pequeño        | 1-10              | Despliegue sencillo; partes difíciles de escalar                  |
| **Monolito Modular** | Complejidad creciente                 | 5-20              | Límites entre módulos; despliegue único                           |
| **Microservicios**   | Dominio complejo, organización grande | Más de 20         | Escalabilidad independiente; complejidad operativa                |
| **Sin servidor**     | Carga variable, basado en eventos     | Cualquiera        | Escalado automático; arranques en frío, dependencia del proveedor |
| **Bajo en eventos**  | Procesamiento asíncrono               | Más de 10         | Acoplamiento flexible; complejidad de depuración                  |

## Monolito

```
┌─────────────────────────────────────┐
│            Application              │
│  ┌─────┐  ┌──────┐  ┌────────┐      │
│  │Users│  │Orders│  │Products│      │
│  └─────┘  └──────┘  └────────┘      │
│  └───────────┬──────────────┘       │
│          Database                   │
└─────────────────────────────────────┘
```

**Cuándo usarlo**:
- Al iniciar un nuevo proyecto
- Equipo pequeño (< 10 desarrolladores)
- Dominio sencillo
- Se requiere iteración rápida

**Ventajas**: Implementación sencilla, depuración fácil, sin latencia de red
**Desventajas**: Dificultad para escalar de forma independiente, tecnología obsoleta, riesgo de implementación

## Microservicios

```
┌──────────┐  ┌──────────┐  ┌──────────┐
│  Users   │  │  Orders  │  │ Products │
│ Service  │  │ Service  │  │ Service  │
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │             │             │
┌────▼────┐   ┌────▼────┐   ┌────▼────┐
│ User DB │   │Order DB │   │ Prod DB │
└─────────┘   └─────────┘   └─────────┘
```

**Cuándo usarlo**:
- Equipos grandes (más de 20 desarrolladores)
- Dominios complejos con límites bien definidos
- Requisitos de escalabilidad diferentes para cada servicio
- Necesidades de tecnologías políglotas

**Ventajas**: Escalabilidad independiente, autonomía del equipo, aislamiento de fallos
**Desventajas**: Complejidad del sistema distribuido, consistencia eventual, sobrecarga operativa

## Orientado a eventos

```
┌──────────┐     ┌─────────────┐     ┌──────────┐
│ Producer │────▶│ Message Bus │────▶│ Consumer │
└──────────┘     │  (Kafka)    │     └──────────┘
                 └─────────────┘
                       │
                       ▼
                 ┌────────────┐
                 │  Consumer  │
                 └────────────┘
```

**Cuándo usarlo**:
- Se requiere procesamiento asíncrono
- Acoplamiento flexible entre servicios
- Necesidades de Event Sourcing
- Mensajería de alto rendimiento

**Ventajas**: Servicios desacoplados, escalabilidad, registro de auditoría
**Desventajas**: Inconsistencia eventual, complejidad de depuración, orden de mensajes

## CQRS (Segregación de Responsabilidad de Comandos y Consultas)

```
┌─────────┐         ┌─────────────┐
│ Commands│────────▶│ Write Model │──┐
└─────────┘         └─────────────┘  │
                                     ▼
                                ┌──────────┐
                                │  Events  │
                                └──────────┘
                                     │
┌─────────┐         ┌─────────────┐  │
│ Queries │◀────────│ Read Model  │◀─┘
└─────────┘         └─────────────┘
```

**Cuándo usarlo**:
- Relación lectura/escritura muy desequilibrada
- Consultas de lectura complejas
- Arquitectura basada en eventos
- Diferentes necesidades de optimización

## Referencia rápida

| Requisito               | Patrón recomendado   |
|-------------------------|----------------------|
| Aplicación CRUD simple  | Monolito             |
| Startup en crecimiento  | Monolito modular     |
| Escala empresarial      | Microservicios       |
| Carga variable          | Sin servidor         |
| Procesamiento asíncrono | Orientado a eventos  |
| Lectura intensiva       | CQRS                 |
