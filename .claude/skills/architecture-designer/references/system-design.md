# System Design Template

## Design Template

```markdown
# System: {System Name}

## Requerimientos

### Funcionales
- [Que debe hacer el sistema]
- [Caracteristicas y funcionalidades clave]
- [Integraciones externas necesarias]

### No Funcionales
- **Rendimiento**: Tiempo de respuesta < 200ms p95
- **Disponibilidad**: 99.9% uptime (8.76 hours downtime/year)
- **Escalabilidad**: Soporta 10,000 usuarios concurrentes
- **Seguridad**: Cumplimiento de PCI DSS requerido
- **Mantenibilidad**: Despliegues semanales sin downtime

## Arquitectura alto nivel

    ```
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │   Client    │────▶│  API Gateway │────▶│  Service    │
    │   (Web)     │     │   (Kong)    │     │  (Node.js)  │
    └─────────────┘     └─────────────┘     └─────────────┘
    │                   │
    ▼                   ▼
    ┌─────────────┐     ┌─────────────┐
    │    Auth     │     │  Database   │
    │  (Auth0)    │     │ (PostgreSQL)│
    └─────────────┘     └─────────────┘
    ```

## Detalles Componentes

### Capa API
- Tecnología: Node.js con Express/NestJS
- Responsabilidades: Enrutamiento de solicitudes, validación, autenticación
- Escalabilidad: Horizontal mediante balanceador de carga

### Capa Datos
- Primaria: PostgreSQL (transacciones, relaciones)
- Cache: Redis (sesiones, datos calientes)
- Storage: S3 (archivos, imágenes)

### Servicios Externos
- Auth: Auth0 (SSO, MFA)
- Email: SendGrid (transaccional)
- Monitorización: Datadog (APM, logs)

## Decisiones Clave

| Decision                 | Relacionado                         |
|--------------------------|-------------------------------------|
| PostgreSQL sobre MongoDB | Datos relacionales, ACID necesarios |
| Redis para cacheo        | Sub-ms latencia requerida           |
| Auth0 sobre custom       | Reduce riesgo de seguridad          |

## Estrategia de Escalabilidad

### Actual (MVP)
- Despliegue en una región
- 2 instancias de API detrás de un balanceador de carga
- Instancia única de RDS

### Futura (10x)
- Multi-region con CDN
- Auto-scaling API (2-10 instancias)
- RDS lectura con réplicas

## Consideraciones de Seguridad
- Todo el tráfico sobre TLS 1.3
- Tokens JWT con expiración de 15 minutos
- Límite de tasa: 100 solicitudes/min por usuario
- WAF para ataques comunes

## Modos de Fallo

| Fallo       | Impacto                  | Mitigación               |
|-------------|--------------------------|--------------------------|
| DB caida    | Caida completa           | Multi-AZ failover        |
| Cache caida | Degradación rendiemiento | Fallback a la DB         |
| Auth caido  | No nuevos logins         | Cache con tokens validos |
```

## Quick Reference

| Sección       | Preguntas clave                        |
|---------------|----------------------------------------|
| Requisitos    | ¿Qué debe hacer? ¿Con qué eficacia?    |
| Arquitectura  | ¿Qué componentes? ¿Cómo se conectan?   |
| Decisiones    | ¿Por qué estas decisiones?             |
| Escalabilidad | ¿Cómo crecer?                          |
| Fallos        | ¿Qué puede fallar? ¿Cómo recuperarse?  |
