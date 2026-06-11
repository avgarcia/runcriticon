# Lista de verificación de requisitos no funcionales

## Categorías de requisitos no funcionales

### Escalabilidad

| Pregunta                          | Objetivos comunes          |
|-----------------------------------|----------------------------|
| ¿Usuarios concurrentes esperados? | 100 / 1000 / 10000 / 10000 |
| ¿Solicitudes por segundo?         | 10 / 100 / 1000 / 10000    |
| ¿Volumen de datos?                | GB / TB / PB               |
| ¿Tasa de crecimiento?             | 10 % / 50 % / 100 % anual  |
| ¿Carga máxima vs. carga promedio? | 2x / 5x / 10x              |

### Rendimiento

| Pregunta                                 | Objetivos comunes              |
|------------------------------------------|--------------------------------|
| ¿Tiempo de respuesta de la API?          | < 100 ms / 200 ms / 500 ms p95 |
| ¿Tiempo de carga de la página?           | < 1 s / 2 s / 3 s              |
| ¿Tiempo de consulta de la base de datos? | < 10 ms / 50 ms / 100 ms       |
| ¿Rendimiento de procesamiento por lotes? | 1K / 10K / 100K registros/hora |

### Disponibilidad

| Objetivo | Tiempo de inactividad/año | Caso de uso                |
|----------|---------------------------|----------------------------|
| 99 %     | 3,65 días                 | Herramientas internas      |
| 99,9 %   | 8,76 horas                | Aplicaciones empresariales |
| 99,95 %  | 4,38 horas                | Comercio electrónico       |
| 99,99 %  | 52,6 minutos              | Sistemas financieros       |
| 99,999 % | 5,26 minutos              | Sistemas críticos          |

### Seguridad

| Pregunta                     | Consideraciones                                                              |
|------------------------------|------------------------------------------------------------------------------|
| ¿Se requiere autenticación?  | JWT, OAuth, SAML, MFA                                                        |
| ¿Modelo de autorización?     | RBAC, ABAC, ACL                                                              |
| ¿Sensibilidad de los datos?  | Públicos, internos, confidenciales, información personal identificable (PII) |
| ¿Requisitos de cumplimiento? | GDPR, HIPAA, PCI DSS, SOC 2                                                  |
| ¿Necesidades de cifrado?     | En reposo, en tránsito, de extremo a extremo                                 |

### Fiabilidad

| Pregunta                            | Consideraciones                          |
|-------------------------------------|------------------------------------------|
| ¿Pérdida de datos aceptable?        | RPO: 0 / 1 h / 24 h                      |
| ¿Tiempo objetivo de recuperación?   | RTO: 1 h / 4 h / 24 h                    |
| ¿Frecuencia de copias de seguridad? | En tiempo real / cada hora / diariamente |
| ¿Recuperación ante desastres?       | Región única / multirregión              |

### Mantenibilidad

| Pregunta                       | Consideraciones                            |
|--------------------------------|--------------------------------------------|
| ¿Frecuencia de despliegue?     | Diario / semanal / mensual                 |
| ¿Estrategia de despliegue?     | Azul-verde, canario, continuo              |
| ¿Requisitos de monitorización? | Registros, métricas, seguimientos, alertas |
| ¿Requisitos de guardia?        | 24/7, horario laboral                      |

### Costo

| Pregunta                         | Consideraciones                               |
|----------------------------------|-----------------------------------------------|
| ¿Presupuesto de infraestructura? | $/mes, $/usuario, $/solicitud                 |
| ¿Presupuesto operativo?          | Personal a tiempo completo para mantenimiento |
| ¿Optimización de costos?         | Instancias reservadas, instancias spot        |
| ¿Alertas de costos?              | Umbrales de notificación                      |

## Plantilla

```markdown
## Requisitos no funcionales

### Rendimiento
- Tiempo de respuesta de la API: < 200 ms p95
- Tiempo de carga de la página: < 2 s
- Tiempo de consulta de la base de datos: < 50 ms

### Escalabilidad
- Usuarios concurrentes: 10.000
- Solicitudes por segundo: 1.000
- Volumen de datos: 1 TB

### Disponibilidad
- Objetivo: 99,9 % (8,76 horas/año de inactividad)
- RPO: 1 hora
- RTO: 4 horas

### Seguridad
- Autenticación: JWT con tokens de actualización
- Autorización: Basada en roles (administrador, usuario, invitado)
- Cumplimiento: RGPD, SOC 2

### Observabilidad
- Registro: JSON estructurado a ELK
- Métricas: Prometheus + Grafana
- Seguimiento: OpenTelemetry
- Alertas: PagerDuty Integración
```

## Referencia rápida

| Categoría      | Métrica clave                     |
|----------------|-----------------------------------|
| Rendimiento    | Tiempo de respuesta (p95)         |
| Escalabilidad  | Usuarios concurrentes, RPS        |
| Disponibilidad | Porcentaje de tiempo de actividad |
| Fiabilidad     | RPO, RTO                          |
| Seguridad      | Requisitos de cumplimiento        |
| Coste          | Presupuesto mensual (en $)        |
