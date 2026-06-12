---
name: common-architecture-diagramming
description: Estándares para crear diagramas de arquitectura C4 y UML claros y adecuados al público utilizando Mermaid. Usar al producir diagramas de contexto del sistema, vistas de contenedores, diagramas de secuencia o al actualizar archivos `docs\arquitectura\arquitectura.md`.
metadata:
  triggers:
    files:
    - 'docs\arquitectura\arquitectura.md'
    - '**/*.mermaid'
    - '**/*.drawio'
  keywords:
  - diagrama
  - arquitectura
  - c4
  - diseño de sistemas
  - mermaid
---
# Estándar para Diagramas de Arquitectura

## **Prioridad: P1 (Estándar)**

## Directrices

- **Usar el modelo C4**: Contexto -> Contenedor -> Componente -> Código.
- **Enfocado en el público**: Ajusta el nivel de abstracción (Ejecutivos vs. Desarrolladores).
- **Seleccionar el tipo de diagrama**: Secuencia (Protocolo), ERD (Datos), Estado (Ciclo de vida), Cloud (Infra). Ver [Selection](referencias/diagramas.md).
- **Etiquetas explícitas**: Etiqueta todas las flechas (p. ej., "Uses", "HTTPS").
- **Notación consistente**: Cilindros=BD, Rectángulos=Sistemas, Línea discontinua=Async.
- **Metadatos**: Título, Fecha, Versión, Autor.
- **Leyenda obligatoria**: Define todas las formas/colores/estilos.
- **Dirección**: `graph LR` (flujo) o `graph TD` (jerarquía).
- **Despliegue**: Mapea contenedores a infraestructura.
- **Gobernanza**: CRÍTICO: Revisa [best-practices.md](referencias/best-practices.md) antes de empezar.

 Consulta [implementation examples](referencias/implementacion.md) para un diagrama de contenedor C4 en Mermaid.

## Anti-Patrones

- **Niveles mezclados**: Columnas de BD en el Contexto del Sistema.
- **Flechas sin etiqueta**: Relaciones ambiguas.
- **Formas misteriosas**: No definidas en la leyenda.
- **Puntos muertos**: Nodos sin conexión.
- **Exceso de elementos**: >20 nodos/diagrama.
- **Acrónimos**: Abreviaturas sin definir.

## Referencias

 - [Selección de Diagrama](referencias/diagramas.md)
 - [Guía Modelo C4](referencias/c4-model.md)
 - [Checklist](referencias/checklist.md)
 - [Buenas Prácticas](referencias/best-practices.md)
